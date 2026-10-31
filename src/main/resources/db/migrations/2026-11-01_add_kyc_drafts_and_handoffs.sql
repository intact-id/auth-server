CREATE TABLE IF NOT EXISTS kyc_drafts
(
    id                 VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    updated_at         TIMESTAMP WITHOUT TIME ZONE,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    company_id         VARCHAR(255) NOT NULL,
    environment        VARCHAR(10)  NOT NULL,
    tier               VARCHAR(20)  NOT NULL,
    external_reference VARCHAR(255),
    details            JSONB,
    status             VARCHAR(20)  NOT NULL,
    verification_id    VARCHAR(255),
    resume_token_hash  VARCHAR(64)  NOT NULL,
    expires_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_kyc_drafts PRIMARY KEY (id),
    CONSTRAINT fk_kyc_drafts_company FOREIGN KEY (company_id) REFERENCES company_tbl (id),
    CONSTRAINT kyc_drafts_status_check CHECK (status IN ('IN_PROGRESS', 'SUBMITTING', 'SUBMITTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_kyc_drafts_resume_token_hash ON kyc_drafts (resume_token_hash);
CREATE INDEX IF NOT EXISTS idx_kyc_drafts_expires_at ON kyc_drafts (expires_at);

CREATE TABLE IF NOT EXISTS kyc_draft_documents
(
    id                VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    draft_id          VARCHAR(255) NOT NULL,
    document_type     VARCHAR(30)  NOT NULL,
    storage_key       VARCHAR(512) NOT NULL,
    mime_type         VARCHAR(100),
    file_size         BIGINT       NOT NULL,
    original_filename VARCHAR(255),
    uploaded_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_kyc_draft_documents PRIMARY KEY (id),
    CONSTRAINT fk_kyc_draft_documents_draft FOREIGN KEY (draft_id) REFERENCES kyc_drafts (id) ON DELETE CASCADE,
    CONSTRAINT uq_kyc_draft_documents_type UNIQUE (draft_id, document_type)
);

CREATE TABLE IF NOT EXISTS kyc_handoffs
(
    id                 VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP WITHOUT TIME ZONE,
    updated_at         TIMESTAMP WITHOUT TIME ZONE,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    draft_id           VARCHAR(255) NOT NULL,
    code_hash          VARCHAR(64)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    expires_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    claimed_at         TIMESTAMP WITHOUT TIME ZONE,
    claimed_user_agent VARCHAR(255),
    CONSTRAINT pk_kyc_handoffs PRIMARY KEY (id),
    CONSTRAINT fk_kyc_handoffs_draft FOREIGN KEY (draft_id) REFERENCES kyc_drafts (id) ON DELETE CASCADE,
    CONSTRAINT uq_kyc_handoffs_code_hash UNIQUE (code_hash),
    CONSTRAINT kyc_handoffs_status_check CHECK (status IN ('PENDING', 'CLAIMED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_kyc_handoffs_draft_id ON kyc_handoffs (draft_id);
