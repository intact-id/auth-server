package com.intact_id.service.user;

import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.RoleRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.UserCreateDto;
import com.intact_id.dto.auth.UserResponseDto;
import com.intact_id.service.email.EmailService;
import com.intact_id.util.generator.GeneratorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private GeneratorUtils generatorUtils;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void createUser_shouldAssignDefaultViewerRole_whenNoRolesProvided() {
        // Arrange
        UserCreateDto request = new UserCreateDto();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setFirstName("Test");
        request.setLastName("User");
        
        // Mock role repository to return VIEWER role
        Role viewerRole = new Role();
        viewerRole.setName("VIEWER");
        viewerRole.setId("role-viewer-id");
        when(roleRepository.findByName("VIEWER")).thenReturn(Optional.of(viewerRole));

        // Mock user repository
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId("user-1");
            savedUser.setCreatedAt(LocalDateTime.now());
            return savedUser;
        });

        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

        // Act
        UserResponseDto response = userService.createUser(request);

        // Assert
        assertEquals(1, response.getRoles().size());
        assertEquals("VIEWER", response.getRoles().get(0));
        verify(roleRepository, times(1)).findByName("VIEWER");
    }

    @Test
    void createUser_shouldSetMustChangePasswordToTrue_whenNotProvided() {
        // Arrange
        UserCreateDto request = new UserCreateDto();
        request.setUsername("testuser2");
        request.setEmail("test2@example.com");
        request.setPassword("Password@123");
        request.setFirstName("Test");
        request.setLastName("User");
        // mustChangePassword is NOT set explicitly, relying on default = true in DTO

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("VIEWER")).thenReturn(Optional.of(new Role()));
        
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId("user-2");
            return savedUser;
        });

        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");

        // Act
        UserResponseDto response = userService.createUser(request);

        // Assert
        assertTrue(response.getMustChangePassword(), "mustChangePassword should be true by default");
    }
}
