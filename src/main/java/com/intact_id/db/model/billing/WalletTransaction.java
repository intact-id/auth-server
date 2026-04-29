package com.intact_id.db.model.billing;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.billing.WalletTransactionType;
import com.intact_id.db.model.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_wallet_transaction_company", columnList = "company_id")
})
@EqualsAndHashCode(callSuper = false, exclude = {"company", "billingProfile"})
public class WalletTransaction extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_profile_id", nullable = false)
    private CompanyBillingProfile billingProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WalletTransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "reference")
    private String reference;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "verification_request_id")
    private String verificationRequestId;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore = BigDecimal.ZERO;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter = BigDecimal.ZERO;
}
