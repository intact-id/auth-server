package com.intact_id.checks.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.checks.screening.RiskConfiguration;
import com.intact_id.config.web.DownstreamApi;
import com.intact_id.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.intact_id.config.web.DownstreamApiResponses.respond;

/**
 * Customer risk assessment (CRA) configuration, done by sactions-service. Companies manage their own risk models;
 * super admins act for any company with ?companyId=.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/risk")
@PreAuthorize("isAuthenticated()")
@DownstreamApi("Risk service")
@Tag(name = "Risk (CRA)", description = "Risk models that score customers LOW / MEDIUM / HIGH, and their assessments.")
public class RiskController {

    private static final String COMPANY_ID = "Super admins only: the company to act for";

    private final RiskConfiguration risk;

    public enum CustomerType { INDIVIDUAL, BUSINESS, BOTH }

    public enum ModelStatus { DRAFT, ACTIVE, RETIRED }

    @GetMapping("/attribute-definitions")
    @Operation(summary = "Attributes a risk model can score on")
    public ResponseEntity<ApiResponse<JsonNode>> attributeDefinitions(@RequestParam(required = false) CustomerType customerType,
                                                                      @RequestParam(required = false) String categoryKey) {
        return ok(risk.attributeDefinitions(name(customerType), categoryKey));
    }

    @GetMapping("/models")
    @Operation(summary = "The company's risk models")
    public ResponseEntity<ApiResponse<JsonNode>> listModels(@RequestParam(required = false) CustomerType customerType,
                                                            @RequestParam(required = false) ModelStatus status,
                                                            @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(risk.listModels(company(companyId), name(customerType), name(status)));
    }

    @GetMapping("/models/active")
    @Operation(summary = "The active model for a customer type", description = "404 when none is active yet.")
    public ResponseEntity<ApiResponse<JsonNode>> activeModel(@RequestParam CustomerType customerType,
                                                             @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(risk.activeModel(company(companyId), customerType.name()));
    }

    @PostMapping("/models")
    @Operation(summary = "Create a draft risk model", description = "Body: the risk model config (categories, attributes, thresholds, override rules).")
    public ResponseEntity<ApiResponse<JsonNode>> createModel(@RequestBody JsonNode config,
                                                             @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.CREATED, "Risk model created", risk.createModel(company(companyId), config));
    }

    @PostMapping("/models/validate")
    @Operation(summary = "Check a config without saving it")
    public ResponseEntity<ApiResponse<JsonNode>> validateModel(@RequestParam(defaultValue = "false") boolean forActivation,
                                                               @RequestBody JsonNode config) {
        return ok(risk.validateModel(forActivation, config));
    }

    @GetMapping("/models/{id}")
    @Operation(summary = "A risk model with its config")
    public ResponseEntity<ApiResponse<JsonNode>> getModel(@PathVariable String id,
                                                          @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(risk.getModel(owner(companyId), id));
    }

    @PutMapping("/models/{id}")
    @Operation(summary = "Save a draft's config", description = "Only drafts can be edited; clone an active model to change it.")
    public ResponseEntity<ApiResponse<JsonNode>> updateModel(@PathVariable String id, @RequestBody JsonNode config,
                                                             @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Risk model saved", risk.updateModel(owner(companyId), id, config));
    }

    @DeleteMapping("/models/{id}")
    @Operation(summary = "Delete a draft")
    public ResponseEntity<ApiResponse<JsonNode>> deleteModel(@PathVariable String id,
                                                             @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Risk model deleted", risk.deleteModel(owner(companyId), id));
    }

    @PostMapping("/models/{id}/clone")
    @Operation(summary = "Copy a model into a new draft")
    public ResponseEntity<ApiResponse<JsonNode>> cloneModel(@PathVariable String id,
                                                            @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.CREATED, "Draft created", risk.cloneModel(owner(companyId), id));
    }

    @PostMapping("/models/{id}/activate")
    @Operation(summary = "Make a draft the active model", description = "The previous active model for that customer type is retired.")
    public ResponseEntity<ApiResponse<JsonNode>> activateModel(@PathVariable String id,
                                                               @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Risk model activated", risk.activateModel(owner(companyId), id));
    }

    @GetMapping("/assessments")
    @Operation(summary = "A customer's assessments", description = "customerId is the verification's externalReference, or its id when none was sent.")
    public ResponseEntity<ApiResponse<JsonNode>> listAssessments(@RequestParam String customerId,
                                                                 @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(risk.listAssessments(company(companyId), customerId));
    }

    @GetMapping("/assessments/current")
    @Operation(summary = "A customer's latest assessment", description = "breakdown=true adds the per-category scores.")
    public ResponseEntity<ApiResponse<JsonNode>> currentAssessment(@RequestParam String customerId,
                                                                   @RequestParam(defaultValue = "false") boolean breakdown,
                                                                   @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(risk.currentAssessment(company(companyId), customerId, breakdown));
    }

    @GetMapping("/assessments/{id}")
    @Operation(summary = "One assessment", description = "breakdown=true adds the per-category scores.")
    public ResponseEntity<ApiResponse<JsonNode>> getAssessment(@PathVariable String id,
                                                               @RequestParam(defaultValue = "false") boolean breakdown,
                                                               @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(risk.getAssessment(owner(companyId), id, breakdown));
    }

    private static String company(String companyId) {
        return SecurityContextUtil.resolveCompanyId(companyId);
    }

    /** For lookups by id: a super admin without ?companyId= may open any company's record. */
    private static String owner(String companyId) {
        if (SecurityContextUtil.isSuperAdminAuthenticated() && (companyId == null || companyId.isBlank())) return null;
        return company(companyId);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static ResponseEntity<ApiResponse<JsonNode>> ok(JsonNode data) {
        return respond(HttpStatus.OK, "OK", data);
    }
}
