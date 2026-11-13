package com.intact_id.workflow.definition;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.NodeType;
import com.intact_id.workflow.ServiceCatalog;
import com.intact_id.workflow.ServiceRequirements;
import com.intact_id.workflow.SubjectType;
import com.intact_id.workflow.WorkflowGraph;
import com.intact_id.workflow.WorkflowInvalidException;
import com.intact_id.workflow.WorkflowPricing;
import com.intact_id.workflow.WorkflowPricing.PriceQuote;
import com.intact_id.workflow.web.WorkflowDtos.CreateWorkflowRequest;
import com.intact_id.workflow.web.WorkflowDtos.PublishResult;
import com.intact_id.workflow.web.WorkflowDtos.UpdateDraftRequest;
import com.intact_id.workflow.web.WorkflowDtos.ValidationResponse;
import com.intact_id.workflow.web.WorkflowDtos.VersionSummary;
import com.intact_id.workflow.web.WorkflowDtos.WorkflowDetail;
import com.intact_id.workflow.web.WorkflowDtos.WorkflowSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowVersionRepository versions;
    private final WorkflowValidator validator;
    private final ServiceCatalog catalog;
    private final WorkflowPricing pricing;
    private final CompanyRepository companies;

    public List<WorkflowSummary> list(String companyId) {
        return definitions.findByCompanyIdAndArchivedFalseOrderByNameAsc(companyId).stream().map(this::summary).toList();
    }

    public List<WorkflowSummary> templates() {
        return definitions.findByCompanyIdIsNullAndArchivedFalseOrderByNameAsc().stream().map(this::summary).toList();
    }

    /** Archived workflows of a company, or archived templates when companyId is null. */
    public List<WorkflowSummary> archived(String companyId) {
        List<WorkflowDefinitionEntity> archived = companyId == null
                ? definitions.findByCompanyIdIsNullAndArchivedTrueOrderByNameAsc()
                : definitions.findByCompanyIdAndArchivedTrueOrderByNameAsc(companyId);
        return archived.stream().map(this::summary).toList();
    }

    public WorkflowDetail detail(String companyId, String id) {
        WorkflowDefinitionEntity definition = readable(companyId, id);
        List<VersionSummary> history = versions.findByDefinitionIdOrderByNumberDesc(id).stream()
                .map(v -> versionSummary(v, definition)).toList();
        return new WorkflowDetail(summary(definition), definition.getDraftGraph(), history);
    }

    @Transactional
    public WorkflowDetail create(String companyId, CreateWorkflowRequest request, boolean template) {
        String owner = template ? null : companyId;
        boolean taken = template ? definitions.existsByCompanyIdIsNullAndKey(request.key())
                : definitions.existsByCompanyIdAndKey(owner, request.key());
        if (taken) {
            throw new ApplicationExceptions.DuplicateResourceException("A workflow with key " + request.key() + " already exists");
        }

        WorkflowGraph graph = request.graph();
        SubjectType subjectType = request.subjectType();
        if (request.fromTemplateId() != null) {
            WorkflowDefinitionEntity source = definitions.findById(request.fromTemplateId())
                    .filter(WorkflowDefinitionEntity::isTemplate)
                    .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Template not found"));
            graph = source.getDraftGraph();
            subjectType = Optional.ofNullable(subjectType).orElse(source.getSubjectType());
        }
        if (subjectType == null) {
            throw new ApplicationExceptions.IntactBadRequestException("subjectType is required (INDIVIDUAL or BUSINESS)");
        }

        WorkflowDefinitionEntity saved = definitions.save(WorkflowDefinitionEntity.builder()
                .companyId(owner)
                .key(request.key())
                .name(request.name().trim())
                .description(request.description())
                .subjectType(subjectType)
                .draftGraph(Optional.ofNullable(graph).orElseGet(WorkflowDefinitionService::emptyGraph))
                .draftUpdatedAt(LocalDateTime.now())
                .build());
        log.info("Workflow {} ({}) created for {}", saved.getKey(), saved.getId(), template ? "templates" : "company " + owner);
        return detail(companyId, saved.getId());
    }

    @Transactional
    public WorkflowDetail updateDraft(String companyId, String id, UpdateDraftRequest request) {
        WorkflowDefinitionEntity definition = editable(companyId, id);
        if (request.name() != null) definition.setName(request.name().trim());
        if (request.description() != null) definition.setDescription(request.description());
        definition.setDraftGraph(request.graph());
        definition.setDraftUpdatedAt(LocalDateTime.now());
        definitions.save(definition);
        return detail(companyId, id);
    }

    /** Problems, what the widget will collect, and the price; nothing is saved. */
    public ValidationResponse validate(String companyId, String id) {
        WorkflowDefinitionEntity definition = readable(companyId, id);
        WorkflowValidator.Result result = validator.validate(definition.getDraftGraph(), definition.getSubjectType());
        ServiceRequirements requirements = catalog.requirementsFor(result.services(), definition.getSubjectType(), null, true);
        PriceQuote price = definition.isTemplate() || result.services().isEmpty() ? null : quote(companyId, result.services());
        return new ValidationResponse(result.valid(), result.problems(), result.services(), requirements, price);
    }

    /**
     * Publishes the draft as the next version, which goes live at once, and prices it in billing. When the draft
     * is the same as the latest version, nothing is created and that version is returned.
     */
    @Transactional
    public PublishResult publish(String companyId, String id, String notes, String publishedBy) {
        WorkflowDefinitionEntity definition = editable(companyId, id);
        Optional<WorkflowVersionEntity> latest = definition.getLatestVersion() == 0 ? Optional.empty()
                : versions.findByDefinitionIdAndNumber(id, definition.getLatestVersion());
        if (latest.isPresent() && Objects.equals(latest.get().getGraph(), definition.getDraftGraph())) {
            log.info("Workflow {} unchanged since v{}; nothing published", definition.getKey(), latest.get().getNumber());
            return new PublishResult(versionSummary(latest.get(), definition), false);
        }
        WorkflowValidator.Result result = validator.validate(definition.getDraftGraph(), definition.getSubjectType());
        if (!result.valid()) {
            throw new WorkflowInvalidException(result.problems());
        }

        PriceQuote price = definition.isTemplate() ? null
                : pricing.price(definition.getId(), clientIdentifier(definition.getCompanyId()), result.services());

        int number = definition.getLatestVersion() + 1;
        WorkflowVersionEntity version = versions.save(WorkflowVersionEntity.builder()
                .definitionId(definition.getId())
                .number(number)
                .graph(definition.getDraftGraph())
                .services(result.services())
                .price(price == null ? null : price.total())
                .currency(price == null ? null : price.currency())
                .notes(notes)
                .publishedBy(publishedBy)
                .publishedAt(LocalDateTime.now())
                .promotedAt(definition.isTemplate() ? null : LocalDateTime.now())
                .build());
        definition.setLatestVersion(number);
        // One environment per server: what is published is live
        if (!definition.isTemplate()) {
            definition.setLiveVersion(number);
        }
        definitions.save(definition);
        log.info("Workflow {} v{} published by {} ({} {})", definition.getKey(), number, publishedBy,
                version.getPrice(), version.getCurrency());
        return new PublishResult(versionSummary(version, definition), true);
    }

    /**
     * Puts an earlier version back in use (a rollback). Billing is re-priced for that version's services, since
     * billing keeps one price per workflow.
     */
    @Transactional
    public VersionSummary promote(String companyId, String id, int number, String promotedBy) {
        WorkflowDefinitionEntity definition = editable(companyId, id);
        WorkflowVersionEntity version = versions.findByDefinitionIdAndNumber(id, number)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Version " + number + " not found"));
        if (!definition.isTemplate()) {
            PriceQuote price = pricing.price(definition.getId(), clientIdentifier(definition.getCompanyId()), version.getServices());
            version.setPrice(price.total());
            version.setCurrency(price.currency());
        }
        version.setPromotedAt(LocalDateTime.now());
        versions.save(version);
        definition.setLiveVersion(number);
        definitions.save(definition);
        log.info("Workflow {} v{} put back in use by {}", definition.getKey(), number, promotedBy);
        return versionSummary(version, definition);
    }

    @Transactional
    public void archive(String companyId, String id) {
        WorkflowDefinitionEntity definition = editable(companyId, id);
        definition.setArchived(true);
        definition.setDefaultWorkflow(false);
        definitions.save(definition);
        log.info("Workflow {} archived", definition.getKey());
    }

    /**
     * Makes this the workflow sessions and API calls run when they name none (instead of the tier checks).
     * Needs a live version, so production never falls back silently. Replaces any previous default.
     */
    @Transactional
    public WorkflowDetail makeDefault(String companyId, String id) {
        WorkflowDefinitionEntity definition = editable(companyId, id);
        if (definition.isTemplate()) {
            throw new ApplicationExceptions.IntactBadRequestException("Templates can't be a company's default");
        }
        if (definition.getLiveVersion() == null) {
            throw new ApplicationExceptions.IntactBadRequestException("Make a version live before making it the default");
        }
        definitions.findByCompanyIdAndDefaultWorkflowTrueAndArchivedFalse(companyId)
                .filter(previous -> !previous.getId().equals(id))
                .ifPresent(previous -> {
                    previous.setDefaultWorkflow(false);
                    definitions.saveAndFlush(previous);
                });
        definition.setDefaultWorkflow(true);
        definitions.save(definition);
        log.info("Workflow {} is now the default for company {}", definition.getKey(), companyId);
        return detail(companyId, id);
    }

    /** Sessions and API calls that name no workflow go back to the tier checks. */
    @Transactional
    public WorkflowDetail clearDefault(String companyId, String id) {
        WorkflowDefinitionEntity definition = editable(companyId, id);
        definition.setDefaultWorkflow(false);
        definitions.save(definition);
        return detail(companyId, id);
    }

    /** Brings an archived workflow back with its draft, versions and live version as they were. */
    @Transactional
    public WorkflowDetail restore(String companyId, String id) {
        WorkflowDefinitionEntity definition = definitions.findById(id)
                .filter(WorkflowDefinitionEntity::isArchived)
                .filter(d -> Objects.equals(d.getCompanyId(), companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Archived workflow not found"));
        definition.setArchived(false);
        definitions.save(definition);
        log.info("Workflow {} restored", definition.getKey());
        return detail(companyId, id);
    }

    public PriceQuote quote(String companyId, Collection<String> services) {
        return pricing.quote(clientIdentifier(companyId), services);
    }

    private String clientIdentifier(String companyId) {
        return companies.findById(companyId).map(Company::getCompanyIdentifierCode)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Company not found"));
    }

    /** A company's own workflows, or any template. */
    private WorkflowDefinitionEntity readable(String companyId, String id) {
        return definitions.findById(id)
                .filter(d -> !d.isArchived())
                .filter(d -> d.isTemplate() || Objects.equals(d.getCompanyId(), companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Workflow not found"));
    }

    /** Only the owning company may change its workflow; templates only by Intact admins (companyId null). */
    private WorkflowDefinitionEntity editable(String companyId, String id) {
        WorkflowDefinitionEntity definition = readable(companyId, id);
        if (definition.isTemplate() && companyId != null) {
            throw new ApplicationExceptions.IntactForbiddenException("Templates can't be edited; copy it into a new workflow");
        }
        return definition;
    }

    private WorkflowSummary summary(WorkflowDefinitionEntity d) {
        Optional<WorkflowVersionEntity> live = Optional.ofNullable(d.getLiveVersion())
                .flatMap(n -> versions.findByDefinitionIdAndNumber(d.getId(), n));
        return new WorkflowSummary(d.getId(), d.getKey(), d.getName(), d.getDescription(), d.getSubjectType(),
                d.isTemplate(), d.getLatestVersion(), d.getLiveVersion(), d.getDraftUpdatedAt(),
                live.map(WorkflowVersionEntity::getPrice).orElse(null), live.map(WorkflowVersionEntity::getCurrency).orElse(null),
                d.isDefaultWorkflow());
    }

    private static VersionSummary versionSummary(WorkflowVersionEntity v, WorkflowDefinitionEntity d) {
        BigDecimal price = v.getPrice();
        return new VersionSummary(v.getNumber(), v.getServices(), price, v.getCurrency(), v.getNotes(),
                v.getPublishedBy(), v.getPublishedAt(), v.getPromotedAt(), Objects.equals(d.getLiveVersion(), v.getNumber()));
    }

    private static WorkflowGraph emptyGraph() {
        return new WorkflowGraph(List.of(new WorkflowGraph.Node("start", NodeType.START, "Start", null, null, null, null,
                Map.of("x", 80, "y", 200))), List.of());
    }
}
