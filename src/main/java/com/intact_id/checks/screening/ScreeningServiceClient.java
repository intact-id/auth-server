package com.intact_id.checks.screening;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
}
