package com.intact_id.identity.web;


import java.util.Map;

public record Enable2FaResponseDto(
        Boolean is2FaEnabled,
        String qrCodeEncodeUrl,
        Map<String, String> extraParams) {
}
