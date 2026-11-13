package com.intact_id.accounts.web;

import com.intact_id.constant.enums.GlobalStatuses;

import java.time.LocalDateTime;

/**
 * What anyone holding an application number may see. The endpoint needs no login, so it carries no
 * contact details (emails, phone numbers, contact person) or internal ids.
 */
public record PublicApplicationStatusDto(
        String applicationNumber,
        String legalName,
        GlobalStatuses applicationStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    static PublicApplicationStatusDto from(CompanyApplicationResponseDto application) {
        return new PublicApplicationStatusDto(application.getApplicationNumber(), application.getLegalName(),
                application.getApplicationStatus(), application.getCreatedAt(), application.getUpdatedAt());
    }
}
