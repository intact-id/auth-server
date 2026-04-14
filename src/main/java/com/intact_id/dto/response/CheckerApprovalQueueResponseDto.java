package com.intact_id.dto.response;

import com.intact_id.constant.enums.GlobalStatuses;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckerApprovalQueueResponseDto {
    private String approvalId;
    private String applicationId;
    private String applicationNumber;
    private String legalName;
    private String registrationNumber;
    private String email;
    private String businessType;
    private GlobalStatuses applicationStatus;
    private String makerId;
    private String makerUsername;
    private String makerEmail;
    private GlobalStatuses makerAction;
    private String makerNote;
    private LocalDateTime submittedForApprovalAt;
}
