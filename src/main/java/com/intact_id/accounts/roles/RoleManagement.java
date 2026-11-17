package com.intact_id.accounts.roles;

import com.intact_id.accounts.Permissions;
import com.intact_id.accounts.Permission;
import com.intact_id.accounts.Role;
import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.User;
import com.intact_id.accounts.UserRepository;
import com.intact_id.accounts.model.PermissionRepository;
import com.intact_id.accounts.model.RoleRepository;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Roles: the built-in ones (Permissions.BUILT_IN_ROLES) and custom roles a company makes for itself, and who holds
 * which. The rules: company roles stay within their company; custom roles hold company permissions only; nobody gives
 * a role with more than they hold themselves; a company always keeps an active Admin; only staff who manage users across
 * the platform give staff roles, and only super admins give Super admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagement {

    private static final String ADMIN = "ADMIN";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRepository users;

    /** A role as the console shows it. */
    public record RoleView(String name, String label, String description, boolean builtIn, boolean staff, String companyId,
                           List<String> permissions, long users) {
    }

    public record RoleChange(String label, String description, List<String> permissions) {
    }

    /** Roles a company can use (its built-in ones and its own), or, for staff without a company, also the staff roles. */
    public List<RoleView> list(String companyId, boolean includeStaffRoles) {
        List<RoleView> views = new ArrayList<>();
        for (Permissions.BuiltInRole b : Permissions.BUILT_IN_ROLES) {
            if (b.staff() && !includeStaffRoles) continue;
            Role row = roles.findByName(b.name()).orElse(null);
            views.add(new RoleView(b.name(), b.label(), b.description(), true, b.staff(), null,
                    Permissions.sorted(b.permissions()), row == null ? 0 : roles.countUsers(row.getId())));
        }
        if (companyId != null) {
            roles.findByCompanyIdOrderByLabelAsc(companyId).forEach(r -> views.add(view(r)));
        }
        return views;
    }

    /** The permissions the caller could put in a custom role: company permissions they hold themselves. */
    public List<Permissions.Info> grantable() {
        return Permissions.catalogue().stream()
                .filter(i -> i.scope() == Permissions.Scope.COMPANY)
                .filter(i -> SecurityContextUtil.hasPermission(i.name()))
                .toList();
    }

    @Transactional
    public RoleView create(String companyId, RoleChange change) {
        Role role = new Role();
        role.setName("CUSTOM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        role.setCompanyId(companyId);
        role.setStatus(GlobalStatuses.ACTIVE);
        apply(role, change);
        log.info("Custom role {} ({}) created for company {}", role.getLabel(), role.getName(), companyId);
        return view(roles.save(role));
    }

    @Transactional
    public RoleView update(String companyId, String name, RoleChange change) {
        Role role = custom(companyId, name);
        apply(role, change);
        return view(roles.save(role));
    }

    @Transactional
    public void delete(String companyId, String name) {
        Role role = custom(companyId, name);
        long holders = roles.countUsers(role.getId());
        if (holders > 0) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    holders + " team member" + (holders == 1 ? " holds" : "s hold") + " this role; give them another role first");
        }
        roles.delete(role);
        log.info("Custom role {} deleted from company {}", name, companyId);
    }

    /** Replaces a person's roles. */
    @Transactional
    public List<String> assign(String userId, Collection<String> roleNames) {
        User target = users.findById(userId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("User not found"));
        boolean callerIsStaff = SecurityContextUtil.isStaffAuthenticated();
        String targetCompany = target.getCompany() == null ? null : target.getCompany().getId();
        if (!callerIsStaff) {
            String callerCompany = SecurityContextUtil.resolveCompanyId(null);
            if (!Objects.equals(callerCompany, targetCompany)) {
                throw new ApplicationExceptions.IntactResourceNotFoundException("User not found");
            }
        } else if (!SecurityContextUtil.hasPermission("users:manage-platform")) {
            throw new ApplicationExceptions.IntactForbiddenException("You can't change other companies' or staff roles");
        }
        if (roleNames == null || roleNames.isEmpty()) {
            throw new ApplicationExceptions.IntactBadRequestException("Give at least one role");
        }

        Set<Role> chosen = new LinkedHashSet<>();
        for (String name : new LinkedHashSet<>(roleNames)) {
            chosen.add(roles.findByName(name)
                    .orElseThrow(() -> new ApplicationExceptions.IntactBadRequestException("Unknown role: " + name)));
        }
        checkCanGrant(chosen, targetCompany);
        // The company keeps at least one active Admin
        boolean wasAdmin = target.getRoles().stream().anyMatch(r -> ADMIN.equals(r.getName()));
        boolean staysAdmin = chosen.stream().anyMatch(r -> ADMIN.equals(r.getName()));
        if (targetCompany != null && wasAdmin && !staysAdmin && target.getStatus() == GlobalStatuses.ACTIVE
                && roles.countActiveInCompany(targetCompany, ADMIN) <= 1) {
            throw new ApplicationExceptions.IntactBadRequestException("The company needs at least one Admin; make someone else Admin first");
        }
        target.setRoles(new HashSet<>(chosen));
        users.save(target);
        List<String> names = chosen.stream().map(Role::getName).toList();
        log.info("Roles of user {} set to {} by {}", target.getUsername(), names, SecurityContextUtil.getAuthenticatedUser().getUsername());
        return names;
    }

    /**
     * Whether the signed-in person may give these roles to someone in targetCompany (null: Intact staff). Used wherever
     * roles are given: here, and when users are created or changed.
     */
    public void checkCanGrant(Collection<Role> chosen, String targetCompany) {
        for (Role role : chosen) {
            boolean staffRole = Permissions.STAFF_ROLES.contains(role.getName());
            if (targetCompany == null && !staffRole) {
                throw new ApplicationExceptions.IntactBadRequestException("Intact staff can only have staff roles");
            }
            if (targetCompany != null && staffRole) {
                throw new ApplicationExceptions.IntactBadRequestException("Company users can't have Intact staff roles");
            }
            if (role.getCompanyId() != null && !role.getCompanyId().equals(targetCompany)) {
                throw new ApplicationExceptions.IntactBadRequestException("Unknown role: " + role.getName());
            }
            if (!Permissions.isBuiltIn(role.getName()) && role.getCompanyId() == null) {
                throw new ApplicationExceptions.IntactBadRequestException("Role " + role.getName() + " can't be given");
            }
            if (SUPER_ADMIN.equals(role.getName()) && !SecurityContextUtil.isSuperAdminAuthenticated()) {
                throw new ApplicationExceptions.IntactForbiddenException("Only super admins can make someone a super admin");
            }
            if (staffRole && !SecurityContextUtil.hasPermission("users:manage-platform")) {
                throw new ApplicationExceptions.IntactForbiddenException("Only staff who manage users can give staff roles");
            }
        }
        // Nobody gives more than they hold
        Set<String> missing = new HashSet<>(Permissions.forRoles(chosen));
        missing.removeIf(SecurityContextUtil::hasPermission);
        if (!missing.isEmpty()) {
            throw new ApplicationExceptions.IntactForbiddenException("Those roles allow things you can't do yourself: "
                    + String.join(", ", Permissions.sorted(missing)));
        }
    }

    private void apply(Role role, RoleChange change) {
        if (change.label() == null || change.label().isBlank()) {
            throw new ApplicationExceptions.IntactBadRequestException("Give the role a name");
        }
        Set<String> wanted = new LinkedHashSet<>(change.permissions() == null ? List.of() : change.permissions());
        if (wanted.isEmpty()) throw new ApplicationExceptions.IntactBadRequestException("Choose at least one permission");
        for (String p : wanted) {
            if (!Permissions.COMPANY_GRANTABLE.contains(p)) {
                throw new ApplicationExceptions.IntactBadRequestException("A company role can't have " + p);
            }
            if (!SecurityContextUtil.hasPermission(p)) {
                throw new ApplicationExceptions.IntactForbiddenException("You can't give a permission you don't have: " + p);
            }
        }
        Set<Permission> rows = new HashSet<>();
        for (String p : wanted) {
            rows.add(permissions.findByName(p)
                    .orElseThrow(() -> new ApplicationExceptions.IntactInternalServerException("Permission missing: " + p)));
        }
        role.setLabel(change.label().trim());
        role.setDescription(change.description() == null || change.description().isBlank() ? null : change.description().trim());
        role.setPermissions(rows);
    }

    private Role custom(String companyId, String name) {
        return roles.findByName(name)
                .filter(r -> r.getCompanyId() != null && r.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Role not found (built-in roles can't be changed)"));
    }

    private RoleView view(Role r) {
        return new RoleView(r.getName(), r.getLabel(), r.getDescription(), false, false, r.getCompanyId(),
                Permissions.sorted(Permissions.of(r)), roles.countUsers(r.getId()));
    }
}
