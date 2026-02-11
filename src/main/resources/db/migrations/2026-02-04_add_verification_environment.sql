-- Add environment column to verification_requests
ALTER TABLE verification_requests
    ADD COLUMN IF NOT EXISTS environment VARCHAR(10) NOT NULL DEFAULT 'PROD';

CREATE INDEX IF NOT EXISTS idx_verification_company_env
    ON verification_requests(company_id, environment);
