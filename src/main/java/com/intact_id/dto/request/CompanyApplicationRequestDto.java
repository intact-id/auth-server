package com.intact_id.dto.request;

import com.intact_id.constant.enums.Country;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 21/11/2025 18:46
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyApplicationRequestDto {

    @Schema(example = "Intact Fintech Ltd", description = "Registered legal name of the company")
    @NotBlank(message = "Legal name is required")
    private String legalName;

    @Schema(example = "Intact Pay", description = "Trading or brand name")
    private String tradingName;

    @Schema(example = "PVT-123456", description = "Registered business number")
    private String registrationNumber;

    @Schema(example = "support@intact.com", description = "Official company email address")
    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    private String email;

    @Schema(example = "+254712345678", description = "Company phone number")
    private String phone;

    @Schema(example = "https://intact.com", description = "Company website URL")
    private String website;

    @Schema(example = "KE", description = "ISO-2 country code (default KE)")
    private Country country;

    @Schema(example = "digital_lender", description = "Business type e.g. digital_lender, crypto_exchange, neobank")
    @NotBlank(message = "Business type is required")
    private String businessType;

    @Schema(example = "John Doe", description = "Full name of primary contact person")
    @NotBlank(message = "Contact person name is required")
    private String contactPersonName;

    @Schema(example = "CTO", description = "Role/Title of contact person")
    private String contactPersonTitle;

    @Schema(example = "john.doe@intact.com", description = "Email of the contact person")
    @Email(message = "Contact person email must be valid")
    private String contactPersonEmail;

    @Schema(example = "+254711223344", description = "Phone number of contact person")
    private String contactPersonPhone;
}
