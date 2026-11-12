package com.intact_id.checks.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A company's tenant in sactions-service, created the first time the company screens someone. */
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "screening_tenant")
public class ScreeningTenant {

    @Id
    @Column(name = "company_id", nullable = false, updatable = false)
    private String companyId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
