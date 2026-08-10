package com.intact_id.client.billing;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** No auth required — billing-service's check-balance endpoint is unauthenticated. */
@FeignClient(name = "billing-service", url = "${billing-service.url}", configuration = BillingServiceFeignConfig.class)
public interface BillingServiceClient {

    @PostMapping("/v1/billing-profile/check-balance")
    BillingApiResult<BalanceCheckResponse> checkBalance(@RequestBody BalanceCheckRequest request);
}
