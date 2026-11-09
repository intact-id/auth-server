package com.intact_id.workflow.catalog;

import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.CatalogService;
import com.intact_id.workflow.ServiceStatus;
import com.intact_id.workflow.web.CatalogServiceRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Intact staff manage the catalog: new services start IN_DEVELOPMENT and merchants only see AVAILABLE ones. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogAdminService {

    private static final Map<ServiceStatus, Set<ServiceStatus>> TRANSITIONS = Map.of(
            ServiceStatus.IN_DEVELOPMENT, Set.of(ServiceStatus.AVAILABLE, ServiceStatus.RETIRED),
            ServiceStatus.AVAILABLE, Set.of(ServiceStatus.DEPRECATED),
            ServiceStatus.DEPRECATED, Set.of(ServiceStatus.AVAILABLE, ServiceStatus.RETIRED),
            ServiceStatus.RETIRED, Set.of());

    private final CatalogServiceRepository repository;
    private final ServiceCatalogImpl catalog;

    public List<CatalogService> list(ServiceStatus status) {
        return catalog.all().values().stream()
                .filter(s -> status == null || s.status() == status)
                .toList();
    }

    @Transactional
    public CatalogService create(CatalogServiceRequestDto request) {
        if (request.getCode() == null || request.getName() == null || request.getCategory() == null) {
            throw new ApplicationExceptions.IntactBadRequestException("code, name and category are required");
        }
        if (repository.existsByCode(request.getCode())) {
            throw new ApplicationExceptions.DuplicateResourceException("Service " + request.getCode() + " already exists");
        }
        CatalogServiceEntity entity = CatalogServiceEntity.builder()
                .code(request.getCode())
                .status(ServiceStatus.IN_DEVELOPMENT)
                .build();
        apply(entity, request);
        validateDependencies(entity);
        CatalogServiceEntity saved = repository.save(entity);
        catalog.refresh();
        log.info("Catalog service {} created (IN_DEVELOPMENT)", saved.getCode());
        return saved.toView();
    }

    @Transactional
    public CatalogService update(String code, CatalogServiceRequestDto request) {
        CatalogServiceEntity entity = require(code);
        if (entity.getStatus() == ServiceStatus.RETIRED) {
            throw new ApplicationExceptions.InvalidStatusTransitionException("Retired services can't be changed");
        }
        apply(entity, request);
        validateDependencies(entity);
        CatalogServiceEntity saved = repository.save(entity);
        catalog.refresh();
        log.info("Catalog service {} updated", code);
        return saved.toView();
    }

    @Transactional
    public CatalogService changeStatus(String code, ServiceStatus target) {
        CatalogServiceEntity entity = require(code);
        if (!TRANSITIONS.get(entity.getStatus()).contains(target)) {
            throw new ApplicationExceptions.InvalidStatusTransitionException(
                    "Can't move " + code + " from " + entity.getStatus() + " to " + target);
        }
        if (target == ServiceStatus.AVAILABLE) {
            for (String dependency : entity.getRequires()) {
                ServiceStatus dependencyStatus = require(dependency).getStatus();
                if (dependencyStatus != ServiceStatus.AVAILABLE) {
                    throw new ApplicationExceptions.InvalidStatusTransitionException(
                            code + " needs " + dependency + ", which is " + dependencyStatus);
                }
            }
        } else {
            List<String> dependents = repository.findAll().stream()
                    .filter(s -> s.getStatus() == ServiceStatus.AVAILABLE && s.getRequires().contains(code))
                    .map(CatalogServiceEntity::getCode)
                    .toList();
            if (!dependents.isEmpty()) {
                throw new ApplicationExceptions.InvalidStatusTransitionException(
                        "Available services depend on " + code + ": " + String.join(", ", dependents));
            }
        }
        ServiceStatus previous = entity.getStatus();
        entity.setStatus(target);
        CatalogServiceEntity saved = repository.save(entity);
        catalog.refresh();
        log.info("Catalog service {} moved from {} to {}", code, previous, target);
        return saved.toView();
    }

    /** Only services never made available can be deleted; others are retired so old workflows stay explainable. */
    @Transactional
    public void delete(String code) {
        CatalogServiceEntity entity = require(code);
        if (entity.getStatus() != ServiceStatus.IN_DEVELOPMENT) {
            throw new ApplicationExceptions.InvalidStatusTransitionException(
                    code + " is " + entity.getStatus() + "; retire it instead of deleting");
        }
        List<String> dependents = repository.findAll().stream()
                .filter(s -> s.getRequires().contains(code))
                .map(CatalogServiceEntity::getCode)
                .toList();
        if (!dependents.isEmpty()) {
            throw new ApplicationExceptions.InvalidStatusTransitionException(
                    "Other services depend on " + code + ": " + String.join(", ", dependents));
        }
        repository.delete(entity);
        catalog.refresh();
        log.info("Catalog service {} deleted", code);
    }

    private CatalogServiceEntity require(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Service not found: " + code));
    }

    private static void apply(CatalogServiceEntity entity, CatalogServiceRequestDto request) {
        if (request.getName() != null) entity.setName(request.getName().trim());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getCategory() != null) entity.setCategory(request.getCategory());
        if (request.getSubjectTypes() != null) entity.setSubjectTypes(new ArrayList<>(new HashSet<>(request.getSubjectTypes())));
        if (request.getCountries() != null) entity.setCountries(new ArrayList<>(new HashSet<>(request.getCountries())));
        if (request.getRequires() != null) entity.setRequires(new ArrayList<>(new HashSet<>(request.getRequires())));
        if (request.getRequiredFields() != null) entity.setRequiredFields(new ArrayList<>(request.getRequiredFields()));
        if (request.getCaptures() != null) entity.setCaptures(new ArrayList<>(request.getCaptures()));
        if (request.getOutputs() != null) entity.setOutputs(new ArrayList<>(request.getOutputs()));
        if (request.getUi() != null) entity.setUi(new HashMap<>(request.getUi()));
        if (request.getSettingsSchema() != null) entity.setSettingsSchema(new HashMap<>(request.getSettingsSchema()));
        if (request.getSortOrder() != null) entity.setSortOrder(request.getSortOrder());
    }

    /** Dependencies must exist and must not loop back to this service. */
    private void validateDependencies(CatalogServiceEntity entity) {
        Map<String, List<String>> graph = new HashMap<>();
        repository.findAll().forEach(s -> graph.put(s.getCode(), s.getRequires()));
        graph.put(entity.getCode(), entity.getRequires());
        for (String dependency : entity.getRequires()) {
            if (Objects.equals(dependency, entity.getCode())) {
                throw new ApplicationExceptions.IntactBadRequestException(entity.getCode() + " can't require itself");
            }
            if (!graph.containsKey(dependency)) {
                throw new ApplicationExceptions.IntactBadRequestException("Unknown dependency: " + dependency);
            }
        }
        if (reaches(graph, entity.getRequires(), entity.getCode(), new HashSet<>())) {
            throw new ApplicationExceptions.IntactBadRequestException("Dependencies of " + entity.getCode() + " form a cycle");
        }
    }

    private static boolean reaches(Map<String, List<String>> graph, List<String> from, String target, Set<String> seen) {
        for (String code : from) {
            if (code.equals(target)) return true;
            if (seen.add(code) && reaches(graph, graph.getOrDefault(code, List.of()), target, seen)) return true;
        }
        return false;
    }
}
