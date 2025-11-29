package com.intact_id.db.model.user;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "roles", indexes = {
        @Index(name = "idx_role_name", columnList = "name")
})
@EqualsAndHashCode(callSuper = false, exclude = { "permissions", "users" })
public class Role extends Auditable {

    @Column(name = "name", unique = true, nullable = false, length = 50)
    private String name; // SUPER_ADMIN, ADMIN, CHECKER, MAKER, VIEWER

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatuses status = GlobalStatuses.ACTIVE;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    // Helper methods
    public boolean hasPermission(String permissionName) {
        return permissions.stream()
                .anyMatch(p -> p.getName().equals(permissionName));
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }
}
