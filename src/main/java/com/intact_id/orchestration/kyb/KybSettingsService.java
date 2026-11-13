package com.intact_id.orchestration.kyb;

import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** The platform default for checking directors and owners, and per-company overrides of it. */
@Service
@RequiredArgsConstructor
public class KybSettingsService {

    private final KybSettingsRepository settings;

    /** The company's own settings, else the platform default. */
    public KybSettings effective(String companyId) {
        return settings.findByCompanyId(companyId).map(s -> toDto(s, true))
                .orElseGet(this::platformDefault);
    }

    public KybSettings platformDefault() {
        return settings.findByCompanyIdIsNull().map(s -> toDto(s, false))
                .orElse(new KybSettings(OwnerCheck.FULL_KYC, new BigDecimal("25"), false, null, false));
    }

    /** companyId null updates the platform default; otherwise creates or replaces the company's override. */
    @Transactional
    public KybSettings update(String companyId, KybSettings changes) {
        if (changes.ownerCheck() == null) {
            throw new ApplicationExceptions.IntactBadRequestException("ownerCheck is required (NONE, SCREENING or FULL_KYC)");
        }
        BigDecimal threshold = changes.ownershipThreshold() == null ? new BigDecimal("25") : changes.ownershipThreshold();
        if (threshold.signum() < 0 || threshold.compareTo(new BigDecimal("100")) > 0) {
            throw new ApplicationExceptions.IntactBadRequestException("ownershipThreshold must be between 0 and 100");
        }
        KybSettingsEntity row = (companyId == null ? settings.findByCompanyIdIsNull() : settings.findByCompanyId(companyId))
                .orElseGet(KybSettingsEntity::new);
        row.setCompanyId(companyId);
        row.setOwnerCheck(changes.ownerCheck());
        row.setOwnershipThreshold(threshold);
        row.setIncludeDirectors(changes.includeDirectors());
        row.setPersonWorkflow(changes.personWorkflow() == null || changes.personWorkflow().isBlank()
                ? null : changes.personWorkflow().trim());
        return toDto(settings.save(row), companyId != null);
    }

    /** The company goes back to the platform default. */
    @Transactional
    public KybSettings clearOverride(String companyId) {
        settings.findByCompanyId(companyId).ifPresent(settings::delete);
        return platformDefault();
    }

    private static KybSettings toDto(KybSettingsEntity s, boolean companyOverride) {
        return new KybSettings(s.getOwnerCheck(), s.getOwnershipThreshold(), s.isIncludeDirectors(), s.getPersonWorkflow(),
                companyOverride);
    }
}
