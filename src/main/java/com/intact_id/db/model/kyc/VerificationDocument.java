package com.intact_id.db.model.kyc;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "verification_documents")
@EqualsAndHashCode(callSuper = false)
public class VerificationDocument extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false)
    private VerificationRequest verificationRequest;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType; // selfie, id_front, id_back, proof_of_address

    @Column(name = "file_path", nullable = false)
    private String filePath; // S3/MinIO path

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "original_filename")
    private String originalFilename;
}
