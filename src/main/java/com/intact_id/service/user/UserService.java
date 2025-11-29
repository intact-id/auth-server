package com.intact_id.service.user;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.model.user.User;
import com.intact_id.db.repository.user.RoleRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.auth.UserCreateDto;
import com.intact_id.dto.auth.UserResponseDto;
import com.intact_id.util.exception.ApplicationExceptions;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Create new user
     */
    @Transactional
    public UserResponseDto createUser(UserCreateDto request) {
        log.info("Creating user: {}", request.getUsername());

        // Check if username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ApplicationExceptions.DuplicateResourceException(
                    "Username already exists: " + request.getUsername());
        }

        // Check if email exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationExceptions.DuplicateResourceException(
                    "Email already exists: " + request.getEmail());
        }

        // Create user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setStatus(GlobalStatuses.ACTIVE);
        user.setEmailVerified(false);
        user.setMustChangePassword(request.getMustChangePassword());
        user.setPasswordChangedAt(LocalDateTime.now());

        // Assign roles
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            Set<Role> roles = new HashSet<>();
            for (String roleId : request.getRoleIds()) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                                "Role not found: " + roleId));
                roles.add(role);
            }
            user.setRoles(roles);
        }

        User savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        return toUserResponseDto(savedUser);
    }

    /**
     * Get user by ID
     */
    public UserResponseDto getUserById(String userId) {
        log.info("Fetching user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "User not found: " + userId));

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

        return toUserResponseDto(user);
    }

    /**
     * Get all users with filters
     */
    public Page<UserResponseDto> getAllUsers(GlobalStatuses status, String search, Pageable pageable) {
        log.info("Fetching users with status: {}, search: {}", status, search);

        Page<User> users = userRepository.findByFilters(status.name(), search, pageable);

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

        // Update fields
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
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

        user.unlock();
        userRepository.save(user);

        log.info("User unlocked: {}", user.getUsername());
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

        Set<Role> roles = new HashSet<>();
        for (String roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Role not found: " + roleId));
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

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Role not found: " + roleId));

        user.getRoles().remove(role);
        User updatedUser = userRepository.save(user);

        log.info("Role removed from user: {}", user.getUsername());

        return toUserResponseDto(updatedUser);
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
                .status(user.getStatus().name())
                .emailVerified(user.getEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .build();
    }
}
