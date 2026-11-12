package com.intact_id.billing.client;

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

    // Account management for the dashboard

    @GetMapping("/v1/client/account")
    BillingApiResult<BillingApi.Account> account(@RequestParam("clientIdentifier") String clientIdentifier);

    @PostMapping("/v1/client")
    BillingApiResult<BillingApi.Client> createClient(@RequestBody BillingApi.ClientCreation client);

    @PostMapping("/v1/billing-profile")
    BillingApiResult<BillingApi.BillingProfile> createBillingProfile(@RequestBody BillingApi.BillingProfileCreation profile);

    @PatchMapping("/v1/billing-profile")
    BillingApiResult<BillingApi.BillingProfile> updateBillingProfile(@RequestParam("id") String id,
                                                                     @RequestBody BillingApi.ProfileChanges changes);

    @PostMapping("/v1/billing-profile/wallet")
    BillingApiResult<BillingApi.Wallet> createWallet(@RequestBody BillingApi.WalletCreation wallet);

    @PostMapping("/v1/billing-profile/wallet/credit")
    BillingApiResult<BillingApi.Wallet> creditWallet(@RequestParam("billingProfileId") String billingProfileId,
                                                     @RequestBody BillingApi.WalletCredit credit);

    @GetMapping("/v1/billing-profile/wallet/history")
    BillingApiResult<List<BillingApi.WalletTransaction>> walletHistory(@RequestParam("billingProfileId") String billingProfileId);

    @PostMapping("/v1/billing-profile/per-call-pricing")
    BillingApiResult<BillingApi.PerCallPricing> createPerCallPricing(@RequestBody BillingApi.PerCallPricingCreation pricing);

    @PatchMapping("/v1/billing-profile/per-call-pricing")
    BillingApiResult<BillingApi.PerCallPricing> updatePerCallPricing(@RequestParam("billingProfileId") String billingProfileId,
                                                                     @RequestBody BillingApi.PerCallChanges changes);

    @GetMapping("/v1/service-charges")
    BillingApiResult<List<BillingApi.ServiceCharge>> serviceCharges(
            @RequestParam(value = "billingProfileId", required = false) String billingProfileId);

    @PostMapping("/v1/service-charges")
    BillingApiResult<BillingApi.ServiceCharge> createServiceCharge(@RequestBody BillingApi.ServiceChargeCreation charge);

    @PatchMapping("/v1/service-charges/{id}")
    BillingApiResult<BillingApi.ServiceCharge> updateServiceCharge(@PathVariable("id") String id,
                                                                   @RequestBody BillingApi.ServiceChargeChanges changes);

    @GetMapping("/v1/invoice/by-billing-profile")
    BillingApiResult<List<BillingApi.Invoice>> invoices(@RequestParam("billingProfileId") String billingProfileId);

    @GetMapping("/v1/invoice")
    BillingApiResult<BillingApi.Invoice> invoice(@RequestParam("id") String id);

    @PostMapping("/v1/invoice/mark-paid")
    BillingApiResult<BillingApi.Invoice> markInvoicePaid(@RequestParam("id") String id);

    @PostMapping("/v1/invoice/void")
    BillingApiResult<BillingApi.Invoice> voidInvoice(@RequestParam("id") String id);
}
