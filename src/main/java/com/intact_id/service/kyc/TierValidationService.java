package com.intact_id.service.kyc;

import com.intact_id.dto.request.KycVerificationRequestDto;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
public class TierValidationService {

    public void validateTier(String tier, KycVerificationRequestDto request) {
        String normalizedTier = normalizeTier(tier);
        log.info("Validating fields for tier: {}", normalizedTier);

        switch (normalizedTier) {
            case "tier1" -> validateTier1(request);
            case "tier2" -> validateTier2(request);
            case "tier3" -> validateTier3(request);
            default -> throw new ApplicationExceptions.IntactBadRequestException("Invalid tier: " + tier);
        }
    }

    private String normalizeTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return "";
        }

        String normalized = tier.trim().toLowerCase().replace("_", "");
        if (normalized.startsWith("tier")) {
            return normalized;
        }
        return tier.trim().toLowerCase();
    }

    private void validateTier1(KycVerificationRequestDto request) {
        // Tier 1: Basic verification
        // Required: first_name, last_name, dob, id_number, selfie, id_front

        validateRequiredFile(request.getSelfie(), "selfie");
        validateRequiredFile(request.getIdFront(), "id_front");

        log.info("Tier 1 validation passed");
    }

    private void validateTier2(KycVerificationRequestDto request) {
        // Tier 2: Includes IPRS verification
        // Required: All tier1 + id_type

        validateTier1(request);

        if (request.getIdType() == null || request.getIdType().isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("id_type is required for tier2");
        }

        validateIdType(request.getIdType());

        log.info("Tier 2 validation passed");
    }

    private void validateTier3(KycVerificationRequestDto request) {
        // Tier 3: Includes sanctions, PEP, adverse media
        // Required: All tier2 + nationality, country_of_residence

        validateTier2(request);

        if (request.getNationality() == null || request.getNationality().isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("nationality is required for tier3");
        }

        if (request.getCountryOfResidence() == null || request.getCountryOfResidence().isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("country_of_residence is required for tier3");
        }

        log.info("Tier 3 validation passed");
    }

    private void validateRequiredFile(org.springframework.web.multipart.MultipartFile file, String fieldName) {
        if (Objects.isNull(file) || file.isEmpty()) {
            throw new ApplicationExceptions.IntactBadRequestException(fieldName + " is required");
        }

        // Validate file size (max 10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    fieldName + " exceeds maximum size of 10MB");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    fieldName + " must be an image file (jpg, jpeg, png)");
        }
    }

    private void validateIdType(String idType) {
        String[] validTypes = {"national_id", "passport", "drivers_license", "alien_card"};
        boolean isValid = Arrays.asList(validTypes).contains(idType.toLowerCase());

        if (!isValid) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Invalid id_type. Must be one of: national_id, passport, drivers_license, alien_card");
        }
    }
}
