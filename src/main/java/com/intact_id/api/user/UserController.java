package com.intact_id.api.user;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.dto.auth.UserCreateDto;
import com.intact_id.dto.auth.UserResponseDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.service.user.UserService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Admin endpoints for user management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Create new user")
    public ResponseEntity<ApiResponse<UserResponseDto>> createUser(
            @Valid @RequestBody UserCreateDto request) {
        try {
            UserResponseDto user = userService.createUser(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<UserResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("User created successfully")
                            .data(user)
                            .build());
        } catch (ApplicationExceptions.DuplicateResourceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiResponse.<UserResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.CONFLICT.value())
                            .responseMessage("User already exists")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        }
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponseDto>> getUserById(@PathVariable String userId) {
        try {
            UserResponseDto user = userService.getUserById(userId);

            return ResponseEntity.ok(
                    ApiResponse.<UserResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("User retrieved successfully")
                            .data(user)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<UserResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("User not found")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Get all users with filters")
    public ResponseEntity<ApiResponse<Page<UserResponseDto>>> getAllUsers(
            @RequestParam(required = false) GlobalStatuses status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<UserResponseDto> users = userService.getAllUsers(status, search, pageable);

        return ResponseEntity.ok(
                ApiResponse.<Page<UserResponseDto>>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Users retrieved successfully")
                        .data(users)
                        .build());
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Update user")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserCreateDto request) {
        try {
            UserResponseDto user = userService.updateUser(userId, request);

            return ResponseEntity.ok(
                    ApiResponse.<UserResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("User updated successfully")
                            .data(user)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<UserResponseDto>builder()
                            .success(false)
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("User not found")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        }
    }

    @PostMapping("/{userId}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Suspend user")
    public ResponseEntity<ApiResponse<Void>> suspendUser(@PathVariable String userId) {
        userService.suspendUser(userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("User suspended successfully")
                        .build());
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activate user")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable String userId) {
        userService.activateUser(userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("User activated successfully")
                        .build());
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    @Operation(summary = "Unlock user account")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable String userId) {
        userService.unlockUser(userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("User unlocked successfully")
                        .build());
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Assign roles to user")
    public ResponseEntity<ApiResponse<UserResponseDto>> assignRoles(
            @PathVariable String userId,
            @RequestBody List<String> roleIds) {
        UserResponseDto user = userService.assignRoles(userId, roleIds);

        return ResponseEntity.ok(
                ApiResponse.<UserResponseDto>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Roles assigned successfully")
                        .data(user)
                        .build());
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Remove role from user")
    public ResponseEntity<ApiResponse<UserResponseDto>> removeRole(
            @PathVariable String userId,
            @PathVariable String roleId) {
        UserResponseDto user = userService.removeRole(userId, roleId);

        return ResponseEntity.ok(
                ApiResponse.<UserResponseDto>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Role removed successfully")
                        .data(user)
                        .build());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete user")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("User deleted successfully")
                        .build());
    }
}
