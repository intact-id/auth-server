package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompanyResponseDto {
    private String id;
    private String companyIdentifierCode;
    private String legalName;
    private String tradingName;
    private String registrationNumber;
    private String country;
    private String businessType;
    private String status;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
