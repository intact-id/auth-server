-- A company's default workflow runs when a session or API call names none.
ALTER TABLE workflow_definition ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one default per company (archived workflows are never the default)
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_definition_company_default
    ON workflow_definition (company_id) WHERE is_default;
