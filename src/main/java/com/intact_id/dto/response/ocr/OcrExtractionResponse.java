package com.intact_id.dto.response.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class OcrExtractionResponse {

    @JsonProperty("document_type")
    private String documentType;

    @JsonProperty("document_type_confidence")
    private double documentTypeConfidence;

    @JsonProperty("country_iso3")
    private String countryIso3;

    @JsonProperty("fields")
    private OcrFields fields;

    @JsonProperty("field_confidence")
    private Map<String, Double> fieldConfidence;

    @JsonProperty("overall_confidence")
    private double overallConfidence;

    @JsonProperty("raw_text")
    private String rawText;

    @Data
    @NoArgsConstructor
    public static class OcrFields {

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("surname")
        private String surname;

        @JsonProperty("given_names")
        private String givenNames;

        @JsonProperty("id_number")
        private String idNumber;

        @JsonProperty("date_of_birth")
        private String dateOfBirth;

        @JsonProperty("sex")
        private String sex;

        @JsonProperty("nationality")
        private String nationality;

        @JsonProperty("expiry_date")
        private String expiryDate;

        @JsonProperty("date_of_issue")
        private String dateOfIssue;

        @JsonProperty("place_of_birth")
        private String placeOfBirth;
    }
}
