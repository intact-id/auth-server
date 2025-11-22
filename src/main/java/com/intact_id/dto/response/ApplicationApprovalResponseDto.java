    package com.intact_id.dto.response;

    import com.intact_id.constant.enums.GlobalStatuses;
    import lombok.AllArgsConstructor;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.time.LocalDateTime;
    import java.util.UUID;

    /**
     * @Author frank
     * email frank.mwangi@dadanadagroup.com
     * Created on 22/11/2025 12:10
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class ApplicationApprovalResponseDto {
        private String id;

        private String companyId;
        private String applicationId;

        private String performedBy;
        private GlobalStatuses performedByAction;

        private String approvedBy;
        private GlobalStatuses finalAction;

        private String approverNote;
        private String endUserMessage;

        private LocalDateTime actionPerformedAt;
        private LocalDateTime actionApprovedAt;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
