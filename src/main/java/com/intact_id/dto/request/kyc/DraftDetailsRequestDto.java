package com.intact_id.dto.request.kyc;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Partial update: only non-null fields are applied to the draft.
 */
@Data
public class DraftDetailsRequestDto {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    // Any document type: national IDs, passports, licences and alien cards have different formats
    @Pattern(regexp = "^[A-Za-z0-9 -]{4,30}$", message = "Document number must be 4 to 30 letters or digits")
    private String idNumber;

    @Size(max = 30)
    private String idType;

    @Size(max = 100)
    private String nationality;

    @Size(max = 100)
    private String countryOfResidence;

    @Pattern(regexp = "^\\+254[0-9]{9}$", message = "Invalid Kenyan phone number")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;
}
