package com.intact_id.service.kyc;

import com.intact_id.dto.response.ocr.OcrExtractionResponse;
import com.intact_id.service.kyc.ExternalKycServiceClient.CheckResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class OcrValidationService {

    @Value("${ocr.confidence.threshold:0.65}")
    private double confidenceThreshold;

    @Value("${ocr.name.similarity.threshold:0.85}")
    private double nameSimilarityThreshold;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy")
    );

    private static final Map<String, String> ID_TYPE_MAP = Map.of(
            "national_id", "national_id",
            "passport", "passport",
            "drivers_license", "drivers_license",
            "alien_card", "residence_permit",
            "residence_permit", "residence_permit"
    );

    public enum ValidationDecision { APPROVED, REJECTED, MANUAL_REVIEW }

    public record ValidationOutcome(CheckResult checkResult, ValidationDecision decision) {}

    public ValidationOutcome validate(OcrExtractionResponse ocr, Map<String, Object> customerData, String tier) {
        if (ocr == null) {
            // OCR service was unavailable — degrade gracefully
            CheckResult check = CheckResult.builder()
                    .stepName("ocr_extraction")
                    .status("skipped")
                    .confidenceScore(0.0)
                    .result(Map.of("reason", "ocr_service_unavailable"))
                    .errorMessage("OCR service unavailable")
                    .build();
            return new ValidationOutcome(check, ValidationDecision.MANUAL_REVIEW);
        }

        Map<String, Object> resultDetails = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Rule 1: OCR confidence gate — don't validate on bad scans, send for human review
        if (ocr.getOverallConfidence() < confidenceThreshold) {
            resultDetails.put("overall_confidence", ocr.getOverallConfidence());
            resultDetails.put("reason", "low_ocr_confidence");
            return buildOutcome("ocr_extraction", "review", ValidationDecision.MANUAL_REVIEW,
                    ocr.getOverallConfidence(), resultDetails, null);
        }

        OcrExtractionResponse.OcrFields fields = ocr.getFields();
        resultDetails.put("document_type", ocr.getDocumentType());
        resultDetails.put("document_type_confidence", ocr.getDocumentTypeConfidence());
        resultDetails.put("overall_confidence", ocr.getOverallConfidence());

        // Rule 2: ID number — exact match after stripping non-alphanumeric
        String submittedId = normalizeAlphanumeric(asString(customerData.get("idNumber")));
        String ocrId = normalizeAlphanumeric(fields != null ? fields.getIdNumber() : null);
        if (submittedId != null && ocrId != null) {
            boolean idMatch = submittedId.equals(ocrId);
            resultDetails.put("id_number_match", idMatch);
            if (!idMatch) failures.add("id_number_mismatch");
        } else {
            resultDetails.put("id_number_match", "skipped_missing_ocr_field");
        }

        // Rule 3: Name — fuzzy match (Jaro-Winkler ≥ threshold), warn don't reject
        String submittedName = normalizeName(
                customerData.get("firstName") + " " + customerData.get("lastName"));
        String ocrName = normalizeName(fields != null ? fields.getFullName() : null);
        if (submittedName != null && ocrName != null && !ocrName.isBlank()) {
            double similarity = jaroWinkler(submittedName, ocrName);
            resultDetails.put("name_similarity", Math.round(similarity * 100.0) / 100.0);
            if (similarity < nameSimilarityThreshold) warnings.add("name_similarity_below_threshold");
        } else {
            resultDetails.put("name_match", "skipped_missing_ocr_field");
        }

        // Rule 4: Date of birth — exact match after parsing
        // dateOfBirth is stored as LocalDate in JSONB → Jackson deserializes as [year,month,day] ArrayList
        String submittedDob = asString(customerData.get("dateOfBirth"));
        String ocrDob = fields != null ? fields.getDateOfBirth() : null;
        if (submittedDob != null && ocrDob != null) {
            LocalDate submitted = parseDate(submittedDob);
            LocalDate ocred = parseDate(ocrDob);
            if (submitted != null && ocred != null) {
                boolean dobMatch = submitted.equals(ocred);
                resultDetails.put("dob_match", dobMatch);
                if (!dobMatch) failures.add("dob_mismatch");
            } else {
                resultDetails.put("dob_match", "skipped_parse_error");
            }
        } else {
            resultDetails.put("dob_match", "skipped_missing_field");
        }

        // Rule 5: Document type match (Tier2+)
        if (!"tier1".equalsIgnoreCase(tier)) {
            String submittedIdType = (String) customerData.get("idType");
            if (submittedIdType != null && ocr.getDocumentType() != null) {
                String expected = ID_TYPE_MAP.getOrDefault(submittedIdType.toLowerCase(), submittedIdType.toLowerCase());
                boolean typeMatch = expected.equals(ocr.getDocumentType());
                resultDetails.put("document_type_match", typeMatch);
                if (!typeMatch) failures.add("document_type_mismatch");
            }
        }

        // Rule 6: Expiry date — reject if document is expired
        String ocrExpiry = fields != null ? fields.getExpiryDate() : null;
        if (ocrExpiry != null) {
            LocalDate expiry = parseDate(ocrExpiry);
            if (expiry != null) {
                boolean expired = expiry.isBefore(LocalDate.now());
                resultDetails.put("document_expired", expired);
                if (expired) failures.add("document_expired");
            }
        }

        // Rule 7: Nationality (Tier3 only) — soft check, warn don't reject
        if ("tier3".equalsIgnoreCase(tier)) {
            String submittedNationality = normalizeAlphanumeric(asString(customerData.get("nationality")));
            String ocrNationality = normalizeAlphanumeric(ocr.getCountryIso3());
            if (submittedNationality != null && ocrNationality != null) {
                boolean nationalityMatch = submittedNationality.contains(ocrNationality)
                        || ocrNationality.contains(submittedNationality);
                resultDetails.put("nationality_match", nationalityMatch);
                if (!nationalityMatch) warnings.add("nationality_mismatch");
            }
        }

        if (!failures.isEmpty()) {
            resultDetails.put("failure_reasons", failures);
            return buildOutcome("ocr_extraction", "failed", ValidationDecision.REJECTED,
                    ocr.getOverallConfidence(), resultDetails, String.join(", ", failures));
        }

        if (!warnings.isEmpty()) {
            resultDetails.put("warnings", warnings);
            return buildOutcome("ocr_extraction", "review", ValidationDecision.MANUAL_REVIEW,
                    ocr.getOverallConfidence(), resultDetails, null);
        }

        return buildOutcome("ocr_extraction", "passed", ValidationDecision.APPROVED,
                ocr.getOverallConfidence(), resultDetails, null);
    }

    private ValidationOutcome buildOutcome(String step, String status, ValidationDecision decision,
                                           double confidence, Map<String, Object> result, String errorMessage) {
        return new ValidationOutcome(
                CheckResult.builder()
                        .stepName(step)
                        .status(status)
                        .confidenceScore(confidence)
                        .result(result)
                        .errorMessage(errorMessage)
                        .build(),
                decision);
    }

    @SuppressWarnings("unchecked")
    private static String asString(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        // LocalDate stored in JSONB deserializes as [year, month, day]
        if (value instanceof java.util.List<?> list && list.size() == 3) {
            return String.format("%04d-%02d-%02d", list.get(0), list.get(1), list.get(2));
        }
        return String.valueOf(value);
    }

    private static String normalizeAlphanumeric(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeName(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", "")
                .replaceAll("\\s+", " ").strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        log.warn("Could not parse date: {}", value);
        return null;
    }

    // Jaro-Winkler similarity implementation — avoids adding commons-text dependency
    static double jaroWinkler(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDist = Math.max(Math.max(len1, len2) / 2 - 1, 0);
        boolean[] s1Matched = new boolean[len1];
        boolean[] s2Matched = new boolean[len2];

        int matches = 0;
        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDist);
            int end = Math.min(i + matchDist + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matched[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matched[i] = s2Matched[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        int transpositions = 0, k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matched[i]) continue;
            while (!s2Matched[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        double jaro = (matches / (double) len1
                + matches / (double) len2
                + (matches - transpositions / 2.0) / matches) / 3.0;

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
