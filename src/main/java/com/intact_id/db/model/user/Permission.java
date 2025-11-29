package com.intact_id.db.model.user;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "permissions", indexes = {
        @Index(name = "idx_permission_name", columnList = "name"),
        @Index(name = "idx_resource_action", columnList = "resource, action")
})
@EqualsAndHashCode(callSuper = false, exclude = "roles")
public class Permission extends Auditable {

    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name; // company:create, application:approve, etc.

    @Column(name = "resource", nullable = false, length = 50)
    private String resource; // company, application, user, role

    @Column(name = "action", nullable = false, length = 50)
    private String action; // create, read, update, delete, approve

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles = new HashSet<>();

    // Helper method
    public static String buildPermissionName(String resource, String action) {
        return resource + ":" + action;
    }
}
