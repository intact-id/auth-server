package com.intact_id.orchestration.privacy;

import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The platform's retention rule and companies' own rules. */
@Service
@RequiredArgsConstructor
public class RetentionSettingsService {

    private final RetentionSettingsRepository settings;

    public RetentionSettings platformDefault() {
        return settings.findByCompanyIdIsNull().map(s -> toDto(s, false)).orElse(new RetentionSettings(null, null, false));
    }

    /** The company's own rule, else the platform default. */
    public RetentionSettings effective(String companyId) {
        return settings.findByCompanyId(companyId).map(s -> toDto(s, true)).orElseGet(this::platformDefault);
    }

    /** companyId null changes the platform default; otherwise creates or replaces the company's own rule. */
    @Transactional
    public RetentionSettings update(String companyId, RetentionSettings changes) {
        validate(changes.documentsDays(), "documentsDays");
        validate(changes.personalDataDays(), "personalDataDays");
        if (changes.documentsDays() != null && changes.personalDataDays() != null && changes.personalDataDays() < changes.documentsDays()) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Personal details can't be erased before the documents: personalDataDays must be at least documentsDays");
        }
        RetentionSettingsEntity row = (companyId == null ? settings.findByCompanyIdIsNull() : settings.findByCompanyId(companyId))
                .orElseGet(RetentionSettingsEntity::new);
        row.setCompanyId(companyId);
        row.setDocumentsDays(changes.documentsDays());
        row.setPersonalDataDays(changes.personalDataDays());
        return toDto(settings.save(row), companyId != null);
    }

    @Transactional
    public RetentionSettings clearOverride(String companyId) {
        settings.findByCompanyId(companyId).ifPresent(settings::delete);
        return platformDefault();
    }

    private static void validate(Integer days, String field) {
        if (days != null && (days < 0 || days > 36500)) {
            throw new ApplicationExceptions.IntactBadRequestException(field + " must be between 0 and 36500 days, or empty to keep");
        }
    }

    private static RetentionSettings toDto(RetentionSettingsEntity s, boolean companyOverride) {
        return new RetentionSettings(s.getDocumentsDays(), s.getPersonalDataDays(), companyOverride);
    }
}
