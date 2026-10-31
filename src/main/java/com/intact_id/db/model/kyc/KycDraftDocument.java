package com.intact_id.db.model.kyc;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "kyc_draft_documents")
@EqualsAndHashCode(callSuper = false)
public class KycDraftDocument extends Auditable {

    @Column(name = "draft_id", nullable = false)
    private String draftId;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
