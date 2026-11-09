-- Sessions, drafts and verifications pin the workflow version they run; verifications keep the path taken.
-- Safe to run more than once.
ALTER TABLE kyc_drafts ADD COLUMN IF NOT EXISTS workflow_id VARCHAR(255);
ALTER TABLE kyc_drafts ADD COLUMN IF NOT EXISTS workflow_version INTEGER;
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS workflow_id VARCHAR(255);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS workflow_version INTEGER;
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS workflow_path JSONB;
