package com.intact_id.accounts.model;

import com.intact_id.accounts.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);
}
