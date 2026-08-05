package com.intact_id.dto.auth;

import java.util.List;

// codes is populated only on generate/regenerate — never retrievable again afterwards.
public record BackupCodesResponseDto(List<String> codes, Integer remainingCount) {
}
