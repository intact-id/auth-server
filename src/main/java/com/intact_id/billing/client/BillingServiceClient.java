package com.intact_id.billing.client;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * The calls on a verification's path (balance check, prices): a short timeout so a slow billing-service
 * does not stall submissions. Dashboard account calls use {@link BillingAccountClient}. No auth required.
 */
@FeignClient(name = "billing-service", url = "${billing-service.url}", configuration = BillingServiceFeignConfig.class)
public interface BillingServiceClient {

    @PostMapping("/v1/billing-profile/check-balance")
    BillingApiResult<BalanceCheckResponse> checkBalance(@RequestBody BalanceCheckRequest request);

    @GetMapping("/v1/service-charges/resolved")
    BillingApiResult<List<ResolvedServiceCharge>> resolvedServiceCharges(@RequestParam("clientIdentifier") String clientIdentifier);

    @PutMapping("/v1/billing-profile/per-call-pricing/workflows/{workflowId}")
    BillingApiResult<WorkflowPricingResult> priceWorkflow(@PathVariable("workflowId") String workflowId,
                                                          @RequestBody WorkflowPricingRequest request);
}
