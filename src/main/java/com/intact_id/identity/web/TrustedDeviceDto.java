package com.intact_id.identity.web;

import java.time.LocalDateTime;

public record TrustedDeviceDto(
        String id,
        String deviceLabel,
        String ipAddress,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt) {
}
