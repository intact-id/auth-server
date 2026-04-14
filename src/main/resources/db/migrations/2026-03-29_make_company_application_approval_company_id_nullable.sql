-- company_id is only available after checker approval; maker stage has no company yet
ALTER TABLE company_application_approvals ALTER COLUMN company_id DROP NOT NULL;
