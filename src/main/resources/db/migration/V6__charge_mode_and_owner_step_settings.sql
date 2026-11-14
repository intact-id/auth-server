-- How a company's verifications are charged, and settings on the "Directors and owners" workflow step.

-- WORKFLOW_PRICE: each run costs the workflow's published price, whichever checks ran.
-- CHECKS_RUN: each run costs only the checks it reached. NULL = the platform default (billing.charge-mode).
ALTER TABLE company_tbl ADD COLUMN IF NOT EXISTS charge_mode VARCHAR(20)
    CHECK (charge_mode IN ('WORKFLOW_PRICE', 'CHECKS_RUN'));

-- Email directors and owners their verification link as soon as the business reaches that step (FULL_KYC)
ALTER TABLE kyb_settings ADD COLUMN IF NOT EXISTS auto_invite BOOLEAN NOT NULL DEFAULT FALSE;

-- The "Directors and owners" step can set its own rules; anything left unset uses the company's or platform's
UPDATE workflow_service
SET settings_schema = '{
  "type": "object",
  "properties": {
    "ownerCheck": {"enum": ["SCREENING", "FULL_KYC"], "title": "How people are checked",
                   "description": "Screening checks names against sanctions and PEP lists. Full KYC has each person complete identity verification."},
    "ownershipThreshold": {"type": "number", "minimum": 0, "maximum": 100, "title": "Ownership threshold (%)",
                           "description": "Owners with at least this share are checked."},
    "includeDirectors": {"type": "boolean", "title": "Check directors",
                         "description": "Directors are checked whatever their share."},
    "personWorkflow": {"type": "string", "title": "Workflow for each person",
                       "description": "Key of the identity workflow each person runs (full KYC). Empty: the company default."},
    "autoInvite": {"type": "boolean", "title": "Send invites automatically",
                   "description": "Email each person their link when the business reaches this step (full KYC). People without an email get their link from you."}
  }
}'::jsonb
WHERE code = 'OWNER_VERIFICATION';
