package com.intact_id.service.auth;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.RefreshTokenRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.LoginRequestDto;
import com.intact_id.dto.auth.LoginResponseDto;
import com.intact_id.security.totp.TotpService;
import com.intact_id.service.audit.AuditService;
import com.intact_id.service.jwt.JwtService;
import com.intact_id.util.generator.GeneratorUtils;
import com.intact_id.util.security.RequestHeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GeneratorUtils generatorUtils;

    @Mock
    private AuditService auditService;

    @Mock
    private TotpService totpService;

    @Mock
    private BackupCodeService backupCodeService;

    @Mock
    private TrustedDeviceService trustedDeviceService;

    @Mock
    private RequestHeaderUtil requestHeaderUtil;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(authenticationService, "accessTokenExpiration", 3600L);
        ReflectionTestUtils.setField(authenticationService, "refreshTokenExpiration", 86400L);
        ReflectionTestUtils.setField(authenticationService, "mfaChallengeExpiration", 300000L);
    }

    @Test
    void login_shouldReturnTokensAndExpiry_onSuccessfulAuthentication() {
        // Arrange
        String username = "testuser";
        String password = "password";

        User user = new User();
        user.setId("user-1");
        user.setUsername(username);
        user.setPasswordHash("hashedPassword");
        user.setStatus(GlobalStatuses.ACTIVE);
        user.setMustChangePassword(true);
        user.setRoles(new HashSet<>());

        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(true);
        when(jwtService.generateAccessToken(any(User.class), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any(User.class), anyString())).thenReturn("refresh-token");
        when(generatorUtils.hashString(anyString())).thenReturn("hashed-token");

        // Act
        LoginResponseDto response = authenticationService.login(loginRequest, httpRequest);

        // Assert — the login response is intentionally minimal (tokens only);
        // profile data like mustChangePassword/roles/permissions now comes from /api/auth/current-user.
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals(86400L, response.getRefreshExpiresIn());
        assertEquals(false, response.getMfaRequired());
    }

    @Test
    void login_shouldReturnMfaChallenge_whenUserHas2faEnabledAndNoTrustedDevice() {
        // Arrange
        String username = "testuser";
        String password = "password";

        User user = new User();
        user.setId("user-1");
        user.setUsername(username);
        user.setPasswordHash("hashedPassword");
        user.setStatus(GlobalStatuses.ACTIVE);
        user.setIs2faEnabled(true);
        user.setRoles(new HashSet<>());

        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(true);
        when(jwtService.generateMfaChallengeToken(user)).thenReturn("mfa-challenge-token");

        // Act
        LoginResponseDto response = authenticationService.login(loginRequest, httpRequest);

        // Assert — no full tokens are ever issued when 2FA is required and no trusted
        // device is recognized; only a short-lived challenge token is returned.
        assertEquals(true, response.getMfaRequired());
        assertEquals("mfa-challenge-token", response.getMfaToken());
        assertEquals(null, response.getAccessToken());
        assertEquals(null, response.getRefreshToken());
    }
}
