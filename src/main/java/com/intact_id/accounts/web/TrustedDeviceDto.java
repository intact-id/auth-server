package com.intact_id.accounts.web;

import java.time.LocalDateTime;

public record TrustedDeviceDto(
        String id,
        String deviceLabel,
        String ipAddress,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt) {
}
