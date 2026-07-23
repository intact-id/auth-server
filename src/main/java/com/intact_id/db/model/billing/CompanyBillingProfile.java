package com.intact_id.db.model.billing;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.constant.enums.billing.BillingMode;
import com.intact_id.db.model.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Table(name = "company_billing_profiles", indexes = {
        @Index(name = "idx_company_billing_company", columnList = "company_id", unique = true)
})
@EqualsAndHashCode(callSuper = false, exclude = "company")
public class CompanyBillingProfile extends Auditable {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "billing_tier_code", nullable = false, length = 50)
    private String billingTierCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private BillingMode billingMode = BillingMode.POSTPAID;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "billing_email")
    private String billingEmail;

    @Column(name = "bill_on_submission", nullable = false)
    private Boolean billOnSubmission = false;

    @Column(name = "bill_on_completed", nullable = false)
    private Boolean billOnCompleted = true;

    @Column(name = "bill_on_rejected", nullable = false)
    private Boolean billOnRejected = true;

    @Column(name = "bill_on_failed_non_system", nullable = false)
    private Boolean billOnFailedNonSystem = true;

    @Column(name = "bill_on_failed_system", nullable = false)
    private Boolean billOnFailedSystem = false;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "low_balance_threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal lowBalanceThreshold = BigDecimal.ZERO;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "invoice_due_days", nullable = false)
    private Integer invoiceDueDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private GlobalStatuses status = GlobalStatuses.ACTIVE;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
