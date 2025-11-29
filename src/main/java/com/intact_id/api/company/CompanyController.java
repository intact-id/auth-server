package com.intact_id.api.company;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.dto.request.CompanyUpdateRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.CompanyResponseDto;
import com.intact_id.service.CompanyService.CompanyService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Company Management", description = "Manage company details")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/{id}")
    @Operation(summary = "Get company by ID")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> getCompanyById(@PathVariable String id) {
        try {
            Company company = companyService.getCompanyById(id);
            CompanyResponseDto response = mapToDto(company);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company retrieved successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current company details")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> getCurrentCompany(
            @RequestParam String companyId) { // In real app, get from auth context

        return getCompanyById(companyId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update company details")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> updateCompany(
            @PathVariable String id,
            @Valid @RequestBody CompanyUpdateRequestDto request) {

        try {
            Company company = companyService.updateCompany(id, request);
            CompanyResponseDto response = mapToDto(company);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company updated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping
    @Operation(summary = "Get all companies with filters (Admin)")
    public ResponseEntity<ApiResponse<Page<CompanyResponseDto>>> getAllCompanies(
            @RequestParam(required = false) GlobalStatuses status,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String businessType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Fetching companies - Status: {}, Country: {}, BusinessType: {}", status, country, businessType);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

        Page<Company> companies = companyService.getAllCompanies(status, country, businessType, pageable);
        Page<CompanyResponseDto> response = companies.map(this::mapToDto);

        return ResponseEntity.ok(
                ApiResponse.<Page<CompanyResponseDto>>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Companies retrieved successfully")
                        .success(true)
                        .data(response)
                        .build());
    }

    private CompanyResponseDto mapToDto(Company company) {
        return CompanyResponseDto.builder()
                .id(company.getId())
                .companyIdentifierCode(company.getCompanyIdentifierCode())
                .legalName(company.getLegalName())
                .tradingName(company.getTradingName())
                .registrationNumber(company.getRegistrationNumber())
                .country(company.getCountry())
                .businessType(company.getBusinessType())
                .status(company.getStatus().name())
                .approvedAt(company.getApprovedAt())
                .createdAt(company.getCreatedAt())
                .build();
    }
}
