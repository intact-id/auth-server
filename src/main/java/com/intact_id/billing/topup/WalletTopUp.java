package com.intact_id.billing.topup;

import com.intact_id.constant.base.Auditable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One wallet top-up: a bank transfer with its receipt, or (later) a partner payment. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "wallet_top_ups")
public class WalletTopUp extends Auditable {

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private TopUpMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TopUpStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "credited_amount", precision = 19, scale = 4)
    private BigDecimal creditedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @JsonIgnore
    @Column(name = "receipt_storage_key", length = 512)
    private String receiptStorageKey;

    @Column(name = "receipt_filename")
    private String receiptFilename;

    @Column(name = "receipt_content_type", length = 100)
    private String receiptContentType;

    @Column(name = "receipt_size")
    private Long receiptSize;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "credited_at")
    private LocalDateTime creditedAt;

    public boolean hasReceipt() {
        return receiptStorageKey != null;
    }
}
