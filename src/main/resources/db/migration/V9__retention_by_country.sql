-- Retention by country and kind of data, legal holds and the end of the customer relationship.
-- Replaces retention_settings (V8): its values move into the new tables.

-- What Intact allows per country (ISO 3166 alpha-2; '*' = any country without its own row) and kind of data.
-- min_days: must be kept at least this long (e.g. AML record keeping); max_days: may be kept at most this long.
-- default_days: what applies when a company sets nothing; NULL with no max_days keeps the data.
CREATE TABLE IF NOT EXISTS retention_country_rules
(
    id           VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at   TIMESTAMP(6),
    updated_at   TIMESTAMP(6),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    country      VARCHAR(2)   NOT NULL,
    -- BIOMETRICS (selfies, liveness frames), DOCUMENTS (ID and business documents),
    -- PERSONAL_DATA (names, numbers, contact, OCR results), RECORD (the verification and its audit trail)
    category     VARCHAR(20)  NOT NULL CHECK (category IN ('BIOMETRICS', 'DOCUMENTS', 'PERSONAL_DATA', 'RECORD')),
    min_days     INTEGER CHECK (min_days IS NULL OR min_days >= 0),
    max_days     INTEGER CHECK (max_days IS NULL OR max_days >= 0),
    default_days INTEGER CHECK (default_days IS NULL OR default_days >= 0),
    -- FINISHED: count from when the verification finished; RELATIONSHIP_END: from when the merchant says the
    -- customer relationship ended (nothing is due before that)
    starts_from  VARCHAR(20)  NOT NULL DEFAULT 'FINISHED' CHECK (starts_from IN ('FINISHED', 'RELATIONSHIP_END')),
    -- The law or policy behind the limits, shown to merchants
    legal_basis  VARCHAR(500),
    CONSTRAINT uq_retention_country_rule UNIQUE (country, category),
    CONSTRAINT ck_retention_country_range CHECK (min_days IS NULL OR max_days IS NULL OR min_days <= max_days)
);

-- A company's own periods, per country ('*' = every country) and kind of data, within the country's limits.
CREATE TABLE IF NOT EXISTS retention_company_rules
(
    id         VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    company_id VARCHAR(255) NOT NULL,
    country    VARCHAR(2)   NOT NULL,
    category   VARCHAR(20)  NOT NULL CHECK (category IN ('BIOMETRICS', 'DOCUMENTS', 'PERSONAL_DATA', 'RECORD')),
    days       INTEGER      NOT NULL CHECK (days >= 0),
    CONSTRAINT uq_retention_company_rule UNIQUE (company_id, country, category)
);

-- Carry over V8's rules
INSERT INTO retention_country_rules (id, created_at, updated_at, country, category, default_days)
SELECT gen_random_uuid()::text, now(), now(), '*', c.category,
       CASE WHEN c.category = 'PERSONAL_DATA' THEN s.personal_data_days ELSE s.documents_days END
FROM retention_settings s
         CROSS JOIN (VALUES ('BIOMETRICS'), ('DOCUMENTS'), ('PERSONAL_DATA')) AS c(category)
WHERE s.company_id IS NULL
ON CONFLICT (country, category) DO NOTHING;
INSERT INTO retention_country_rules (id, created_at, updated_at, country, category)
SELECT gen_random_uuid()::text, now(), now(), '*', c.category
FROM (VALUES ('BIOMETRICS'), ('DOCUMENTS'), ('PERSONAL_DATA'), ('RECORD')) AS c(category)
ON CONFLICT (country, category) DO NOTHING;

INSERT INTO retention_company_rules (id, created_at, updated_at, company_id, country, category, days)
SELECT gen_random_uuid()::text, now(), now(), s.company_id, '*', c.category,
       CASE WHEN c.category = 'PERSONAL_DATA' THEN s.personal_data_days ELSE s.documents_days END
FROM retention_settings s
         CROSS JOIN (VALUES ('BIOMETRICS'), ('DOCUMENTS'), ('PERSONAL_DATA')) AS c(category)
WHERE s.company_id IS NOT NULL
  AND (CASE WHEN c.category = 'PERSONAL_DATA' THEN s.personal_data_days ELSE s.documents_days END) IS NOT NULL
ON CONFLICT (company_id, country, category) DO NOTHING;

DROP TABLE IF EXISTS retention_settings;

-- Erasures can now be of each kind of data
ALTER TABLE data_erasures DROP CONSTRAINT IF EXISTS data_erasures_scope_check;
ALTER TABLE data_erasures ADD CONSTRAINT data_erasures_scope_check
    CHECK (scope IN ('BIOMETRICS', 'DOCUMENTS', 'PERSONAL_DATA', 'RECORD'));
-- Manual erasures that a super admin forced past a minimum period
ALTER TABLE data_erasures ADD COLUMN IF NOT EXISTS overrode_minimum BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE verification_requests
    -- Whose law applies: the applicant's country of residence (else nationality), the business's country of
    -- registration, else the merchant's country
    ADD COLUMN IF NOT EXISTS data_country          VARCHAR(2),
    ADD COLUMN IF NOT EXISTS relationship_ended_at TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS legal_hold            BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS legal_hold_reason     VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS legal_hold_by         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS legal_hold_at         TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS biometrics_deleted_at TIMESTAMP(6),
    ADD COLUMN IF NOT EXISTS record_purged_at      TIMESTAMP(6),
    -- The nightly run only looks at rows whose next deletion is due; planned is reset when rules change
    ADD COLUMN IF NOT EXISTS retention_planned     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS next_retention_at     TIMESTAMP(6);

DROP INDEX IF EXISTS idx_verification_requests_retention;
CREATE INDEX IF NOT EXISTS idx_verification_requests_retention_due ON verification_requests (next_retention_at)
    WHERE retention_planned AND next_retention_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_verification_requests_retention_unplanned ON verification_requests (company_id)
    WHERE NOT retention_planned AND record_purged_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_verification_requests_external_reference ON verification_requests (company_id, external_reference);
