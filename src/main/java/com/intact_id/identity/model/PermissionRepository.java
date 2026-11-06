package com.intact_id.identity.model;

import com.intact_id.identity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    Optional<Permission> findByName(String name);

    List<Permission> findByResource(String resource);

    Optional<Permission> findByResourceAndAction(String resource, String action);

    boolean existsByName(String name);
}
