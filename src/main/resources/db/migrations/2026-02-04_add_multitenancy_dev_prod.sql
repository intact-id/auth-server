-- Consolidated migration: multi-tenant users, dev/prod API keys, verification environment

-- Users: add company_id + FK + index
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS company_id VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_users_company'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT fk_users_company
                FOREIGN KEY (company_id) REFERENCES company_tbl(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_company_id ON users(company_id);

-- Company credentials: dev/prod keys, status, usage, limits
ALTER TABLE company_credentials
    ADD COLUMN IF NOT EXISTS dev_api_key_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dev_api_secret_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS prod_api_key_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS prod_api_secret_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dev_status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS prod_status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dev_last_used_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS prod_last_used_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS dev_expires_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS prod_expires_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS dev_rate_limit_per_hour INTEGER,
    ADD COLUMN IF NOT EXISTS dev_rate_limit_per_day INTEGER,
    ADD COLUMN IF NOT EXISTS dev_rate_limit_per_month INTEGER,
    ADD COLUMN IF NOT EXISTS prod_rate_limit_per_hour INTEGER,
    ADD COLUMN IF NOT EXISTS prod_rate_limit_per_day INTEGER,
    ADD COLUMN IF NOT EXISTS prod_rate_limit_per_month INTEGER,
    ADD COLUMN IF NOT EXISTS dev_total_requests BIGINT,
    ADD COLUMN IF NOT EXISTS dev_total_successful_requests BIGINT,
    ADD COLUMN IF NOT EXISTS dev_total_failed_requests BIGINT,
    ADD COLUMN IF NOT EXISTS prod_total_requests BIGINT,
    ADD COLUMN IF NOT EXISTS prod_total_successful_requests BIGINT,
    ADD COLUMN IF NOT EXISTS prod_total_failed_requests BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uc_companycredentials_dev_api_key_hash'
    ) THEN
        ALTER TABLE company_credentials
            ADD CONSTRAINT uc_companycredentials_dev_api_key_hash UNIQUE (dev_api_key_hash);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uc_companycredentials_prod_api_key_hash'
    ) THEN
        ALTER TABLE company_credentials
            ADD CONSTRAINT uc_companycredentials_prod_api_key_hash UNIQUE (prod_api_key_hash);
    END IF;
END $$;

-- Backfill: treat existing legacy keys as prod keys
UPDATE company_credentials
SET prod_api_key_hash = api_key_hash,
    prod_api_secret_hash = api_secret_hash,
    prod_status = status,
    prod_expires_at = expires_at
WHERE prod_api_key_hash IS NULL AND api_key_hash IS NOT NULL;

-- Verification requests: environment + index
ALTER TABLE verification_requests
    ADD COLUMN IF NOT EXISTS environment VARCHAR(10) NOT NULL DEFAULT 'PROD';

CREATE INDEX IF NOT EXISTS idx_verification_company_env
    ON verification_requests(company_id, environment);
