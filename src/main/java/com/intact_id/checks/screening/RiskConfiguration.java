package com.intact_id.checks.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * A company's risk models and assessments in sactions-service. The tenant always comes from the company, never the
 * caller, and anything fetched by id must belong to it (super admins skip that check when acting without a company).
 */
@Service
@RequiredArgsConstructor
public class RiskConfiguration {

    private final ScreeningServiceClient client;
    private final ScreeningTenants tenants;
    private final CompanyRepository companies;

    public String tenantOf(String companyId) {
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Company not found"));
        return tenants.tenantFor(company.getId(), company.getLegalName());
    }

    public JsonNode attributeDefinitions(String customerType, String categoryKey) {
        return data(client.attributeDefinitions(customerType, categoryKey));
    }

    public JsonNode listModels(String companyId, String customerType, String status) {
        return data(client.listRiskModels(tenantOf(companyId), customerType, status));
    }

    public JsonNode activeModel(String companyId, String customerType) {
        return data(client.activeRiskModel(tenantOf(companyId), customerType));
    }

    public JsonNode createModel(String companyId, JsonNode config) {
        return data(client.createRiskModel(tenantOf(companyId), config));
    }

    public JsonNode getModel(String companyId, String id) {
        return owned(companyId, data(client.getRiskModel(id)), m -> m.path("model").path("tenantId"));
    }

    public JsonNode updateModel(String companyId, String id, JsonNode config) {
        getModel(companyId, id);
        return data(client.updateRiskModel(id, config));
    }

    public JsonNode deleteModel(String companyId, String id) {
        getModel(companyId, id);
        return data(client.deleteRiskModel(id));
    }

    public JsonNode cloneModel(String companyId, String id) {
        getModel(companyId, id);
        return data(client.cloneRiskModel(id));
    }

    public JsonNode activateModel(String companyId, String id) {
        getModel(companyId, id);
        return data(client.activateRiskModel(id));
    }

    public JsonNode validateModel(boolean forActivation, JsonNode config) {
        return data(client.validateRiskModel(forActivation, config));
    }

    public JsonNode listAssessments(String companyId, String customerId) {
        return data(client.listAssessments(tenantOf(companyId), customerId));
    }

    public JsonNode currentAssessment(String companyId, String customerId, boolean breakdown) {
        return data(client.currentAssessment(tenantOf(companyId), customerId, breakdown ? "breakdown" : null));
    }

    public JsonNode getAssessment(String companyId, String id, boolean breakdown) {
        return owned(companyId, data(client.getAssessment(id, breakdown ? "breakdown" : null)), a -> a.path("tenantId"));
    }

    /** companyId null = a super admin acting without a company: no ownership check. */
    private JsonNode owned(String companyId, JsonNode data, Function<JsonNode, JsonNode> tenantField) {
        if (companyId == null) return data;
        String tenantId = tenantField.apply(data).asText(null);
        if (tenantId == null || !tenantId.equals(tenantOf(companyId))) {
            // Same answer as a missing record, so ids from other companies reveal nothing
            throw new ApplicationExceptions.IntactResourceNotFoundException("Not found");
        }
        return data;
    }

    private static JsonNode data(JsonNode envelope) {
        return envelope == null ? null : envelope.get("data");
    }
}
