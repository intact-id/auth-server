package com.intact_id.api.auth;

import com.intact_id.dto.auth.CompanyAccessTokenResponseDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.service.auth.CompanyAccessTokenService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client-credentials token exchange for company (API key) callers. Exchange X-API-Key /
 * X-API-Secret for a short-lived access token once, then use that token on /verify instead of
 * sending key+secret on every request. See CompanyAccessTokenService for the reasoning.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Company Access Token", description = "Client-credentials token exchange for company API auth")
public class CompanyAccessTokenController {

    private final CompanyAccessTokenService companyAccessTokenService;

    @PostMapping("/token")
    @Operation(summary = "Exchange API key + secret for a short-lived access token",
            description = "Send X-API-Key and X-API-Secret headers. The returned bearer token is valid "
                    + "for a short window and should be reused across requests instead of re-sending "
                    + "the key/secret on every call.")
    public ResponseEntity<ApiResponse<CompanyAccessTokenResponseDto>> issueToken(HttpServletRequest request) {
        try {
            CompanyAccessTokenResponseDto response = companyAccessTokenService.issueToken(request);
            return ResponseEntity.ok(
                    ApiResponse.<CompanyAccessTokenResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Access token issued")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactTooManyRequestsException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    ApiResponse.<CompanyAccessTokenResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.TOO_MANY_REQUESTS.value())
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException | ApplicationExceptions.IntactCompanyNotFoundException e) {
            log.warn("Token issuance failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<CompanyAccessTokenResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage("Invalid API credentials")
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Token issuance error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<CompanyAccessTokenResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .isInternalError(true)
                            .build());
        }
    }

    @PostMapping("/token/revoke")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Revoke an access token before its natural expiry",
            description = "Pass the token to revoke as a Bearer Authorization header. Adds its jti to "
                    + "the revocation denylist so it stops working immediately, even though it hasn't expired.")
    public ResponseEntity<ApiResponse<Void>> revokeToken(
            @RequestHeader("Authorization") String authorizationHeader) {
        try {
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                throw new ApplicationExceptions.IntactBadRequestException("Missing Bearer token");
            }
            companyAccessTokenService.revokeToken(authorizationHeader.substring(7));
            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Token revoked")
                            .build());
        } catch (ApplicationExceptions.IntactBadRequestException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<Void>builder()
                            .success(false)
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Token revocation error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Void>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .isInternalError(true)
                            .build());
        }
    }
}
