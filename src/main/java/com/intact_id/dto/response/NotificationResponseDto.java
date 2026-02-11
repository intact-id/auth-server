package com.intact_id.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private Long id;
    private String companyId;
    private String companyName;
    private String title;
    private String message;
    private String type;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
