package com.intact_id.identity.web;

import java.util.List;

// codes is populated only on generate/regenerate — never retrievable again afterwards.
public record BackupCodesResponseDto(List<String> codes, Integer remainingCount) {
}
