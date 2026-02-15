-- Add dedicated webhook signing secret to company_communications.
-- Replaces using company.id as the HMAC secret (which was insecure since company.id is public).
ALTER TABLE company_communications
    ADD COLUMN IF NOT EXISTS webhook_secret VARCHAR(255);
