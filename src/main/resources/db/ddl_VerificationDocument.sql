CREATE TABLE verification_documents
(
    id                VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    verification_id   VARCHAR(255) NOT NULL,
    document_type     VARCHAR(50)  NOT NULL,
    file_path         VARCHAR(255) NOT NULL,
    file_size         BIGINT,
    mime_type         VARCHAR(100),
    original_filename VARCHAR(255),
    CONSTRAINT pk_verification_documents PRIMARY KEY (id)
);

ALTER TABLE verification_documents
    ADD CONSTRAINT FK_VERIFICATION_DOCUMENTS_ON_VERIFICATION FOREIGN KEY (verification_id) REFERENCES verification_requests (id);