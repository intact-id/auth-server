package com.intact_id.checks.screening;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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

    // Risk model configuration; auth-server only ever sets the tenant

    @GetMapping("/v1/attribute-definitions")
    ScreeningApi.Envelope<List<RiskApi.AttributeDefinition>> attributeDefinitions(
            @RequestParam(value = "customerType", required = false) RiskApi.CustomerType customerType,
            @RequestParam(value = "categoryKey", required = false) String categoryKey);

    @GetMapping("/v1/risk-models")
    ScreeningApi.Envelope<List<RiskApi.RiskModel>> listRiskModels(
            @RequestParam("tenantId") String tenantId,
            @RequestParam(value = "customerType", required = false) RiskApi.CustomerType customerType,
            @RequestParam(value = "status", required = false) RiskApi.ModelStatus status);

    @GetMapping("/v1/risk-models/active")
    ScreeningApi.Envelope<RiskApi.RiskModelDetail> activeRiskModel(@RequestParam("tenantId") String tenantId,
                                                                   @RequestParam("customerType") RiskApi.CustomerType customerType);

    @PostMapping("/v1/risk-models")
    ScreeningApi.Envelope<RiskApi.RiskModelDetail> createRiskModel(@RequestParam("tenantId") String tenantId,
                                                                   @RequestBody RiskApi.RiskModelConfig config);

    @GetMapping("/v1/risk-models/{id}")
    ScreeningApi.Envelope<RiskApi.RiskModelDetail> getRiskModel(@PathVariable("id") String id);

    @PutMapping("/v1/risk-models/{id}")
    ScreeningApi.Envelope<RiskApi.RiskModelDetail> updateRiskModel(@PathVariable("id") String id,
                                                                   @RequestBody RiskApi.RiskModelConfig config);

    @DeleteMapping("/v1/risk-models/{id}")
    ScreeningApi.Envelope<RiskApi.RiskModel> deleteRiskModel(@PathVariable("id") String id);

    @PostMapping("/v1/risk-models/{id}/clone")
    ScreeningApi.Envelope<RiskApi.RiskModel> cloneRiskModel(@PathVariable("id") String id);

    @PostMapping("/v1/risk-models/{id}/activate")
    ScreeningApi.Envelope<RiskApi.RiskModel> activateRiskModel(@PathVariable("id") String id);

    @PostMapping("/v1/risk-models/validate")
    ScreeningApi.Envelope<RiskApi.Validation> validateRiskModel(@RequestParam("forActivation") boolean forActivation,
                                                                @RequestBody RiskApi.RiskModelConfig config);

    @GetMapping("/v1/assessments")
    ScreeningApi.Envelope<List<RiskApi.Assessment>> listAssessments(@RequestParam("tenantId") String tenantId,
                                                                    @RequestParam("customerId") String customerId);

    @GetMapping("/v1/assessments/current")
    ScreeningApi.Envelope<RiskApi.Assessment> currentAssessment(
            @RequestParam("tenantId") String tenantId, @RequestParam("customerId") String customerId,
            @RequestParam(value = "include", required = false) String include);

    @GetMapping("/v1/assessments/{id}")
    ScreeningApi.Envelope<RiskApi.Assessment> getAssessment(@PathVariable("id") String id,
                                                            @RequestParam(value = "include", required = false) String include);
}
