package com.intact_id.db.model.billing;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.billing.BillingChargeStatus;
import com.intact_id.constant.enums.billing.BillingEvent;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.kyc.VerificationRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "billing_charges", indexes = {
        @Index(name = "idx_billing_charge_company_status", columnList = "company_id,status"),
        @Index(name = "idx_billing_charge_verification", columnList = "verification_request_id", unique = true)
})
@EqualsAndHashCode(callSuper = false, exclude = {"company", "billingProfile", "verificationRequest", "invoice"})
public class BillingCharge extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_profile_id", nullable = false)
    private CompanyBillingProfile billingProfile;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_request_id", nullable = false, unique = true)
    private VerificationRequest verificationRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private BillingInvoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_event", nullable = false, length = 30)
    private BillingEvent billingEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BillingChargeStatus status;

    @Column(name = "billing_tier_code", nullable = false, length = 50)
    private String billingTierCode;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "chargeable_at", nullable = false)
    private LocalDateTime chargeableAt;

    @Column(name = "charged_at")
    private LocalDateTime chargedAt;
}
