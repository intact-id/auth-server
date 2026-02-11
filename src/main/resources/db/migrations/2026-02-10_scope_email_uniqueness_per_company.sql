-- Scope email uniqueness per company instead of globally.
-- Username remains globally unique (used as login identifier).

-- Drop the global unique constraint on email
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
DROP INDEX IF EXISTS uk_email_company;

-- Add composite unique constraint on (email, company_id)
-- Note: In PostgreSQL, rows with NULL company_id are each considered distinct,
-- so we also add a partial unique index for users without a company (super admins).
ALTER TABLE users
    ADD CONSTRAINT uk_email_company UNIQUE (email, company_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_no_company
    ON users (email) WHERE company_id IS NULL;
