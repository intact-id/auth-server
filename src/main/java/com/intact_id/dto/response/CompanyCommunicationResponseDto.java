package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * @Author frank
 *         email frank.mwangi@dadanadagroup.com
 *         Created on 05/12/2025
 */
@Data
@Builder
public class CompanyCommunicationResponseDto {
    private String id;
    private String email;
    private String phone;
    private String website;
    private String secondaryEmail;
    private String secondaryPhone;
    private String supportEmail;
    private String billingEmail;
    private String webhookUrl;
    private Boolean webhookEnabled;
    private Boolean emailNotifications;
    private Boolean smsNotifications;
    private Map<String, Object> notificationSettings;
}
