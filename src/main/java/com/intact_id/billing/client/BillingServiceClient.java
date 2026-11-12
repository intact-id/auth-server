package com.intact_id.billing.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** No auth required — billing-service's check-balance endpoint is unauthenticated. */
@FeignClient(name = "billing-service", url = "${billing-service.url}", configuration = BillingServiceFeignConfig.class)
public interface BillingServiceClient {

    @PostMapping("/v1/billing-profile/check-balance")
    BillingApiResult<BalanceCheckResponse> checkBalance(@RequestBody BalanceCheckRequest request);

    @GetMapping("/v1/service-charges/resolved")
    BillingApiResult<List<ResolvedServiceCharge>> resolvedServiceCharges(@RequestParam("clientIdentifier") String clientIdentifier);

    @PutMapping("/v1/billing-profile/per-call-pricing/workflows/{workflowId}")
    BillingApiResult<WorkflowPricingResult> priceWorkflow(@PathVariable("workflowId") String workflowId,
                                                          @RequestBody WorkflowPricingRequest request);

    // Account management for the dashboard: bodies and results pass through as JSON

    @GetMapping("/v1/client/account")
    JsonNode account(@RequestParam("clientIdentifier") String clientIdentifier);

    @PostMapping("/v1/client")
    JsonNode createClient(@RequestBody JsonNode client);

    @PostMapping("/v1/billing-profile")
    JsonNode createBillingProfile(@RequestBody JsonNode profile);

    @PatchMapping("/v1/billing-profile")
    JsonNode updateBillingProfile(@RequestParam("id") String id, @RequestBody JsonNode changes);

    @PostMapping("/v1/billing-profile/wallet")
    JsonNode createWallet(@RequestBody JsonNode wallet);

    @PostMapping("/v1/billing-profile/wallet/credit")
    JsonNode creditWallet(@RequestParam("billingProfileId") String billingProfileId, @RequestBody JsonNode credit);

    @GetMapping("/v1/billing-profile/wallet/history")
    JsonNode walletHistory(@RequestParam("billingProfileId") String billingProfileId);

    @PostMapping("/v1/billing-profile/per-call-pricing")
    JsonNode createPerCallPricing(@RequestBody JsonNode pricing);

    @PatchMapping("/v1/billing-profile/per-call-pricing")
    JsonNode updatePerCallPricing(@RequestParam("billingProfileId") String billingProfileId, @RequestBody JsonNode changes);

    @GetMapping("/v1/service-charges")
    JsonNode serviceCharges(@RequestParam(value = "billingProfileId", required = false) String billingProfileId);

    @PostMapping("/v1/service-charges")
    JsonNode createServiceCharge(@RequestBody JsonNode charge);

    @PatchMapping("/v1/service-charges/{id}")
    JsonNode updateServiceCharge(@PathVariable("id") String id, @RequestBody JsonNode changes);

    @GetMapping("/v1/invoice/by-billing-profile")
    JsonNode invoices(@RequestParam("billingProfileId") String billingProfileId);

    @GetMapping("/v1/invoice")
    JsonNode invoice(@RequestParam("id") String id);

    @PostMapping("/v1/invoice/mark-paid")
    JsonNode markInvoicePaid(@RequestParam("id") String id);

    @PostMapping("/v1/invoice/void")
    JsonNode voidInvoice(@RequestParam("id") String id);
}
