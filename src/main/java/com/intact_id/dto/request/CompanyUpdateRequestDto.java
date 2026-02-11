package com.intact_id.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class CompanyUpdateRequestDto {
    // Company fields
    private String tradingName;
    private String businessType;

    // Communication fields
    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String website;

    @Email(message = "Invalid secondary email format")
    private String secondaryEmail;

    private String secondaryPhone;

    @Email(message = "Invalid support email format")
    private String supportEmail;

    @Email(message = "Invalid billing email format")
    private String billingEmail;
}
