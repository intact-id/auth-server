package com.intact_id.service.auth;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.RefreshTokenRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.LoginRequestDto;
import com.intact_id.dto.auth.LoginResponseDto;
import com.intact_id.service.audit.AuditService;
import com.intact_id.service.jwt.JwtService;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(authenticationService, "accessTokenExpiration", 3600L);
        ReflectionTestUtils.setField(authenticationService, "refreshTokenExpiration", 86400L);
    }

    @Test
    void login_shouldReturnMustChangePasswordTrue_whenUserHasFlagTrue() {
        // Arrange
        String username = "testuser";
        String password = "password";

        User user = new User();
        user.setId("user-1");
        user.setUsername(username);
        user.setPasswordHash("hashedPassword");
        user.setStatus(GlobalStatuses.ACTIVE);
        user.setMustChangePassword(true); // Flag is TRUE
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

        // Assert
        assertTrue(response.getMustChangePassword(), "mustChangePassword should be true in response");
    }
}
