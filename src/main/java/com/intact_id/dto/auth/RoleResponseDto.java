package com.intact_id.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleResponseDto {
    private String id;
    private String name;
    private String description;
    private String status;
}
