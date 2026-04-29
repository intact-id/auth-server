package com.intact_id.service.user;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.user.RefreshToken;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.db.repository.user.RefreshTokenRepository;
import com.intact_id.db.repository.user.RoleRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.service.audit.AuditService;
import com.intact_id.dto.auth.RoleResponseDto;
import com.intact_id.dto.auth.UserCreateDto;
import com.intact_id.dto.auth.UserResponseDto;
import com.intact_id.dto.email.EmailDTO;
import com.intact_id.service.email.EmailService;
import com.intact_id.util.SecurityContextUtil;
import com.intact_id.util.TenantContext;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.mail.internet.MailDateFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    @Value("${app.dashboard-url}")
    private String dashboardUrl;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final GeneratorUtils generatorUtils;

    /**
     * Create new user
     */
    @Transactional
    public UserResponseDto createUser(UserCreateDto request) {
        log.info("Creating user: {}", request.getUsername());
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(request.getCompanyId());
        if (resolvedCompanyId != null && !resolvedCompanyId.isBlank()) {
            request.setCompanyId(resolvedCompanyId);
        }

        // Check if username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApplicationExceptions.DuplicateResourceException(
                    "Username already exists: " + request.getUsername());
        }

        // Check if email exists within the same company scope
        if (request.getCompanyId() != null && !request.getCompanyId().isBlank()) {
            if (userRepository.existsByEmailAndCompany_Id(request.getEmail(), request.getCompanyId())) {
                throw new ApplicationExceptions.DuplicateResourceException(
                        "Email already exists: " + request.getEmail());
            }
        } else {
            if (userRepository.existsByEmailAndCompanyIsNull(request.getEmail())) {
                throw new ApplicationExceptions.DuplicateResourceException(
                        "Email already exists: " + request.getEmail());
            }
        }

        // Generate random password if not provided
        String plainPassword = request.getPassword();
        boolean isRandomPassword = false;

        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            plainPassword = generatorUtils.generateRandomPassword();
            isRandomPassword = true;
            log.info("Generated random password for user: {}, :{}", request.getUsername(), plainPassword);
        }

        // Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setStatus(GlobalStatuses.ACTIVE);
        user.setEmailVerified(false);

        if (request.getCompanyId() != null && !request.getCompanyId().isBlank()) {
            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Company not found: " + request.getCompanyId()));
            user.setCompany(company);
        }

        // Force password change if random password was generated
        user.setMustChangePassword(
                isRandomPassword || (request.getMustChangePassword() != null && request.getMustChangePassword()));
        user.setPasswordChangedAt(LocalDateTime.now());

        // Assign roles
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (String roleId : request.getRoleIds()) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                                "Role not found: " + roleId));
                // Prevent privilege escalation: only SUPER_ADMIN can assign the SUPER_ADMIN role
                if ("SUPER_ADMIN".equals(role.getName()) && !SecurityContextUtil.isSuperAdminAuthenticated()) {
                    throw new ApplicationExceptions.IntactUnauthorizedException(
                            "Insufficient privileges to assign the SUPER_ADMIN role");
                }
                roles.add(role);
            }
            user.setRoles(roles);
        } else {
            // Assign default VIEWER role if no roles provided
            roleRepository.findByName("VIEWER").ifPresent(role -> {
                Set<Role> roles = new HashSet<>();
                roles.add(role);
                user.setRoles(roles);
                log.info("Assigned default VIEWER role to user: {}", user.getUsername());
            });
        }

        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        // Send welcome email with credentials if random password was generated
        if (isRandomPassword) {
            sendWelcomeEmail(savedUser, plainPassword);
        }

        return toUserResponseDto(savedUser);
    }

    /**
     * Send welcome email with credentials
     */
    private void sendWelcomeEmail(User user, String temporaryPassword) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("fullName", user.getFirstName() + " " + user.getLastName());
            variables.put("username", user.getUsername());
            variables.put("password", temporaryPassword);
            variables.put("dashboardUrl", dashboardUrl + "/login");

            EmailDTO emailDTO = new EmailDTO();
            emailDTO.setTo(user.getEmail());
            emailDTO.setSubject("Welcome to Intact ID - Your Account Credentials");
            emailDTO.setTemplateName("welcome-email");
            emailDTO.setVariables(variables);

            emailService.sendTemplatedEmail(emailDTO);

            log.info("Welcome email sent to user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to user: {}", user.getEmail(), e);
        }
    }

    /**
     * Get user by ID
     */
    public UserResponseDto getUserById(String userId) {
        log.info("Fetching user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        return toUserResponseDto(user);
    }

    /**
     * Get user by username
     */
    public UserResponseDto getUserByUsername(String username) {
        log.info("Fetching user by username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + username));

        enforceUserScope(user);
        return toUserResponseDto(user);
    }

    /**
     * Get all users with filters
     */
    public Page<UserResponseDto> getAllUsers(GlobalStatuses status, String search, String companyId, Pageable pageable) {
        log.info("Fetching users with status: {}, search: {}, companyId: {}", status, search, companyId);

        String statusName = status != null ? status.name() : null;
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        Page<User> users = userRepository.findByFilters(resolvedCompanyId, statusName, search, pageable);

        return users.map(this::toUserResponseDto);
    }

    /**
     * Update user
     */
    @Transactional
    public UserResponseDto updateUser(String userId, UserCreateDto request) {
        log.info("Updating user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);

        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(request.getCompanyId());
        if (resolvedCompanyId != null) {
            request.setCompanyId(resolvedCompanyId);
        }
        // Update fields
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            String companyId = user.getCompany() != null ? user.getCompany().getId() : null;
            boolean emailTaken;
            if (companyId != null) {
                emailTaken = userRepository.existsByEmailAndCompany_Id(request.getEmail(), companyId);
            } else {
                emailTaken = userRepository.existsByEmailAndCompanyIsNull(request.getEmail());
            }
            if (emailTaken) {
                throw new ApplicationExceptions.DuplicateResourceException(
                        "Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getCompanyId() != null) {
            if (request.getCompanyId().isBlank()) {
                user.setCompany(null);
            } else {
                Company company = companyRepository.findById(request.getCompanyId())
                        .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                                "Company not found: " + request.getCompanyId()));
                user.setCompany(company);
            }
        }

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (String roleId : request.getRoleIds()) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                                "Role not found: " + roleId));
                if ("SUPER_ADMIN".equals(role.getName()) && !SecurityContextUtil.isSuperAdminAuthenticated()) {
                    throw new ApplicationExceptions.IntactUnauthorizedException(
                            "Insufficient privileges to assign the SUPER_ADMIN role");
                }
                roles.add(role);
            }
            user.setRoles(roles);
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", updatedUser.getUsername());

        return toUserResponseDto(updatedUser);
    }

    /**
     * Suspend user
     */
    @Transactional
    public void suspendUser(String userId) {
        log.info("Suspending user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        user.setStatus(GlobalStatuses.SUSPENDED);
        userRepository.save(user);

        log.info("User suspended: {}", user.getUsername());
    }

    /**
     * Activate user
     */
    @Transactional
    public void activateUser(String userId) {
        log.info("Activating user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        user.setStatus(GlobalStatuses.ACTIVE);
        user.unlock(); // Also unlock if locked
        userRepository.save(user);

        log.info("User activated: {}", user.getUsername());
    }

    /**
     * Unlock user
     */
    @Transactional
    public void unlockUser(String userId) {
        log.info("Unlocking user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        user.unlock();
        userRepository.save(user);

        log.info("User unlocked: {}", user.getUsername());
    }

    /**
     * Change user password
     */
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword, String confirmPassword) {
        log.info("Changing password for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApplicationExceptions.InvalidCredentialsException("Current password is incorrect");
        }

        // Verify new password confirmation
        if (!newPassword.equals(confirmPassword)) {
            throw new ApplicationExceptions.InvalidRequestException("New password and confirmation do not match");
        }

        // Verify new password is different from current
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApplicationExceptions.InvalidRequestException(
                    "New password must be different from current password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(false); // Clear the flag after successful password change
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", user.getUsername());
    }

    /**
     * Assign roles to user
     */
    @Transactional
    public UserResponseDto assignRoles(String userId, List<String> roleIds) {
        log.info("Assigning roles to user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        Set<Role> roles = new HashSet<>();
        for (String roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Role not found: " + roleId));
            if ("SUPER_ADMIN".equals(role.getName()) && !SecurityContextUtil.isSuperAdminAuthenticated()) {
                throw new ApplicationExceptions.IntactUnauthorizedException(
                        "Insufficient privileges to assign the SUPER_ADMIN role");
            }
            roles.add(role);
        }

        user.setRoles(roles);
        User updatedUser = userRepository.save(user);

        log.info("Roles assigned to user: {}", user.getUsername());

        return toUserResponseDto(updatedUser);
    }

    /**
     * Remove role from user
     */
    @Transactional
    public UserResponseDto removeRole(String userId, String roleId) {
        log.info("Removing role {} from user: {}", roleId, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Role not found: " + roleId));

        user.getRoles().remove(role);
        User updatedUser = userRepository.save(user);

        log.info("Role removed from user: {}", user.getUsername());

        return toUserResponseDto(updatedUser);
    }

    /**
     * Force-logout a user by revoking all their active refresh tokens
     */
    @Transactional
    public void logoutUser(String userId) {
        log.info("Force-logging out user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);

        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
        for (RefreshToken token : tokens) {
            if (!Boolean.TRUE.equals(token.getRevoked())) {
                token.revoke();
                refreshTokenRepository.save(token);
                if (token.getSessionId() != null) {
                    auditService.closeSession(token.getSessionId(), user, null);
                }
            }
        }

        log.info("Force-logout complete for user: {}", user.getUsername());
    }

    /**
     * Delete user
     */
    @Transactional
    public void deleteUser(String userId) {
        log.info("Deleting user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

        enforceUserScope(user);
        userRepository.delete(user);
        log.info("User deleted: {}", user.getUsername());
    }

    /**
     * Convert User to UserResponseDto
     */
    private UserResponseDto toUserResponseDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .status(user.getStatus() != null ? user.getStatus().name() : "UNKNOWN")
                .emailVerified(user.getEmailVerified())
                .mustChangePassword(user.getMustChangePassword())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .build();
    }

    private void enforceUserScope(User user) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(null);
        if (resolvedCompanyId != null) {
            String userCompanyId = user.getCompany() != null ? user.getCompany().getId() : null;
            if (userCompanyId == null || !userCompanyId.equals(resolvedCompanyId)) {
                throw new ApplicationExceptions.IntactUnauthorizedException("Access denied");
            }
        }
    }

    /**
     * Get all available roles
     */
    public List<RoleResponseDto> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(role -> RoleResponseDto.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .description(role.getDescription())
                        .status(role.getStatus() != null ? role.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }
}
