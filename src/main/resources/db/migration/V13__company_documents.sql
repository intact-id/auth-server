-- Compliance documents a company keeps with Intact ID (certificate of incorporation, CR12, AML policy, ...).
-- Each upload is a version; Intact staff approve or reject it.
CREATE TABLE IF NOT EXISTS company_documents (
    id           VARCHAR(255) PRIMARY KEY,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    company_id   VARCHAR(255) NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    version      INTEGER      NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(512) NOT NULL,
    status       VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    expires_on   DATE,
    uploaded_by  VARCHAR(255) NOT NULL,
    uploaded_at  TIMESTAMP    NOT NULL,
    review_note  TEXT,
    reviewed_by  VARCHAR(255),
    reviewed_at  TIMESTAMP,
    CONSTRAINT uq_company_documents_version UNIQUE (company_id, type, version)
);
CREATE INDEX IF NOT EXISTS idx_company_documents_company ON company_documents (company_id);

-- The new permissions, so custom roles can hold the company ones
INSERT INTO permissions (id, created_at, updated_at, name, resource, action, description)
SELECT gen_random_uuid()::text, now(), now(), v.name, split_part(v.name, ':', 1), split_part(v.name, ':', 2), v.description
FROM (VALUES
    ('documents:view', 'See the company''s compliance documents'),
    ('documents:manage', 'Upload compliance documents'),
    ('documents:review-platform', 'Approve and reject companies'' compliance documents')
) AS v(name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = v.name);
