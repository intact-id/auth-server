-- Business verification (KYB): directors and owners, owner-verification settings, the business registry
-- module, the owner-verification service and two starter templates.

-- The exact status a check reported (passed, failed, review, skipped). A business verification that waits
-- for its owners resumes from its saved checks instead of running them again.
ALTER TABLE verification_steps ADD COLUMN IF NOT EXISTS check_status VARCHAR(20);

-- Who must be verified behind a business, and how. company_id NULL is the platform default;
-- a company row overrides it.
CREATE TABLE IF NOT EXISTS kyb_settings
(
    id                  VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at          TIMESTAMP(6),
    updated_at          TIMESTAMP(6),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    company_id          VARCHAR(255) UNIQUE,
    -- NONE: nobody; SCREENING: sanctions/PEP only; FULL_KYC: each person completes identity verification
    owner_check         VARCHAR(20)   NOT NULL CHECK (owner_check IN ('NONE', 'SCREENING', 'FULL_KYC')),
    -- Owners at or above this share are checked
    ownership_threshold NUMERIC(5, 2) NOT NULL DEFAULT 25,
    include_directors   BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Workflow key each person's identity verification runs (FULL_KYC); null = the company's default, else tier1
    person_workflow     VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_kyb_settings_platform ON kyb_settings ((company_id IS NULL)) WHERE company_id IS NULL;

INSERT INTO kyb_settings (id, created_at, updated_at, company_id, owner_check, ownership_threshold, include_directors)
SELECT gen_random_uuid()::text, now(), now(), NULL, 'FULL_KYC', 25, FALSE
WHERE NOT EXISTS (SELECT 1 FROM kyb_settings WHERE company_id IS NULL);

-- Directors and owners named on a business verification
CREATE TABLE IF NOT EXISTS verification_parties
(
    id                        VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at                TIMESTAMP(6),
    updated_at                TIMESTAMP(6),
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),
    verification_id           VARCHAR(255) NOT NULL REFERENCES verification_requests (id),
    first_name                VARCHAR(255) NOT NULL,
    last_name                 VARCHAR(255) NOT NULL,
    date_of_birth             DATE,
    nationality               VARCHAR(3),
    email                     VARCHAR(255),
    phone                     VARCHAR(50),
    is_director               BOOLEAN      NOT NULL DEFAULT FALSE,
    ownership_percent         NUMERIC(5, 2),
    -- Whether the settings at submission required this person to be checked
    must_verify               BOOLEAN      NOT NULL DEFAULT FALSE,
    -- PENDING, INVITED, VERIFYING, PASSED, FAILED, REVIEW, NOT_REQUIRED
    status                    VARCHAR(20)  NOT NULL,
    -- Their own identity verification (FULL_KYC)
    person_verification_id    VARCHAR(255),
    screening                 JSONB,
    invite_token_hash         VARCHAR(255) UNIQUE,
    invite_expires_at         TIMESTAMP(6)
);
CREATE INDEX IF NOT EXISTS idx_verification_parties_verification ON verification_parties (verification_id);
CREATE INDEX IF NOT EXISTS idx_verification_parties_person ON verification_parties (person_verification_id);

-- Business registry: a module whose provider is coming; until then it reports the business as registered
UPDATE workflow_service
SET status          = 'AVAILABLE',
    description     = 'Confirm the business against the company registry (e.g. CR12 in Kenya). The registry provider is being connected; until then every business is reported as registered and active.',
    required_fields = '["companyName", "registrationNumber", "country"]',
    outputs         = '[{"key": "business.registered", "type": "boolean"}, {"key": "business.status", "type": "string"}]',
    updated_at      = now()
WHERE code = 'BUSINESS_REGISTRY';

-- What screening and risk assessment need when the subject is a business
UPDATE workflow_service
SET ui = ui || '{"businessRequiredFields": ["companyName", "country"]}', updated_at = now()
WHERE code IN ('SANCTIONS_SCREENING', 'CRA');

INSERT INTO workflow_service (id, created_at, updated_at, code, name, description, category, status, subject_types,
                              countries, requires, required_fields, captures, outputs, ui, settings_schema, sort_order)
VALUES (gen_random_uuid()::text, now(), now(), 'OWNER_VERIFICATION', 'Directors and owners',
        'Check the people behind the business: owners above the ownership threshold, and directors if configured. '
            || 'Depending on the settings they are screened, or each completes identity verification.',
        'VERIFICATION', 'AVAILABLE', '["BUSINESS"]', '[]', '[]', '[]', '[]',
        '[{"key": "owners.required", "type": "number"}, {"key": "owners.passed", "type": "number"}, {"key": "owners.failed", "type": "number"}, {"key": "owners.review", "type": "number"}]',
        '{"icon": "users"}', '{}', 95)
ON CONFLICT (code) DO NOTHING;

-- Starter templates
INSERT INTO workflow_definition (id, created_at, updated_at, company_id, workflow_key, name, description, subject_type,
                                 draft_graph, draft_updated_at, latest_version, archived, is_default)
SELECT gen_random_uuid()::text, now(), now(), NULL, 'standard-kyb', 'Standard KYB',
       'Business registry check and sanctions screening of the business; possible matches go to manual review.', 'BUSINESS',
       '{"nodes": [
          {"id": "start", "type": "START", "label": "Start", "position": {"x": 40, "y": 200}},
          {"id": "registry", "type": "SERVICE", "label": "Business registry", "service": "BUSINESS_REGISTRY", "position": {"x": 260, "y": 200}},
          {"id": "screening", "type": "SERVICE", "label": "Sanctions screening", "service": "SANCTIONS_SCREENING", "position": {"x": 500, "y": 200}},
          {"id": "review", "type": "MANUAL_REVIEW", "label": "Manual review", "position": {"x": 740, "y": 60}},
          {"id": "approved", "type": "END", "label": "Approved", "outcome": "APPROVED", "position": {"x": 740, "y": 200}},
          {"id": "rejected", "type": "END", "label": "Rejected", "outcome": "REJECTED", "position": {"x": 740, "y": 340}}],
        "edges": [
          {"from": "start", "port": "next", "to": "registry"},
          {"from": "registry", "port": "passed", "to": "screening"},
          {"from": "registry", "port": "failed", "to": "rejected"},
          {"from": "registry", "port": "review", "to": "review"},
          {"from": "screening", "port": "passed", "to": "approved"},
          {"from": "screening", "port": "failed", "to": "rejected"},
          {"from": "screening", "port": "review", "to": "review"}]}'::jsonb,
       now(), 0, FALSE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition WHERE company_id IS NULL AND workflow_key = 'standard-kyb');

INSERT INTO workflow_definition (id, created_at, updated_at, company_id, workflow_key, name, description, subject_type,
                                 draft_graph, draft_updated_at, latest_version, archived, is_default)
SELECT gen_random_uuid()::text, now(), now(), NULL, 'kyb-plus-owners', 'KYB plus owners',
       'Standard KYB, then the directors and owners the settings require; a person who fails or needs review sends the business to manual review.', 'BUSINESS',
       '{"nodes": [
          {"id": "start", "type": "START", "label": "Start", "position": {"x": 40, "y": 200}},
          {"id": "registry", "type": "SERVICE", "label": "Business registry", "service": "BUSINESS_REGISTRY", "position": {"x": 260, "y": 200}},
          {"id": "screening", "type": "SERVICE", "label": "Sanctions screening", "service": "SANCTIONS_SCREENING", "position": {"x": 500, "y": 200}},
          {"id": "owners", "type": "SERVICE", "label": "Directors and owners", "service": "OWNER_VERIFICATION", "position": {"x": 740, "y": 200}},
          {"id": "review", "type": "MANUAL_REVIEW", "label": "Manual review", "position": {"x": 980, "y": 60}},
          {"id": "approved", "type": "END", "label": "Approved", "outcome": "APPROVED", "position": {"x": 980, "y": 200}},
          {"id": "rejected", "type": "END", "label": "Rejected", "outcome": "REJECTED", "position": {"x": 980, "y": 340}}],
        "edges": [
          {"from": "start", "port": "next", "to": "registry"},
          {"from": "registry", "port": "passed", "to": "screening"},
          {"from": "registry", "port": "failed", "to": "rejected"},
          {"from": "registry", "port": "review", "to": "review"},
          {"from": "screening", "port": "passed", "to": "owners"},
          {"from": "screening", "port": "failed", "to": "rejected"},
          {"from": "screening", "port": "review", "to": "review"},
          {"from": "owners", "port": "passed", "to": "approved"},
          {"from": "owners", "port": "failed", "to": "review"},
          {"from": "owners", "port": "review", "to": "review"}]}'::jsonb,
       now(), 0, FALSE, FALSE
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition WHERE company_id IS NULL AND workflow_key = 'kyb-plus-owners');
