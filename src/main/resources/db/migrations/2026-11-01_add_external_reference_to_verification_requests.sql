ALTER TABLE verification_requests
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_verification_company_env_ext_ref
    ON verification_requests (company_id, environment, external_reference, created_at DESC)
    WHERE external_reference IS NOT NULL;
