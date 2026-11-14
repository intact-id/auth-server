package com.intact_id.billing.charge;

import com.intact_id.accounts.Company;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The platform's charge mode (billing.charge-mode) and each company's own choice, if it has one. */
@Service
@RequiredArgsConstructor
public class ChargeModes {

    private final CompanyRepository companies;

    @Value("${billing.charge-mode:WORKFLOW_PRICE}")
    private ChargeMode platformDefault;

    public ChargeMode platformDefault() {
        return platformDefault;
    }

    /** The company's own mode, else the platform default. */
    public ChargeMode effective(String companyId) {
        return companies.findById(companyId).map(Company::getChargeMode).map(ChargeMode::valueOf).orElse(platformDefault);
    }

    public Settings settings(String companyId) {
        Company company = company(companyId);
        return new Settings(platformDefault, effective(companyId), company.getChargeMode() != null);
    }

    /** mode null puts the company back on the platform default. */
    @Transactional
    public Settings update(String companyId, ChargeMode mode) {
        Company company = company(companyId);
        company.setChargeMode(mode == null ? null : mode.name());
        companies.save(company);
        return settings(companyId);
    }

    private Company company(String companyId) {
        return companies.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Company not found"));
    }

    /** companyOverride is true when the company has chosen its own mode. */
    public record Settings(ChargeMode platformDefault, ChargeMode mode, boolean companyOverride) {
    }

    public record Change(ChargeMode mode) {
    }
}
