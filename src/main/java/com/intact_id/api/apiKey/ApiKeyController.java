package com.intact_id.api.apiKey;

import com.intact_id.dto.response.ApiKeyGenerationResponseDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.service.apiKey.ApiKeyService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<ApiKeyGenerationResponseDto>> generateApiKey(
            @RequestParam String companyId) { // In real app, get companyId from auth context

        try {
            var response = apiKeyService.generateApiKey(companyId);
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

    @DeleteMapping("/{keyId}")
    @Operation(summary = "Revoke API key")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(
            @PathVariable String keyId,
            @RequestParam String companyId) {

        try {
            apiKeyService.revokeApiKey(companyId, keyId);
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
    public ResponseEntity<ApiResponse<List<ApiKeyGenerationResponseDto>>> getApiKeys(
            @RequestParam String companyId) {

        var response = apiKeyService.getApiKeys(companyId);
        return ResponseEntity.ok(
                ApiResponse.<List<ApiKeyGenerationResponseDto>>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("API Keys retrieved successfully")
                        .success(true)
                        .data(response)
                        .build());
    }
}
