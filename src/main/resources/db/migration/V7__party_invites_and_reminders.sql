-- Invite links for directors and owners: when the link was made, whether Intact emailed it, and reminders.
ALTER TABLE verification_parties ADD COLUMN IF NOT EXISTS invited_at TIMESTAMP(6);
-- True when Intact emailed the current link; false when the merchant shares it themselves
ALTER TABLE verification_parties ADD COLUMN IF NOT EXISTS invite_emailed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE verification_parties ADD COLUMN IF NOT EXISTS reminders_sent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE verification_parties ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMP(6);
CREATE INDEX IF NOT EXISTS idx_verification_parties_status ON verification_parties (status);

-- "Send invites automatically" now means "email them": every required person gets a link either way
UPDATE workflow_service
SET settings_schema = jsonb_set(settings_schema, '{properties,autoInvite}',
    '{"type": "boolean", "title": "Email invites automatically",
      "description": "Email each person their link when the business reaches this step (full KYC). Links are always created, so you can also share them yourself from the console, the API or the webhook."}'::jsonb)
WHERE code = 'OWNER_VERIFICATION';
