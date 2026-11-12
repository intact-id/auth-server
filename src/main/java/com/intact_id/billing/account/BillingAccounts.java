package com.intact_id.billing.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.billing.client.BillingServiceClient;
import com.intact_id.billing.client.ResolvedServiceCharge;
import com.intact_id.util.exception.ApplicationExceptions;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public enum BillingModel { PER_CALL, FLAT_PERIODIC, HYBRID }

    public enum PaymentType { PREPAID, POSTPAID }

    public record Setup(BillingModel billingModel, PaymentType paymentType, String currency, BigDecimal pricePerCall,
                        Long freeQuota) {
    }

    private final BillingServiceClient client;
    private final CompanyRepository companies;
    private final ObjectMapper objectMapper;

    /** client, billingProfile, wallet, perCallPricing; parts not set up yet are null. 404 if billing doesn't know the company. */
    public JsonNode account(String companyId) {
        try {
            return data(client.account(company(companyId).getCompanyIdentifierCode()));
        } catch (FeignException.NotFound e) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Billing is not set up for this company yet");
        }
    }

    public JsonNode walletHistory(String companyId) {
        return data(client.walletHistory(profileId(companyId)));
    }

    public JsonNode invoices(String companyId) {
        return data(client.invoices(profileId(companyId)));
    }

    public JsonNode invoice(String companyId, String invoiceId) {
        JsonNode invoice = data(client.invoice(invoiceId));
        if (companyId != null && !profileId(companyId).equals(invoice.path("billingProfileId").asText(null))) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Not found");
        }
        return invoice;
    }

    /** What each service costs this company: its own price where set, otherwise the global one. */
    public List<ResolvedServiceCharge> prices(String companyId) {
        return client.resolvedServiceCharges(company(companyId).getCompanyIdentifierCode()).data();
    }

    /** Registers the company with billing (client type ENTITY). Terms are set later from the dashboard. */
    public void register(String companyId) {
        Company company = company(companyId);
        if (existingAccount(company) != null) return;
        client.createClient(json(Map.of("clientIdentifier", company.getCompanyIdentifierCode(),
                "clientType", "ENTITY", "clientName", company.getLegalName(),
                "description", "Intact ID company", "extraDetails", Map.of("companyId", company.getId()))));
        log.info("Registered company {} with billing", company.getCompanyIdentifierCode());
    }

    /**
     * Sets up the company's billing with the terms chosen on the dashboard: profile, then wallet (prepaid) and the
     * default per-call price when given. Creates only what is missing, so it is safe to repeat.
     */
    public JsonNode setUp(String companyId, Setup setup) {
        register(companyId);
        Company company = company(companyId);
        JsonNode account = existingAccount(company);
        if (absent(account.path("billingProfile"))) {
            require(setup.billingModel() != null && setup.paymentType() != null && setup.currency() != null,
                    "billingModel, paymentType and currency are required to create the billing profile");
            client.createBillingProfile(json(Map.of("clientId", account.path("client").path("id").asText(),
                    "billingModel", setup.billingModel().name(), "paymentType", setup.paymentType().name(),
                    "currency", setup.currency())));
            account = existingAccount(company);
        }
        String profileId = account.path("billingProfile").path("id").asText();
        if (absent(account.path("wallet")) && "PREPAID".equals(account.path("billingProfile").path("paymentType").asText())) {
            client.createWallet(json(Map.of("billingProfileId", profileId)));
        }
        if (absent(account.path("perCallPricing")) && setup.pricePerCall() != null) {
            client.createPerCallPricing(json(Map.of("billingProfileId", profileId, "pricePerCall", setup.pricePerCall(),
                    "freeQuota", setup.freeQuota() != null ? setup.freeQuota() : 0L)));
        }
        return account(companyId);
    }

    private static boolean absent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    public JsonNode updateProfile(String companyId, JsonNode changes) {
        return data(client.updateBillingProfile(profileId(companyId), changes));
    }

    public JsonNode creditWallet(String companyId, JsonNode credit) {
        return data(client.creditWallet(profileId(companyId), credit));
    }

    public JsonNode updatePerCallPricing(String companyId, JsonNode changes) {
        return data(client.updatePerCallPricing(profileId(companyId), changes));
    }

    /** companyId null = the global charges every company pays unless it has its own. */
    public JsonNode serviceCharges(String companyId) {
        return data(client.serviceCharges(companyId == null ? null : profileId(companyId)));
    }

    public JsonNode createServiceCharge(String companyId, JsonNode charge) {
        ObjectNode body = charge.deepCopy();
        if (companyId != null) body.put("billingProfileId", profileId(companyId));
        else body.remove("billingProfileId");
        return data(client.createServiceCharge(body));
    }

    public JsonNode updateServiceCharge(String chargeId, JsonNode changes) {
        return data(client.updateServiceCharge(chargeId, changes));
    }

    public JsonNode markInvoicePaid(String invoiceId) {
        return data(client.markInvoicePaid(invoiceId));
    }

    public JsonNode voidInvoice(String invoiceId) {
        return data(client.voidInvoice(invoiceId));
    }

    private String profileId(String companyId) {
        String id = account(companyId).path("billingProfile").path("id").asText(null);
        if (id == null) throw new ApplicationExceptions.IntactResourceNotFoundException("This company has no billing profile yet");
        return id;
    }

    private JsonNode existingAccount(Company company) {
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

    private JsonNode json(Map<String, Object> body) {
        return objectMapper.valueToTree(body);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ApplicationExceptions.IntactBadRequestException(message);
    }

    private static JsonNode data(JsonNode envelope) {
        return envelope == null ? null : envelope.get("data");
    }
}
