package com.intact_id.db.model;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 11:50
 */

@Data
@Entity
@Table(name = "company_application_approvals")
@EqualsAndHashCode(callSuper = false)
public class CompanyApplicationApproval extends Auditable {

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "performed_by_action", nullable = false)
    private GlobalStatuses performedByAction;

    @Column(name = "approved_by")
    private String approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_action")
    private GlobalStatuses finalAction;

    @Column(name = "approver_note", columnDefinition = "TEXT")
    private String approverNote;

    @Column(name = "end_user_message", columnDefinition = "TEXT")
    private String endUserMessage;

    @Column(name = "action_performed_at")
    private LocalDateTime actionPerformedAt;

    @Column(name = "action_approved_at")
    private LocalDateTime actionApprovedAt;
}
