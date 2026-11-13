package com.intact_id.billing.account;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.billing.client.BillingApi;
import com.intact_id.billing.client.BillingApiResult;
import com.intact_id.billing.client.BillingAccountClient;
import com.intact_id.billing.client.ResolvedServiceCharge;
import com.intact_id.util.exception.ApplicationExceptions;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * A company's account in billing-service, found by its company code (billing's clientIdentifier). The company
 * always comes from the caller's login, so one company never reaches another's billing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingAccounts {

    private final BillingAccountClient client;
    private final CompanyRepository companies;

    /** 404 when billing doesn't know the company yet. */
    public BillingApi.Account account(String companyId) {
        BillingApi.Account account = existingAccount(company(companyId));
        if (account == null) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Billing is not set up for this company yet");
        }
        return account;
    }

    public List<BillingApi.WalletTransaction> walletHistory(String companyId) {
        return data(client.walletHistory(profileId(companyId)));
    }

    public List<BillingApi.Invoice> invoices(String companyId) {
        return data(client.invoices(profileId(companyId)));
    }

    /** companyId null = a super admin acting without a company: no ownership check. */
    public BillingApi.Invoice invoice(String companyId, String invoiceId) {
        BillingApi.Invoice invoice = data(client.invoice(invoiceId));
        if (companyId != null && (invoice == null || !profileId(companyId).equals(invoice.billingProfileId()))) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Not found");
        }
        return invoice;
    }

    /** What each service costs this company: its own price where set, otherwise the global one. */
    public List<ResolvedServiceCharge> prices(String companyId) {
        return data(client.resolvedServiceCharges(company(companyId).getCompanyIdentifierCode()));
    }

    /** Registers the company with billing (client type ENTITY). Terms are set later from the dashboard. */
    public void register(String companyId) {
        Company company = company(companyId);
        if (existingAccount(company) != null) return;
        client.createClient(new BillingApi.ClientCreation(company.getCompanyIdentifierCode(), BillingApi.ClientType.ENTITY,
                company.getLegalName(), "Intact ID company", Map.of("companyId", company.getId())));
        log.info("Registered company {} with billing", company.getCompanyIdentifierCode());
    }

    /**
     * Sets up the company's billing with the terms chosen on the dashboard: profile, then wallet (prepaid) and the
     * default per-call price when given. Creates only what is missing, so it is safe to repeat.
     */
    public BillingApi.Account setUp(String companyId, BillingApi.Setup setup) {
        register(companyId);
        Company company = company(companyId);
        BillingApi.Account account = existingAccount(company);
        if (account.billingProfile() == null) {
            client.createBillingProfile(new BillingApi.BillingProfileCreation(account.client().id(), setup.billingModel(),
                    setup.currency(), setup.paymentType()));
            account = existingAccount(company);
        }
        String profileId = account.billingProfile().id();
        if (account.wallet() == null && account.billingProfile().paymentType() == BillingApi.PaymentType.PREPAID) {
            client.createWallet(new BillingApi.WalletCreation(profileId));
        }
        if (account.perCallPricing() == null && setup.pricePerCall() != null) {
            client.createPerCallPricing(new BillingApi.PerCallPricingCreation(profileId, setup.pricePerCall(),
                    setup.freeQuota() != null ? setup.freeQuota() : 0L));
        }
        log.info("Billing set up for company {}", company.getCompanyIdentifierCode());
        return account(companyId);
    }

    public BillingApi.BillingProfile updateProfile(String companyId, BillingApi.ProfileChanges changes) {
        return data(client.updateBillingProfile(profileId(companyId), changes));
    }

    public BillingApi.Wallet creditWallet(String companyId, BillingApi.WalletCredit credit) {
        return data(client.creditWallet(profileId(companyId), credit));
    }

    public BillingApi.PerCallPricing updatePerCallPricing(String companyId, BillingApi.PerCallChanges changes) {
        return data(client.updatePerCallPricing(profileId(companyId), changes));
    }

    /** companyId null = the global prices every company pays unless it has its own. */
    public List<BillingApi.ServiceCharge> serviceCharges(String companyId) {
        return data(client.serviceCharges(companyId == null ? null : profileId(companyId)));
    }

    /** companyId null = a global price. */
    public BillingApi.ServiceCharge createServiceCharge(String companyId, BillingApi.NewServiceCharge charge) {
        return data(client.createServiceCharge(new BillingApi.ServiceChargeCreation(charge.serviceCode(), charge.name(),
                companyId == null ? null : profileId(companyId), charge.price(), charge.currency())));
    }

    public BillingApi.ServiceCharge updateServiceCharge(String chargeId, BillingApi.ServiceChargeChanges changes) {
        return data(client.updateServiceCharge(chargeId, changes));
    }

    public BillingApi.Invoice markInvoicePaid(String invoiceId) {
        return data(client.markInvoicePaid(invoiceId));
    }

    public BillingApi.Invoice voidInvoice(String invoiceId) {
        return data(client.voidInvoice(invoiceId));
    }

    private String profileId(String companyId) {
        BillingApi.BillingProfile profile = account(companyId).billingProfile();
        if (profile == null) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("This company has no billing profile yet");
        }
        return profile.id();
    }

    private BillingApi.Account existingAccount(Company company) {
        try {
            return data(client.account(company.getCompanyIdentifierCode()));
        } catch (FeignException.NotFound e) {
            return null;
        }
    }

    private Company company(String companyId) {
        return companies.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Company not found"));
    }

    private static <T> T data(BillingApiResult<T> result) {
        return result == null ? null : result.data();
    }
}
