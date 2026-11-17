package com.intact_id.orchestration.companydocs;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One uploaded version of a company's compliance document. A new upload of the same type is the next version. */
@Entity
@Getter
@Setter
@Table(name = "company_documents")
@EqualsAndHashCode(callSuper = false)
public class CompanyDocument extends Auditable {

    public enum Status { PENDING_REVIEW, APPROVED, REJECTED }

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private CompanyDocumentType type;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING_REVIEW;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
