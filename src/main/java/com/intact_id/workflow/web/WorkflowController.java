package com.intact_id.workflow.web;

import com.intact_id.accounts.CompanyAuthentication;
import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.User;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.WorkflowPricing.PriceQuote;
import com.intact_id.workflow.definition.WorkflowDefinitionService;
import com.intact_id.workflow.web.WorkflowDtos.PublishResult;
import com.intact_id.workflow.web.WorkflowDtos.CreateWorkflowRequest;
import com.intact_id.workflow.web.WorkflowDtos.PromoteRequest;
import com.intact_id.workflow.web.WorkflowDtos.PublishRequest;
import com.intact_id.workflow.web.WorkflowDtos.QuoteRequest;
import com.intact_id.workflow.web.WorkflowDtos.UpdateDraftRequest;
import com.intact_id.workflow.web.WorkflowDtos.ValidationResponse;
import com.intact_id.workflow.web.WorkflowDtos.VersionSummary;
import com.intact_id.workflow.web.WorkflowDtos.WorkflowDetail;
import com.intact_id.workflow.web.WorkflowDtos.WorkflowSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.intact_id.workflow.web.WorkflowResponses.respond;

/**
 * Merchants build workflows for their company; super admins act for a company with ?companyId= and manage
 * the templates merchants start from (template=true, edited with ?template=true).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workflows")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Workflows", description = "Build, validate and publish verification workflows. Publishing makes a version live.")
public class WorkflowController {

    private final WorkflowDefinitionService service;

    @GetMapping
    @Operation(summary = "The company's workflows")
    public ResponseEntity<ApiResponse<List<WorkflowSummary>>> list(@RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "OK", service.list(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    @GetMapping("/archived")
    @Operation(summary = "Archived workflows", description = "The company's, or templates when template=true. Restore one to use it again.")
    public ResponseEntity<ApiResponse<List<WorkflowSummary>>> archived(@RequestParam(required = false) String companyId,
                                                                     @RequestParam(defaultValue = "false") boolean template) {
        return respond(HttpStatus.OK, "OK", service.archived(owner(companyId, template)));
    }

    @GetMapping("/templates")
    @Operation(summary = "Intact templates to start a workflow from")
    public ResponseEntity<ApiResponse<List<WorkflowSummary>>> templates() {
        return respond(HttpStatus.OK, "OK", service.templates());
    }

    @PostMapping
    @Operation(summary = "Create a workflow", description = "Empty canvas, a graph, or a copy of fromTemplateId.")
    public ResponseEntity<ApiResponse<WorkflowDetail>> create(@RequestParam(required = false) String companyId,
                                                             @Valid @RequestBody CreateWorkflowRequest request) {
        boolean template = Boolean.TRUE.equals(request.template());
        if (template) requireSuperAdmin();
        String owner = template ? null : SecurityContextUtil.resolveCompanyId(companyId);
        return respond(HttpStatus.CREATED, "Workflow created", service.create(owner, request, template));
    }

    @GetMapping("/{id}")
    @Operation(summary = "A workflow with its draft and version history")
    public ResponseEntity<ApiResponse<WorkflowDetail>> detail(@PathVariable String id,
                                                             @RequestParam(required = false) String companyId,
                                                             @RequestParam(defaultValue = "false") boolean template) {
        return respond(HttpStatus.OK, "OK", service.detail(owner(companyId, template), id));
    }

    @PutMapping("/{id}/draft")
    @Operation(summary = "Save the draft", description = "Published versions are never changed by editing.")
    public ResponseEntity<ApiResponse<WorkflowDetail>> saveDraft(@PathVariable String id,
                                                                @RequestParam(required = false) String companyId,
                                                                @RequestParam(defaultValue = "false") boolean template,
                                                                @Valid @RequestBody UpdateDraftRequest request) {
        return respond(HttpStatus.OK, "Draft saved", service.updateDraft(owner(companyId, template), id, request));
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Check the draft", description = "Problems (with node ids), what the widget will collect, and the price.")
    public ResponseEntity<ApiResponse<ValidationResponse>> validate(@PathVariable String id,
                                                                   @RequestParam(required = false) String companyId,
                                                                   @RequestParam(defaultValue = "false") boolean template) {
        return respond(HttpStatus.OK, "OK", service.validate(owner(companyId, template), id));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish the draft as a new version", description = "It goes live at once and is priced in billing. 422 lists problems.")
    public ResponseEntity<ApiResponse<VersionSummary>> publish(@PathVariable String id,
                                                              @RequestParam(required = false) String companyId,
                                                              @RequestParam(defaultValue = "false") boolean template,
                                                              @Valid @RequestBody(required = false) PublishRequest request) {
        String notes = request == null ? null : request.notes();
        PublishResult result = service.publish(owner(companyId, template), id, notes, actor());
        return result.created()
                ? respond(HttpStatus.CREATED, "Version published", result.version())
                : respond(HttpStatus.OK, "Nothing changed since v" + result.version().number() + "; no new version", result.version());
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Make this the company's default workflow",
            description = "Sessions and API calls that name no workflow run it instead of the tier checks. Needs a live version.")
    public ResponseEntity<ApiResponse<WorkflowDetail>> makeDefault(@PathVariable String id,
                                                                  @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Default workflow set", service.makeDefault(SecurityContextUtil.resolveCompanyId(companyId), id));
    }

    @DeleteMapping("/{id}/default")
    @Operation(summary = "Stop using this as the default", description = "Calls that name no workflow go back to the tier checks.")
    public ResponseEntity<ApiResponse<WorkflowDetail>> clearDefault(@PathVariable String id,
                                                                   @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Default workflow cleared", service.clearDefault(SecurityContextUtil.resolveCompanyId(companyId), id));
    }

    @PostMapping("/{id}/promote")
    @Operation(summary = "Put an earlier version back in use", description = "A rollback; billing is re-priced for that version.")
    public ResponseEntity<ApiResponse<VersionSummary>> promote(@PathVariable String id,
                                                              @RequestParam(required = false) String companyId,
                                                              @Valid @RequestBody PromoteRequest request) {
        return respond(HttpStatus.OK, "Version is live",
                service.promote(SecurityContextUtil.resolveCompanyId(companyId), id, request.version(), actor()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Archive a workflow")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable String id,
                                                    @RequestParam(required = false) String companyId,
                                                    @RequestParam(defaultValue = "false") boolean template) {
        service.archive(owner(companyId, template), id);
        return respond(HttpStatus.OK, "Workflow archived", null);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore an archived workflow", description = "Its versions and live version come back as they were.")
    public ResponseEntity<ApiResponse<WorkflowDetail>> restore(@PathVariable String id,
                                                              @RequestParam(required = false) String companyId,
                                                              @RequestParam(defaultValue = "false") boolean template) {
        return respond(HttpStatus.OK, "Workflow restored", service.restore(owner(companyId, template), id));
    }

    @PostMapping("/quote")
    @Operation(summary = "Price a set of services for the company", description = "Used by the builder while editing.")
    public ResponseEntity<ApiResponse<PriceQuote>> quote(@RequestParam(required = false) String companyId,
                                                        @Valid @RequestBody QuoteRequest request) {
        return respond(HttpStatus.OK, "OK", service.quote(SecurityContextUtil.resolveCompanyId(companyId), request.services()));
    }

    /** null = templates (super admins only); otherwise the caller's company. */
    private static String owner(String companyId, boolean template) {
        if (template) {
            requireSuperAdmin();
            return null;
        }
        return SecurityContextUtil.resolveCompanyId(companyId);
    }

    private static void requireSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdminAuthenticated()) {
            throw new ApplicationExceptions.IntactForbiddenException("Only Intact super admins manage templates");
        }
    }

    /** Username for dashboard users, company code for API clients; recorded on each version. */
    private static String actor() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getUsername();
        if (SecurityContextHolder.getContext().getAuthentication() instanceof CompanyAuthentication company) {
            return company.getCompany().getCompanyIdentifierCode();
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
