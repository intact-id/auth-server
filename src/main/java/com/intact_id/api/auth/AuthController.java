package com.intact_id.api.auth;

import com.intact_id.dto.auth.LoginRequestDto;
import com.intact_id.dto.auth.LoginResponseDto;
import com.intact_id.dto.auth.RefreshTokenRequestDto;
import com.intact_id.dto.auth.TokenResponseDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.service.auth.AuthenticationService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest) {
        try {
            log.info("Login request for username: {}", request.getUsername());

            LoginResponseDto response = authenticationService.login(request, httpRequest);

            return ResponseEntity.ok(
                    ApiResponse.<LoginResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Login successful")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<LoginResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage("Authentication failed")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Login error", e);
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

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<TokenResponseDto>> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDto request) {
        try {
            log.info("Token refresh request");

            TokenResponseDto response = authenticationService.refreshAccessToken(request.getRefreshToken());

            return ResponseEntity.ok(
                    ApiResponse.<TokenResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Token refreshed successfully")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    ApiResponse.<TokenResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.UNAUTHORIZED.value())
                            .responseMessage("Token refresh failed")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        } catch (Exception e) {
            log.error("Token refresh error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<TokenResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Internal server error")
                            .errorMessage(e.getMessage())
                            .isInternalError(true)
                            .build());
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequestDto request) {
        try {
            log.info("Logout request");

            authenticationService.logout(request.getRefreshToken());

            return ResponseEntity.ok(
                    ApiResponse.<Void>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Logged out successfully")
                            .build());
        } catch (Exception e) {
            log.error("Logout error", e);
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
