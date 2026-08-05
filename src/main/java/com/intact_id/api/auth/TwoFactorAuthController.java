package com.intact_id.api.auth;

import com.intact_id.dto.auth.BackupCodesResponseDto;
import com.intact_id.dto.auth.Enable2FaResponseDto;
import com.intact_id.dto.auth.LoginResponseDto;
import com.intact_id.dto.auth.MfaLoginVerifyRequestDto;
import com.intact_id.dto.auth.TrustedDeviceDto;
import com.intact_id.dto.auth.VerifyTotpRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.service.auth.AuthenticationService;
import com.intact_id.service.auth.BackupCodeService;
import com.intact_id.service.auth.TrustedDeviceService;
import com.intact_id.service.auth.TwoFactorAuthService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/2fa")
@Tag(name = "Two-Factor Authentication", description = "Endpoints for enabling, disabling and verifying TOTP-based 2FA")
@SecurityRequirement(name = "bearerAuth")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;
    private final AuthenticationService authenticationService;
    private final BackupCodeService backupCodeService;
    private final TrustedDeviceService trustedDeviceService;

    @PostMapping("/enable")
    @Operation(summary = "Enable 2FA for the authenticated user and return a QR code to scan")
    public ResponseEntity<ApiResponse<Enable2FaResponseDto>> enable2Fa(HttpServletRequest request) {
        try {
            Enable2FaResponseDto response = twoFactorAuthService.enabled2Fa(request);

            return ResponseEntity.ok(
                    ApiResponse.<Enable2FaResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("2FA enabled successfully")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("Enable 2FA failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<Enable2FaResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage("Authentication failed")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Enable 2FA error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Enable2FaResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable 2FA for the authenticated user")
    public ResponseEntity<ApiResponse<Enable2FaResponseDto>> disable2Fa(HttpServletRequest request) {
        try {
            Enable2FaResponseDto response = twoFactorAuthService.disabled2Fa(request);

            return ResponseEntity.ok(
                    ApiResponse.<Enable2FaResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("2FA disabled successfully")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("Disable 2FA failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<Enable2FaResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage("Authentication failed")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Disable 2FA error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Enable2FaResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify a TOTP code for the authenticated user")
    public ResponseEntity<ApiResponse<Boolean>> verify2Fa(
            @Valid @RequestBody VerifyTotpRequestDto request,
            HttpServletRequest httpRequest) {
        try {
            boolean isValid = twoFactorAuthService.validateTotpVerificationCode(
                    request.getVerificationCode(), httpRequest);

            if (!isValid) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        ApiResponse.<Boolean>builder()
                                .success(false)
                                .responseCode(HttpStatus.BAD_REQUEST.value())
                                .responseMessage("Invalid verification code")
                                .data(false)
                                .isInternalError(false)
                                .build());
            }

            return ResponseEntity.ok(
                    ApiResponse.<Boolean>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Verification successful")
                            .data(true)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("Verify 2FA failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<Boolean>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage("Authentication failed")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Verify 2FA error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Boolean>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @PostMapping("/login-verify")
    @Operation(summary = "Complete a 2FA login challenge using a TOTP code or a backup code")
    @SecurityRequirements // public — authenticates via the mfaToken in the body, not a bearer header
    public ResponseEntity<ApiResponse<LoginResponseDto>> loginVerify(
            @Valid @RequestBody MfaLoginVerifyRequestDto request,
            HttpServletRequest httpRequest) {
        try {
            LoginResponseDto response = authenticationService.verifyMfaLogin(request, httpRequest);

            return ResponseEntity.ok(
                    ApiResponse.<LoginResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Login successful")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.InvalidRequestException e) {
            log.warn("MFA login-verify rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<LoginResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("MFA login-verify failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<LoginResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("MFA login-verify error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<LoginResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @PostMapping("/backup-codes")
    @Operation(summary = "Generate (or regenerate) the authenticated user's backup codes")
    public ResponseEntity<ApiResponse<BackupCodesResponseDto>> generateBackupCodes(HttpServletRequest request) {
        try {

            BackupCodesResponseDto response = backupCodeService.generateBackupCodes();

            return ResponseEntity.ok(
                    ApiResponse.<BackupCodesResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Backup codes generated successfully")
                            .data(response)
                            .build());
        } catch (Exception e) {
            log.error("Generate backup codes error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<BackupCodesResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @GetMapping("/backup-codes/count")
    @Operation(summary = "Get the count of remaining unused backup codes")
    public ResponseEntity<ApiResponse<Integer>> getRemainingBackupCodeCount(HttpServletRequest request) {
        try {
            String username = authenticationService.getAuthorizedUsername();
            int count = backupCodeService.getRemainingBackupCodeCount(username);

            return ResponseEntity.ok(
                    ApiResponse.<Integer>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Remaining backup code count retrieved successfully")
                            .data(count)
                            .build());
        } catch (Exception e) {
            log.error("Get remaining backup code count error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Integer>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @GetMapping("/trusted-devices")
    @Operation(summary = "List the authenticated user's trusted devices")
    public ResponseEntity<ApiResponse<List<TrustedDeviceDto>>> listTrustedDevices(HttpServletRequest request) {
        try {
            String username = authenticationService.getAuthorizedUsername();
            List<TrustedDeviceDto> devices = trustedDeviceService.listTrustedDevices(username);

            return ResponseEntity.ok(
                    ApiResponse.<List<TrustedDeviceDto>>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Trusted devices retrieved successfully")
                            .data(devices)
                            .build());
        } catch (Exception e) {
            log.error("List trusted devices error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<List<TrustedDeviceDto>>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @DeleteMapping("/trusted-devices/{id}")
    @Operation(summary = "Revoke a trusted device")
    public ResponseEntity<ApiResponse<Void>> revokeTrustedDevice(
            @PathVariable("id") String id, HttpServletRequest request) {
        try {
            String username = authenticationService.getAuthorizedUsername();
            trustedDeviceService.revokeTrustedDevice(username, id);

            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Trusted device revoked successfully")
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("Revoke trusted device failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<Void>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (ApplicationExceptions.IntactResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Void>builder()
                            .success(false)
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Revoke trusted device error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<Void>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }
}
