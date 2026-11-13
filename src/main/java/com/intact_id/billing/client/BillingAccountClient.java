package com.intact_id.billing.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * billing-service account management for the dashboard: accounts, wallet top-ups, prices and invoices.
 * Separate from {@link BillingServiceClient} so these calls get a longer read timeout: a write that is
 * slow but succeeds must not be reported as failed, or a retried top-up credits the wallet twice.
 */
@FeignClient(name = "billing-service", contextId = "billing-accounts", url = "${billing-service.url}",
        configuration = BillingAccountFeignConfig.class)
public interface BillingAccountClient {

    @GetMapping("/v1/service-charges/resolved")
    BillingApiResult<List<ResolvedServiceCharge>> resolvedServiceCharges(@RequestParam("clientIdentifier") String clientIdentifier);

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
