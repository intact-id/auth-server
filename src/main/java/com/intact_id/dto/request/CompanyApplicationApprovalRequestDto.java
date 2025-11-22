package com.intact_id.dto.request;
import com.intact_id.constant.enums.GlobalStatuses;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 12:12
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyApplicationApprovalRequestDto {
    private String applicationId;

    private String performedBy;
    private GlobalStatuses performedByAction;

    private String approverNote;
    private String endUserMessage;
}
