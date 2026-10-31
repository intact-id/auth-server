package com.intact_id.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@Data
public class KycVerificationRequestDto {

    // Required fields (all tiers)
    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "ID number is required")
    @Pattern(regexp = "^[0-9]{8}$", message = "ID number must be 8 digits")
    private String idNumber;

    // Required for tier2+
    private String idType; // national_id, passport, drivers_license, alien_card

    // Required for tier3
    private String nationality;
    private String countryOfResidence;

    // Optional fields
    @Pattern(regexp = "^\\+254[0-9]{9}$", message = "Invalid Kenyan phone number")
    private String phone;

    @Email(message = "Invalid email format")
    private String email;

    private String middleName;
    private String occupation;

    // Custom fields (business-specific data)
    private Map<String, Object> customFields;

    // Files (handled separately in multipart)
    private MultipartFile selfie;
    private MultipartFile idFront;
    private MultipartFile idBack;
    private MultipartFile proofOfAddress;

    // Metadata
    private String verificationType = "individual"; // individual, business
    private String webhookUrl;

    @Size(max = 255, message = "externalReference must be at most 255 characters")
    private String externalReference;
}
