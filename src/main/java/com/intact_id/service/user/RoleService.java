package com.intact_id.service.user;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.user.Permission;
import com.intact_id.db.model.user.Role;
import com.intact_id.db.repository.user.PermissionRepository;
import com.intact_id.db.repository.user.RoleRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Create new role
     */
    @Transactional
    public Role createRole(String name, String description) {
        log.info("Creating role: {}", name);

        if (roleRepository.existsByName(name)) {
            throw new ApplicationExceptions.DuplicateResourceException("Role already exists: " + name);
        }

        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        role.setStatus(GlobalStatuses.ACTIVE);

        Role savedRole = roleRepository.save(role);
        log.info("Role created: {}", savedRole.getName());

        return savedRole;
    }

    /**
     * Get role by ID
     */
    public Role getRoleById(String roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Role not found: " + roleId));
    }

    /**
     * Get role by name
     */
    public Role getRoleByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Role not found: " + name));
    }

    /**
     * Get all roles
     */
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * Update role
     */
    @Transactional
    public Role updateRole(String roleId, String description) {
        log.info("Updating role: {}", roleId);

        Role role = getRoleById(roleId);
        role.setDescription(description);

        Role updatedRole = roleRepository.save(role);
        log.info("Role updated: {}", updatedRole.getName());

        return updatedRole;
    }

    /**
     * Assign permissions to role
     */
    @Transactional
    public Role assignPermissions(String roleId, List<String> permissionIds) {
        log.info("Assigning permissions to role: {}", roleId);

        Role role = getRoleById(roleId);

        Set<Permission> permissions = new HashSet<>();
        for (String permissionId : permissionIds) {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Permission not found: " + permissionId));
            permissions.add(permission);
        }

        role.setPermissions(permissions);
        Role updatedRole = roleRepository.save(role);

        log.info("Permissions assigned to role: {}", role.getName());

        return updatedRole;
    }

    /**
     * Remove permission from role
     */
    @Transactional
    public Role removePermission(String roleId, String permissionId) {
        log.info("Removing permission {} from role: {}", permissionId, roleId);

        Role role = getRoleById(roleId);
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Permission not found: " + permissionId));

        role.removePermission(permission);
        Role updatedRole = roleRepository.save(role);

        log.info("Permission removed from role: {}", role.getName());

        return updatedRole;
    }

    /**
     * Delete role
     */
    @Transactional
    public void deleteRole(String roleId) {
        log.info("Deleting role: {}", roleId);

        Role role = getRoleById(roleId);
        roleRepository.delete(role);

        log.info("Role deleted: {}", role.getName());
    }
}
