package com.intact_id.accounts.web;

import com.intact_id.accounts.Permissions;
import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.roles.RoleManagement;
import com.intact_id.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Roles: built-in ones and a company's custom roles, the permissions that can be put in them, and who holds which.
 * Company admins manage their own company; staff who manage users across the platform act for any company.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Roles", description = "Built-in and custom roles, and assigning them to team members.")
public class RoleController {

    private final RoleManagement roles;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('users:view')")
    @Operation(summary = "Roles", description = "Built-in roles and the company's custom roles, with their permissions; "
            + "and the permissions the caller may put in a custom role.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(@RequestParam(required = false) String companyId) {
        boolean staff = SecurityContextUtil.isStaffAuthenticated();
        String company = staff ? (companyId == null || companyId.isBlank() ? null : companyId) : SecurityContextUtil.resolveCompanyId(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roles", roles.list(company, staff && company == null));
        body.put("grantable", roles.grantable());
        return ok(body);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('users:view')")
    @Operation(summary = "Every permission, grouped and labelled")
    public ResponseEntity<ApiResponse<List<Permissions.Info>>> permissions() {
        return ok(Permissions.catalogue());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    @Operation(summary = "Create a custom role", description = "Company permissions the caller holds themselves.")
    public ResponseEntity<ApiResponse<RoleManagement.RoleView>> create(@RequestParam(required = false) String companyId,
                                                                      @RequestBody RoleManagement.RoleChange change) {
        return respond(HttpStatus.CREATED, "Role created", roles.create(SecurityContextUtil.resolveCompanyId(companyId), change));
    }

    @PutMapping("/roles/{name}")
    @PreAuthorize("hasAuthority('roles:manage')")
    @Operation(summary = "Change a custom role")
    public ResponseEntity<ApiResponse<RoleManagement.RoleView>> update(@PathVariable String name, @RequestParam(required = false) String companyId,
                                                                      @RequestBody RoleManagement.RoleChange change) {
        return respond(HttpStatus.OK, "Role updated", roles.update(SecurityContextUtil.resolveCompanyId(companyId), name, change));
    }

    @DeleteMapping("/roles/{name}")
    @PreAuthorize("hasAuthority('roles:manage')")
    @Operation(summary = "Delete a custom role", description = "Only when nobody holds it.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String name, @RequestParam(required = false) String companyId) {
        roles.delete(SecurityContextUtil.resolveCompanyId(companyId), name);
        return respond(HttpStatus.OK, "Role deleted", null);
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('roles:manage')")
    @Operation(summary = "Set a team member's roles", description = "Replaces their roles. A company keeps at least one Admin; "
            + "nobody gives more than they hold.")
    public ResponseEntity<ApiResponse<List<String>>> assign(@PathVariable String userId, @RequestBody AssignRequest request) {
        return respond(HttpStatus.OK, "Roles updated", roles.assign(userId, request.roles()));
    }

    public record AssignRequest(List<String> roles) {
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return respond(HttpStatus.OK, "OK", data);
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .responseCode(status.value()).responseMessage(message).success(true).data(data).build());
    }
}
