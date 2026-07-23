package com.intact_id.dto.auth;

import java.time.LocalDateTime;

public record TrustedDeviceDto(
        String id,
        String deviceLabel,
        String ipAddress,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt) {
}
