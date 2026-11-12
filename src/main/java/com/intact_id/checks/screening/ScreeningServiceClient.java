package com.intact_id.checks.screening;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** sactions-service: sanctions/PEP screening and customer risk assessment (CRA). */
@FeignClient(name = "screening-service", url = "${screening-service.url}", configuration = ScreeningServiceFeignConfig.class)
public interface ScreeningServiceClient {

    @PostMapping("/api/v1/match/person")
    ScreeningApi.Result matchPerson(@RequestBody ScreeningApi.PersonMatch person);

    @PostMapping("/api/v1/match/business")
    ScreeningApi.Result matchBusiness(@RequestBody ScreeningApi.BusinessMatch business);

    @PostMapping("/v1/assessments")
    ScreeningApi.Envelope<ScreeningApi.Assessment> runAssessment(@RequestBody ScreeningApi.AssessmentRequest request);

    @PostMapping("/v1/tenant")
    ScreeningApi.Envelope<ScreeningApi.Tenant> createTenant(@RequestBody ScreeningApi.TenantCreation tenant);

    // Risk model configuration: bodies and results pass through as JSON; auth-server only sets the tenant

    @GetMapping("/v1/attribute-definitions")
    JsonNode attributeDefinitions(@RequestParam(value = "customerType", required = false) String customerType,
                                  @RequestParam(value = "categoryKey", required = false) String categoryKey);

    @GetMapping("/v1/risk-models")
    JsonNode listRiskModels(@RequestParam("tenantId") String tenantId,
                            @RequestParam(value = "customerType", required = false) String customerType,
                            @RequestParam(value = "status", required = false) String status);

    @GetMapping("/v1/risk-models/active")
    JsonNode activeRiskModel(@RequestParam("tenantId") String tenantId, @RequestParam("customerType") String customerType);

    @PostMapping("/v1/risk-models")
    JsonNode createRiskModel(@RequestParam("tenantId") String tenantId, @RequestBody JsonNode config);

    @GetMapping("/v1/risk-models/{id}")
    JsonNode getRiskModel(@PathVariable("id") String id);

    @PutMapping("/v1/risk-models/{id}")
    JsonNode updateRiskModel(@PathVariable("id") String id, @RequestBody JsonNode config);

    @DeleteMapping("/v1/risk-models/{id}")
    JsonNode deleteRiskModel(@PathVariable("id") String id);

    @PostMapping("/v1/risk-models/{id}/clone")
    JsonNode cloneRiskModel(@PathVariable("id") String id);

    @PostMapping("/v1/risk-models/{id}/activate")
    JsonNode activateRiskModel(@PathVariable("id") String id);

    @PostMapping("/v1/risk-models/validate")
    JsonNode validateRiskModel(@RequestParam("forActivation") boolean forActivation, @RequestBody JsonNode config);

    @GetMapping("/v1/assessments")
    JsonNode listAssessments(@RequestParam("tenantId") String tenantId, @RequestParam("customerId") String customerId);

    @GetMapping("/v1/assessments/current")
    JsonNode currentAssessment(@RequestParam("tenantId") String tenantId, @RequestParam("customerId") String customerId,
                               @RequestParam(value = "include", required = false) String include);

    @GetMapping("/v1/assessments/{id}")
    JsonNode getAssessment(@PathVariable("id") String id, @RequestParam(value = "include", required = false) String include);
}
