package com.intact_id.workflow.definition;

import com.intact_id.constant.base.Auditable;
import com.intact_id.workflow.SubjectType;
import com.intact_id.workflow.WorkflowGraph;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A merchant's workflow (company_id empty = an Intact template merchants copy). */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workflow_definition", uniqueConstraints = @UniqueConstraint(name = "uq_workflow_definition_company_key",
        columnNames = {"company_id", "workflow_key"}))
public class WorkflowDefinitionEntity extends Auditable {

    @Column(name = "company_id")
    private String companyId;

    // Stable name integrators send when starting a session
    @Column(name = "workflow_key", nullable = false, length = 100)
    private String key;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private SubjectType subjectType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_graph", columnDefinition = "jsonb", nullable = false)
    private WorkflowGraph draftGraph;

    @Column(name = "draft_updated_at")
    private LocalDateTime draftUpdatedAt;

    // 0 = never published
    @Column(name = "latest_version", nullable = false)
    private int latestVersion;

    // The version sessions and API calls run: the latest published, unless an older one was put back
    @Column(name = "live_version")
    private Integer liveVersion;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    // Runs when a session or API call names no workflow; at most one per company
    @Column(name = "is_default", nullable = false)
    private boolean defaultWorkflow;

    public boolean isTemplate() {
        return companyId == null;
    }
}
