package com.intact_id.accounts.web;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserResponseDto {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String status;
    private Boolean emailVerified;
    private Boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private List<String> roles;
    // What this person may do (Permissions), so the console shows only what they can use
    private List<String> permissions;
    private String companyId;
    private Boolean is2faEnabled;
    // The two-factor secret: used inside the server only, never sent in a response
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String googleAuthenticationKey;
}
