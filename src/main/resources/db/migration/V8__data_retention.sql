-- Data retention and erasure (Kenya Data Protection Act): how long applicants' documents and personal details are
-- kept after a verification finishes, the erasures done, and when each verification's data went.

-- company_id NULL is the platform default; a company row overrides it. NULL days = keep.
CREATE TABLE IF NOT EXISTS retention_settings
(
    id                 VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at         TIMESTAMP(6),
    updated_at         TIMESTAMP(6),
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    company_id         VARCHAR(255) UNIQUE,
    -- Delete uploaded documents, selfies and liveness frames this many days after the verification finished
    documents_days     INTEGER CHECK (documents_days IS NULL OR documents_days >= 0),
    -- Erase personal details (names, ID numbers, contact, OCR results) this many days after it finished
    personal_data_days INTEGER CHECK (personal_data_days IS NULL OR personal_data_days >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_retention_settings_platform ON retention_settings ((company_id IS NULL)) WHERE company_id IS NULL;
INSERT INTO retention_settings (id, created_at, updated_at, company_id)
SELECT gen_random_uuid()::text, now(), now(), NULL
WHERE NOT EXISTS (SELECT 1 FROM retention_settings WHERE company_id IS NULL);

-- Each erasure: what went, why and who did it. Kept after the data itself is gone.
CREATE TABLE IF NOT EXISTS data_erasures
(
    id                VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6),
    updated_at        TIMESTAMP(6),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    verification_id   VARCHAR(255) NOT NULL REFERENCES verification_requests (id),
    company_id        VARCHAR(255) NOT NULL,
    -- DOCUMENTS (files only) or PERSONAL_DATA (files and personal details)
    scope             VARCHAR(20)  NOT NULL CHECK (scope IN ('DOCUMENTS', 'PERSONAL_DATA')),
    -- MANUAL (someone asked) or RETENTION (the company's retention rule)
    trigger_type      VARCHAR(20)  NOT NULL CHECK (trigger_type IN ('MANUAL', 'RETENTION')),
    reason            VARCHAR(1000),
    performed_by      VARCHAR(255) NOT NULL,
    documents_deleted INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_data_erasures_company ON data_erasures (company_id, created_at DESC);

ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS documents_deleted_at TIMESTAMP(6);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS personal_data_erased_at TIMESTAMP(6);
CREATE INDEX IF NOT EXISTS idx_verification_requests_retention ON verification_requests (company_id, completed_at)
    WHERE personal_data_erased_at IS NULL;
