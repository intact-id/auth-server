package com.intact_id.service.auth;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.user.Permission;
import com.intact_id.db.model.user.RefreshToken;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.RefreshTokenRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.LoginRequestDto;
import com.intact_id.dto.auth.LoginResponseDto;
import com.intact_id.dto.auth.TokenResponseDto;
import com.intact_id.service.audit.AuditService;
import com.intact_id.service.jwt.JwtService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GeneratorUtils generatorUtils;
    private final AuditService auditService;
    @Value("${jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;


    /**
     * Authenticate user and generate tokens
     */
    @Transactional
    public LoginResponseDto login(LoginRequestDto request, HttpServletRequest httpRequest) {
        log.info("Login attempt for username: {}", request.getUsername());
        // Find user
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("User not found: {}", request.getUsername());
                    return new ApplicationExceptions.IntactUnauthorizedException("Invalid credentials");
                });

        log.info("User found: {}, Status: {}, Locked: {}", user.getUsername(), user.getStatus(), user.isLocked());

        // Check if account is locked
        if (user.isLocked()) {
            log.warn("Account locked: {}", request.getUsername());
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "Account is locked. Please try again later or contact support.");
        }

        // Check if account is active
        if (user.getStatus() != GlobalStatuses.ACTIVE) {
            log.warn("Account not active: {}, status: {}", request.getUsername(), user.getStatus());
            throw new ApplicationExceptions.IntactUnauthorizedException("Account is not active");
        }

        // Verify password
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        log.info("Password match result for user {}: {}", request.getUsername(), passwordMatches);

        if (!passwordMatches) {
            log.warn("Invalid password for user: {}", request.getUsername());
            user.incrementFailedAttempts();
            userRepository.save(user);
            throw new ApplicationExceptions.IntactUnauthorizedException("Invalid credentials");
        }

        String sessionId = java.util.UUID.randomUUID().toString();

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user, sessionId);
        String refreshToken = jwtService.generateRefreshToken(user, sessionId);

        // Save refresh token
        RefreshToken savedRefreshToken = saveRefreshToken(user, refreshToken, httpRequest, sessionId);

        // Update user login info
        user.recordLogin();
        userRepository.save(user);

        auditService.startSession(sessionId, user, savedRefreshToken, getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
        auditService.recordLogin(sessionId, user, getClientIp(httpRequest));

        log.info("User logged in successfully: {}", request.getUsername());

        return LoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(Role::getName).toList())
                .permissions(user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(Permission::getName)
                        .distinct()
                        .toList())
                .loginAt(LocalDateTime.now())
                .mustChangePassword(user.getMustChangePassword())
                .build();
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public TokenResponseDto refreshAccessToken(String refreshTokenValue, HttpServletRequest httpRequest) {
        log.info("Refreshing access token");

        // Hash the refresh token
        String tokenHash = generatorUtils.hashString(refreshTokenValue);

        // Find refresh token
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found");
                    return new ApplicationExceptions.IntactUnauthorizedException("Invalid refresh token");
                });

        // Validate refresh token
        if (!refreshToken.isValid()) {
            log.warn("Refresh token is invalid or expired");
            throw new ApplicationExceptions.IntactUnauthorizedException("Refresh token is invalid or expired");
        }

        // Get user
        User user = refreshToken.getUser();
        String sessionId = refreshToken.getSessionId() != null
                ? refreshToken.getSessionId()
                : jwtService.extractSessionId(refreshTokenValue);

        // Check if user is still active
        if (!user.isActive()) {
            log.warn("User account is not active: {}", user.getUsername());
            throw new ApplicationExceptions.IntactUnauthorizedException("Account is not active");
        }

        // Generate new access token
        String newAccessToken = jwtService.generateAccessToken(user, sessionId);

        // Generate new refresh token with retry logic for uniqueness
        String newRefreshToken = null;
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                newRefreshToken = jwtService.generateRefreshToken(user, sessionId);
                String newTokenHash = generatorUtils.hashString(newRefreshToken);

                // Check if this token hash already exists
                if (refreshTokenRepository.findByTokenHash(newTokenHash).isEmpty()) {
                    break; // Token is unique, proceed
                }

                // Token exists, wait a bit and try again
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApplicationExceptions.IntactUnauthorizedException("Token generation interrupted");
            }
        }

        if (newRefreshToken == null) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Failed to generate unique refresh token");
        }

        // Revoke old token first
        refreshToken.revoke();
        refreshTokenRepository.saveAndFlush(refreshToken); // Use saveAndFlush to ensure immediate persistence

        // Save new refresh token
        RefreshToken savedRefreshToken = saveRefreshToken(user, newRefreshToken, httpRequest, sessionId);
        auditService.linkRefreshToken(sessionId, savedRefreshToken);
        auditService.recordTokenRefresh(sessionId, user, httpRequest != null ? getClientIp(httpRequest) : null);

        log.info("Access token refreshed for user: {}", user.getUsername());

        return TokenResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiration)
                .build();
    }

    /**
     * Logout user by revoking refresh token
     */
    @Transactional
    public void logout(String refreshTokenValue, HttpServletRequest httpRequest) {
        log.info("Logout request");

        String tokenHash = generatorUtils.hashString(refreshTokenValue);

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    if (token.getSessionId() != null) {
                        auditService.closeSession(token.getSessionId(), token.getUser(),
                                httpRequest != null ? getClientIp(httpRequest) : null);
                    }
                    log.info("Refresh token revoked for user: {}", token.getUser().getUsername());
                });
    }

    public String getAuthorizedUsername(){

        Authentication authentication= SecurityContextHolder.getContext().getAuthentication();

        var response = authentication.getPrincipal() instanceof String ? ((String) authentication.getPrincipal()) : null;
        log.info("Authentication principal: {}", response);
        return (String) authentication.getPrincipal();
    }

    /**
     * Save refresh token to database
     */
    private RefreshToken saveRefreshToken(User user, String refreshToken, HttpServletRequest httpRequest, String sessionId) {
        String tokenHash = generatorUtils.hashString(refreshToken);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000));
        token.setSessionId(sessionId);

        if (httpRequest != null) {
            token.setIpAddress(getClientIp(httpRequest));
            token.setUserAgent(httpRequest.getHeader("User-Agent"));
        }

        return refreshTokenRepository.save(token);
    }

    /**
     * Get client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
