package com.intact_id.dto.response;

import com.intact_id.constant.enums.Country;
import com.intact_id.constant.enums.GlobalStatuses;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 21/11/2025 19:56
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyApplicationResponseDto {

    @Schema(description = "Unique application ID / UUID")
    private String id;

    @Schema(description = "Application number (e.g. APP-0001)")
    private String applicationNumber;

    @Schema(description = "Registered legal name of the company")
    private String legalName;

    @Schema(description = "Trading or brand name")
    private String tradingName;

    @Schema(description = "Registered business number")
    private String registrationNumber;

    @Schema(description = "Official company email")
    private String email;

    @Schema(description = "Company phone number")
    private String phone;

    @Schema(description = "Company website URL")
    private String website;

    @Schema(description = "ISO-2 country code")
    private String country;

    @Schema(description = "Business type e.g. digital_lender, crypto_exchange, neobank")
    private String businessType;

    @Schema(description = "Business application status")
    private GlobalStatuses applicationStatus;

    @Schema(description = "Primary contact full name")
    private String contactPersonName;

    @Schema(description = "Primary contact title/role")
    private String contactPersonTitle;

    @Schema(description = "Primary contact email")
    private String contactPersonEmail;

    @Schema(description = "Primary contact phone")
    private String contactPersonPhone;

    @Schema(description = "Timestamp when the entity was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the entity was last updated")
    private LocalDateTime updatedAt;

    @Schema(description = "User who created the entity")
    private String createdBy;

    @Schema(description = "User who last updated the entity")
    private String updatedBy;
}

