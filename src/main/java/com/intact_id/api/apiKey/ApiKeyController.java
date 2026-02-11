package com.intact_id.api.apiKey;

import com.intact_id.dto.response.ApiKeyGenerationResponseDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.ApiKeyUsageResponseDto;
import com.intact_id.service.apiKey.ApiKeyService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/companies/api-keys")
@RequiredArgsConstructor
@Tag(name = "Company - API Keys", description = "Manage company API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping("/generate")
    @Operation(summary = "Generate or regenerate API key for company")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ApiKeyGenerationResponseDto>> generateApiKey(
            @RequestParam(required = false) String companyId) {

        try {
            String resolvedCompanyId = com.intact_id.util.SecurityContextUtil.resolveCompanyId(companyId);
            var response = apiKeyService.generateProdApiKey(resolvedCompanyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<ApiKeyGenerationResponseDto>builder()
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("API Key generated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<ApiKeyGenerationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/generate/dev")
    @Operation(summary = "Generate or regenerate DEV API key for company (Admin only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ApiKeyGenerationResponseDto>> generateDevApiKey(
            @RequestParam(required = false) String companyId) {

        try {
            String resolvedCompanyId = com.intact_id.util.SecurityContextUtil.resolveCompanyId(companyId);
            var response = apiKeyService.generateDevApiKey(resolvedCompanyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<ApiKeyGenerationResponseDto>builder()
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("DEV API Key generated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<ApiKeyGenerationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/generate/prod")
    @Operation(summary = "Generate or regenerate PROD API key for company (Admin only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ApiKeyGenerationResponseDto>> generateProdApiKey(
            @RequestParam(required = false) String companyId) {

        try {
            String resolvedCompanyId = com.intact_id.util.SecurityContextUtil.resolveCompanyId(companyId);
            var response = apiKeyService.generateProdApiKey(resolvedCompanyId);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<ApiKeyGenerationResponseDto>builder()
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("PROD API Key generated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<ApiKeyGenerationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }
    @DeleteMapping("/{keyId}")
    @Operation(summary = "Revoke API key")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(
            @PathVariable String keyId,
            @RequestParam(required = false) String companyId) {

        try {
            String resolvedCompanyId = com.intact_id.util.SecurityContextUtil.resolveCompanyId(companyId);
            apiKeyService.revokeApiKey(resolvedCompanyId, keyId);
            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("API Key revoked successfully")
                            .success(true)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Void>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping
    @Operation(summary = "Get company API keys")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ApiKeyGenerationResponseDto>>> getApiKeys(
            @RequestParam(required = false) String companyId) {

        String resolvedCompanyId = com.intact_id.util.SecurityContextUtil.resolveCompanyId(companyId);
        var response = apiKeyService.getApiKeys(resolvedCompanyId);
        return ResponseEntity.ok(
                ApiResponse.<List<ApiKeyGenerationResponseDto>>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("API Keys retrieved successfully")
                        .success(true)
                        .data(response)
                        .build());
    }

    @GetMapping("/usage")
    @Operation(summary = "Get API key usage metrics (Admin only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ApiKeyUsageResponseDto>> getUsage(
            @RequestParam(required = false) String companyId) {

        String resolvedCompanyId = com.intact_id.util.SecurityContextUtil.resolveCompanyId(companyId);
        ApiKeyUsageResponseDto response = apiKeyService.getUsage(resolvedCompanyId);
        return ResponseEntity.ok(
                ApiResponse.<ApiKeyUsageResponseDto>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("API Key usage retrieved successfully")
                        .success(true)
                        .data(response)
                        .build());
    }
}
