package com.intact_id.workflow.catalog;

import com.intact_id.constant.base.Auditable;
import com.intact_id.workflow.CatalogService;
import com.intact_id.workflow.ServiceCategory;
import com.intact_id.workflow.ServiceStatus;
import com.intact_id.workflow.SubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workflow_service")
public class CatalogServiceEntity extends Auditable {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ServiceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ServiceStatus status;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subject_types", columnDefinition = "jsonb", nullable = false)
    private List<SubjectType> subjectTypes = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "countries", columnDefinition = "jsonb", nullable = false)
    private List<String> countries = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requires", columnDefinition = "jsonb", nullable = false)
    private List<String> requires = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_fields", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredFields = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "captures", columnDefinition = "jsonb", nullable = false)
    private List<String> captures = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> outputs = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> ui = new HashMap<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_schema", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> settingsSchema = new HashMap<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public CatalogService toView() {
        return new CatalogService(code, name, description, category, status,
                List.copyOf(subjectTypes), List.copyOf(countries), List.copyOf(requires),
                List.copyOf(requiredFields), List.copyOf(captures), List.copyOf(outputs),
                Map.copyOf(ui), Map.copyOf(settingsSchema), sortOrder);
    }
}
