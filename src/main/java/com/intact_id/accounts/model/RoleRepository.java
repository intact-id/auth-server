package com.intact_id.accounts.model;

import com.intact_id.accounts.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    /** A company's custom roles. */
    List<Role> findByCompanyIdOrderByLabelAsc(String companyId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsers(@org.springframework.data.repository.query.Param("roleId") String roleId);

    /** Active users of a company who hold the role (e.g. ADMIN), to keep at least one. */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName"
            + " AND u.company.id = :companyId AND u.status = com.intact_id.constant.enums.GlobalStatuses.ACTIVE")
    long countActiveInCompany(@org.springframework.data.repository.query.Param("companyId") String companyId,
                              @org.springframework.data.repository.query.Param("roleName") String roleName);
}
