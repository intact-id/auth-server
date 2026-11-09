package com.intact_id.workflow.definition;

import com.intact_id.constant.base.Auditable;
import com.intact_id.workflow.WorkflowGraph;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** An immutable published snapshot of a workflow; sessions pin one, so later edits never change a run. */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workflow_version", uniqueConstraints = @UniqueConstraint(name = "uq_workflow_version_number",
        columnNames = {"definition_id", "version_number"}))
public class WorkflowVersionEntity extends Auditable {

    @Column(name = "definition_id", nullable = false)
    private String definitionId;

    @Column(name = "version_number", nullable = false)
    private int number;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph", columnDefinition = "jsonb", nullable = false)
    private WorkflowGraph graph;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "services", columnDefinition = "jsonb", nullable = false)
    private List<String> services;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "published_by")
    private String publishedBy;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;
}
