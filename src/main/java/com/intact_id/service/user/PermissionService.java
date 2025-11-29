package com.intact_id.service.user;

import com.intact_id.db.model.user.Permission;
import com.intact_id.db.repository.user.PermissionRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    /**
     * Create new permission
     */
    @Transactional
    public Permission createPermission(String resource, String action, String description) {
        log.info("Creating permission: {}:{}", resource, action);

        String permissionName = Permission.buildPermissionName(resource, action);

        if (permissionRepository.existsByName(permissionName)) {
            throw new ApplicationExceptions.DuplicateResourceException(
                    "Permission already exists: " + permissionName);
        }

        Permission permission = new Permission();
        permission.setName(permissionName);
        permission.setResource(resource);
        permission.setAction(action);
        permission.setDescription(description);

        Permission savedPermission = permissionRepository.save(permission);
        log.info("Permission created: {}", savedPermission.getName());

        return savedPermission;
    }

    /**
     * Get permission by ID
     */
    public Permission getPermissionById(String permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Permission not found: " + permissionId));
    }

    /**
     * Get all permissions
     */
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    /**
     * Get permissions by resource
     */
    public List<Permission> getPermissionsByResource(String resource) {
        return permissionRepository.findByResource(resource);
    }

    /**
     * Delete permission
     */
    @Transactional
    public void deletePermission(String permissionId) {
        log.info("Deleting permission: {}", permissionId);

        Permission permission = getPermissionById(permissionId);
        permissionRepository.delete(permission);

        log.info("Permission deleted: {}", permission.getName());
    }
}
