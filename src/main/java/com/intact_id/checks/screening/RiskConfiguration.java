package com.intact_id.checks.screening;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<RiskApi.AttributeDefinition> attributeDefinitions(RiskApi.CustomerType customerType, String categoryKey) {
        return data(client.attributeDefinitions(customerType, categoryKey));
    }

    public List<RiskApi.RiskModel> listModels(String companyId, RiskApi.CustomerType customerType, RiskApi.ModelStatus status) {
        return data(client.listRiskModels(tenantOf(companyId), customerType, status));
    }

    public RiskApi.RiskModelDetail activeModel(String companyId, RiskApi.CustomerType customerType) {
        return data(client.activeRiskModel(tenantOf(companyId), customerType));
    }

    public RiskApi.RiskModelDetail createModel(String companyId, RiskApi.RiskModelConfig config) {
        return data(client.createRiskModel(tenantOf(companyId), config));
    }

    public RiskApi.RiskModelDetail getModel(String companyId, String id) {
        RiskApi.RiskModelDetail detail = data(client.getRiskModel(id));
        requireOwned(companyId, detail == null || detail.model() == null ? null : detail.model().tenantId());
        return detail;
    }

    public RiskApi.RiskModelDetail updateModel(String companyId, String id, RiskApi.RiskModelConfig config) {
        getModel(companyId, id);
        return data(client.updateRiskModel(id, config));
    }

    public RiskApi.RiskModel deleteModel(String companyId, String id) {
        getModel(companyId, id);
        return data(client.deleteRiskModel(id));
    }

    public RiskApi.RiskModel cloneModel(String companyId, String id) {
        getModel(companyId, id);
        return data(client.cloneRiskModel(id));
    }

    public RiskApi.RiskModel activateModel(String companyId, String id) {
        getModel(companyId, id);
        return data(client.activateRiskModel(id));
    }

    public RiskApi.Validation validateModel(boolean forActivation, RiskApi.RiskModelConfig config) {
        return data(client.validateRiskModel(forActivation, config));
    }

    public List<RiskApi.Assessment> listAssessments(String companyId, String customerId) {
        return data(client.listAssessments(tenantOf(companyId), customerId));
    }

    public RiskApi.Assessment currentAssessment(String companyId, String customerId, boolean breakdown) {
        return data(client.currentAssessment(tenantOf(companyId), customerId, breakdown ? "breakdown" : null));
    }

    public RiskApi.Assessment getAssessment(String companyId, String id, boolean breakdown) {
        RiskApi.Assessment assessment = data(client.getAssessment(id, breakdown ? "breakdown" : null));
        requireOwned(companyId, assessment == null ? null : assessment.tenantId());
        return assessment;
    }

    /** companyId null = a super admin acting without a company: no ownership check. */
    private void requireOwned(String companyId, String tenantId) {
        if (companyId == null) return;
        if (tenantId == null || !tenantId.equals(tenantOf(companyId))) {
            // Same answer as a missing record, so ids from other companies reveal nothing
            throw new ApplicationExceptions.IntactResourceNotFoundException("Not found");
        }
    }

    private static <T> T data(ScreeningApi.Envelope<T> envelope) {
        return envelope == null ? null : envelope.data();
    }
}
