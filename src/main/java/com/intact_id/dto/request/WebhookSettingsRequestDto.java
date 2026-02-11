package com.intact_id.dto.request;

import lombok.Data;

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
}
