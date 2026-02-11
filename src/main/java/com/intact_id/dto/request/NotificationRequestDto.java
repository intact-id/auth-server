package com.intact_id.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationRequestDto {

    // Optional for company-scoped JWT users; admins can provide to target a company
    private String companyId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    @NotNull(message = "Notification type is required")
    private String type; // INFO, SUCCESS, WARNING, ERROR
}
