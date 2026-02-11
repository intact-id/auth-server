-- Add company_id to users for tenant scoping
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

-- Backfill guidance:
-- Set company_id for tenant-scoped users once you know the mapping.
-- Example (replace with real logic):
-- UPDATE users SET company_id = '<company-uuid>' WHERE username IN (...);
