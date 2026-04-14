package com.intact_id.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * @Author frank
 *         email frank.mwangi@dadanadagroup.com
 *         Created on 05/12/2025
 */
@Data
public class WebhookSettingsRequestDto {
    private String webhookUrl;
    private Boolean webhookEnabled;
    private Boolean emailNotifications;
    private Boolean smsNotifications;
    private Map<String, Object> payloadFields;
}
