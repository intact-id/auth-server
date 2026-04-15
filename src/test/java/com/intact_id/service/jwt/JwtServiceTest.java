package com.intact_id.service.jwt;

import com.intact_id.db.model.user.Permission;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Set values that would normally be injected @Value
        ReflectionTestUtils.setField(jwtService, "secret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"); // 256-bit key
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "intact-id");
    }

    @Test
    void generateRefreshToken_shouldGenerateUniqueTokensForSameUser_whenCalledRapidly() {
        // Arrange
        User user = new User();
        user.setId("1");
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        
        Role role = new Role();
        role.setName("USER");
        role.setPermissions(new HashSet<>());
        user.setRoles(Collections.singleton(role));

        // Act
        // Generate two tokens as fast as possible
        String token1 = jwtService.generateRefreshToken(user, "session-1");
        String token2 = jwtService.generateRefreshToken(user, "session-1");

        // Assert
        // This fails if tokens are identical (collision)
        assertNotEquals(token1, token2, "Refresh tokens should be unique even when generated rapidly");
    }
}
