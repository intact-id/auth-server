package com.intact_id.accounts.config;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.Permission;
import com.intact_id.accounts.Role;
import com.intact_id.accounts.User;
import com.intact_id.accounts.model.PermissionRepository;
import com.intact_id.accounts.model.RoleRepository;
import com.intact_id.accounts.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        log.info("Initializing default data...");

        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT column_name, data_type FROM information_schema.columns " +
                            "WHERE table_name = 'users' AND column_name IN ('username', 'email', 'first_name', 'last_name', 'password_hash')");
            log.info("CHECK_COLUMN_TYPES: User table columns:");
            for (Map<String, Object> col : columns) {
                log.info("  - {} : {}", col.get("column_name"), col.get("data_type"));
            }
        } catch (Exception e) {
            log.error("CHECK_COLUMN_TYPE: Failed to check column types", e);
        }

        createPermissions();
        createRoles();
        createSuperAdmin();

        log.info("Data initialization complete");
    }

    private void createPermissions() {
        log.info("Creating default permissions...");

        List<PermissionData> permissions = Arrays.asList(
                // Company permissions
                new PermissionData("company", "create", "Create new company"),
                new PermissionData("company", "read", "View company details"),
                new PermissionData("company", "update", "Update company information"),
                new PermissionData("company", "delete", "Delete company"),

                // Application permissions
                new PermissionData("application", "create", "Create application"),
                new PermissionData("application", "read", "View applications"),
                new PermissionData("application", "update", "Update application"),
                new PermissionData("application", "approve", "Approve application"),
                new PermissionData("application", "reject", "Reject application"),

                // User permissions
                new PermissionData("user", "create", "Create user"),
                new PermissionData("user", "read", "View users"),
                new PermissionData("user", "update", "Update user"),
                new PermissionData("user", "delete", "Delete user"),

                // Role permissions
                new PermissionData("role", "create", "Create role"),
                new PermissionData("role", "read", "View roles"),
                new PermissionData("role", "update", "Update role"),
                new PermissionData("role", "delete", "Delete role"),

                // Permission permissions
                new PermissionData("permission", "create", "Create permission"),
                new PermissionData("permission", "read", "View permissions"),
                new PermissionData("permission", "delete", "Delete permission"),

                // KYC permissions
                new PermissionData("kyc", "verify", "Perform KYC verification"),
                new PermissionData("kyc", "read", "View KYC verifications"),

                // API Key permissions
                new PermissionData("apikey", "create", "Generate API keys"),
                new PermissionData("apikey", "read", "View API keys"),
                new PermissionData("apikey", "revoke", "Revoke API keys"));

        for (PermissionData permData : permissions) {
            String permissionName = Permission.buildPermissionName(permData.resource, permData.action);

            if (!permissionRepository.existsByName(permissionName)) {
                Permission permission = new Permission();
                permission.setName(permissionName);
                permission.setResource(permData.resource);
                permission.setAction(permData.action);
                permission.setDescription(permData.description);

                permissionRepository.save(permission);
                log.info("Created permission: {}", permissionName);
            }
        }
    }

    private void createRoles() {
        log.info("Creating default roles...");

        // SUPER_ADMIN - Full access
        createRoleWithAllPermissions("SUPER_ADMIN", "Super Administrator with full system access",
                permissionRepository.findAll());

        // ADMIN - Manage companies and applications
        createRoleWithPermissions("ADMIN", "Administrator for company and application management",
                Arrays.asList(
                        "company:create", "company:read", "company:update",
                        "application:read", "application:update",
                        "apikey:create", "apikey:read", "apikey:revoke",
                        "kyc:read"));

        // CHECKER - Final approval authority
        createRoleWithPermissions("CHECKER", "Final approval authority for applications",
                Arrays.asList(
                        "application:read", "application:approve", "application:reject",
                        "company:read"));

        // MAKER - Initial review authority
        createRoleWithPermissions("MAKER", "Initial review authority for applications",
                Arrays.asList(
                        "application:read", "application:update",
                        "company:read"));

        // VIEWER - Read-only access
        createRoleWithPermissions("VIEWER", "Read-only access to system",
                Arrays.asList(
                        "company:read", "application:read", "kyc:read"));
    }

    private void createRoleWithPermissions(String roleName, String description, List<String> permissionNames) {
        if (roleRepository.existsByName(roleName)) {
            log.info("Role already exists: {}", roleName);
            return;
        }

        Role role = new Role();
        role.setName(roleName);
        role.setDescription(description);
        role.setStatus(GlobalStatuses.ACTIVE);

        Set<Permission> permissions = new HashSet<>();
        for (String permName : permissionNames) {
            permissionRepository.findByName(permName).ifPresent(permissions::add);
        }

        role.setPermissions(permissions);
        roleRepository.save(role);

        log.info("Created role: {} with {} permissions", roleName, permissions.size());
    }

    private void createRoleWithAllPermissions(String roleName, String description, List<Permission> permissions) {
        if (roleRepository.existsByName(roleName)) {
            log.info("Role already exists: {}", roleName);
            return;
        }

        Role role = new Role();
        role.setName(roleName);
        role.setDescription(description);
        role.setStatus(GlobalStatuses.ACTIVE);
        role.setPermissions(new HashSet<>(permissions));

        roleRepository.save(role);

        log.info("Created role: {} with {} permissions", roleName, permissions.size());
    }

    /**
     * Creates the super admin once. Never resets an existing admin's password: that made the default
     * password valid again after every restart.
     */
    private void createSuperAdmin() {
        String username = "admin";
        if (userRepository.findByUsername(username).isPresent()) {
            log.info("Super admin already exists; leaving it unchanged");
            return;
        }

        String initialPassword = System.getenv("SUPER_ADMIN_INITIAL_PASSWORD");
        boolean generated = initialPassword == null || initialPassword.isBlank();
        if (generated) {
            initialPassword = UUID.randomUUID().toString();
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail("admin@intact-id.com");
        admin.setPasswordHash(passwordEncoder.encode(initialPassword));
        admin.setFirstName("Super");
        admin.setLastName("Admin");
        admin.setStatus(GlobalStatuses.ACTIVE);
        admin.setEmailVerified(true);
        admin.setMustChangePassword(true);
        admin.setPasswordChangedAt(LocalDateTime.now());
        roleRepository.findByName("SUPER_ADMIN").ifPresent(role -> {
            Set<Role> roles = new HashSet<>();
            roles.add(role);
            admin.setRoles(roles);
        });
        userRepository.save(admin);

        if (generated) {
            log.warn("Super admin created with a one-time password (set SUPER_ADMIN_INITIAL_PASSWORD to choose it): {}",
                    initialPassword);
        } else {
            log.info("Super admin created from SUPER_ADMIN_INITIAL_PASSWORD");
        }
    }

    private static class PermissionData {
        String resource;
        String action;
        String description;

        PermissionData(String resource, String action, String description) {
            this.resource = resource;
            this.action = action;
            this.description = description;
        }
    }
}
