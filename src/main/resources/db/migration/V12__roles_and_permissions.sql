-- Roles and permissions: built-in roles for companies and Intact staff, custom roles a company makes for itself,
-- and every permission the API checks (named in Permissions) so custom roles can be given them.

-- A custom role belongs to one company; built-in roles have no company. label is what the console shows.
ALTER TABLE roles ADD COLUMN IF NOT EXISTS company_id VARCHAR(255);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS label VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_roles_company ON roles (company_id);

-- Built-in roles (their permissions are defined in code, Permissions.BUILT_IN_ROLES)
INSERT INTO roles (id, created_at, updated_at, name, label, description, status)
SELECT gen_random_uuid()::text, now(), now(), v.name, v.label, v.description, 'ACTIVE'
FROM (VALUES
    ('ADMIN', 'Admin', 'Everything for the company, including team and roles'),
    ('COMPLIANCE_OFFICER', 'Compliance officer', 'Verifications, reviews, monitoring, screening, risk models and data protection'),
    ('REVIEWER', 'Reviewer', 'Sees verifications; decides manual reviews and monitoring alerts'),
    ('DEVELOPER', 'Developer', 'API keys, webhooks and workflows'),
    ('FINANCE', 'Finance', 'Billing, invoices and top-ups'),
    ('VIEWER', 'Viewer', 'Read-only'),
    ('SUPER_ADMIN', 'Super admin', 'Everything across the platform'),
    ('OPERATIONS', 'Operations', 'Applications, companies, their users and reviews'),
    ('STAFF_FINANCE', 'Finance (Intact)', 'Prices, billing set-up, FX and top-up reviews'),
    ('SUPPORT', 'Support', 'Read-only across every company'),
    ('MAKER', 'Application maker', 'First decision on company applications'),
    ('CHECKER', 'Application checker', 'Final decision on company applications')
) AS v(name, label, description)
WHERE NOT EXISTS (SELECT 1 FROM roles r WHERE r.name = v.name);

UPDATE roles r SET label = v.label, description = v.description
FROM (VALUES
    ('ADMIN', 'Admin', 'Everything for the company, including team and roles'),
    ('COMPLIANCE_OFFICER', 'Compliance officer', 'Verifications, reviews, monitoring, screening, risk models and data protection'),
    ('REVIEWER', 'Reviewer', 'Sees verifications; decides manual reviews and monitoring alerts'),
    ('DEVELOPER', 'Developer', 'API keys, webhooks and workflows'),
    ('FINANCE', 'Finance', 'Billing, invoices and top-ups'),
    ('VIEWER', 'Viewer', 'Read-only'),
    ('SUPER_ADMIN', 'Super admin', 'Everything across the platform'),
    ('OPERATIONS', 'Operations', 'Applications, companies, their users and reviews'),
    ('STAFF_FINANCE', 'Finance (Intact)', 'Prices, billing set-up, FX and top-up reviews'),
    ('SUPPORT', 'Support', 'Read-only across every company'),
    ('MAKER', 'Application maker', 'First decision on company applications'),
    ('CHECKER', 'Application checker', 'Final decision on company applications')
) AS v(name, label, description)
WHERE r.name = v.name AND r.company_id IS NULL;

-- Every permission, so a custom role can hold it (built-in roles don't use these rows)
INSERT INTO permissions (id, created_at, updated_at, name, resource, action, description)
SELECT gen_random_uuid()::text, now(), now(), v.name, split_part(v.name, ':', 1), split_part(v.name, ':', 2), v.description
FROM (VALUES
    ('verifications:view', 'See verifications and their documents'),
    ('verifications:manage', 'Release held verifications'),
    ('reviews:view', 'See the manual review queue'),
    ('reviews:manage', 'Take and decide manual reviews'),
    ('analytics:view', 'See statistics and the overview'),
    ('kyb:view', 'See directors and owners, and businesses waiting'),
    ('kyb:invite', 'Send directors and owners their verification links'),
    ('kyb:manage', 'Change KYB settings'),
    ('screening:view', 'See screening results'),
    ('screening:manage', 'Run a screening check by hand'),
    ('monitoring:use', 'See monitoring, decide alerts, start or stop monitoring'),
    ('monitoring:manage', 'Change monitoring settings'),
    ('risk:manage', 'Create, change and activate risk models'),
    ('workflows:manage', 'Create, change, publish and archive workflows'),
    ('services:available', 'See the services workflows can use'),
    ('services:view', 'See the full service catalog'),
    ('services:manage', 'Price a workflow'),
    ('apikeys:view', 'See API keys and their usage'),
    ('apikeys:manage', 'Create and revoke API keys'),
    ('webhooks:view', 'See webhook settings and deliveries'),
    ('webhooks:manage', 'Change webhook settings, test and rotate the secret'),
    ('companies:profile', 'See the company profile'),
    ('companies:view', 'See company details and limits'),
    ('companies:manage', 'Change the company profile and credentials'),
    ('users:view', 'See team members'),
    ('users:manage', 'Invite and change team members'),
    ('roles:manage', 'Create roles and assign them to team members'),
    ('notifications:inbox', 'See and clear notifications'),
    ('notifications:view', 'See all of the company''s notifications'),
    ('notifications:manage', 'Send and delete notifications'),
    ('topups:view', 'See wallet top-ups'),
    ('topups:manage', 'Submit and cancel bank transfer top-ups'),
    ('privacy:view', 'See retention rules, legal holds and erasures'),
    ('privacy:requests', 'Erase data, see a verification''s retention'),
    ('privacy:manage', 'Change retention periods and legal holds'),
    ('privacy:manage-company', 'Record that a customer relationship ended'),
    ('applications:view', 'See company applications'),
    ('applications:make', 'Make the first decision on an application'),
    ('applications:check', 'Make the final (checker) decision on an application'),
    ('applications:manage', 'Delete applications'),
    ('companies:manage-platform', 'Change companies'' rate limits and notification settings'),
    ('users:view-platform', 'See Intact staff and every company''s users'),
    ('users:manage-platform', 'Suspend users and assign staff roles'),
    ('kyb:manage-platform', 'Change the platform''s KYB defaults'),
    ('privacy:manage-platform', 'Change the retention rules per country'),
    ('privacy:override-platform', 'Erase data before its minimum period'),
    ('services:manage-platform', 'Manage the service catalog'),
    ('templates:manage-platform', 'Create and change workflow templates'),
    ('billing:view-platform', 'See companies'' billing'),
    ('billing:manage', 'Change companies'' billing (older billing pages)'),
    ('billing:manage-platform', 'Set up billing, prices, FX, credits and invoices'),
    ('topups:manage-platform', 'Approve and reject top-ups'),
    ('analytics:view-platform', 'See verification timelines and turnaround'),
    ('audit:view', 'See the audit log'),
    ('system:use', 'See system status'),
    ('widget:use', 'Widget sessions'),
    ('widget:view', 'Widget verification status'),
    ('widget:manage', 'Widget drafts and handoffs'),
    ('verifications:manage-api', 'Submit verifications'),
    ('kyb:manage-api', 'Submit business verifications'),
    ('kyb:view-api', 'Read business verifications from the widget'),
    ('billing:view-api', 'Read the company''s billing'),
    ('apikeys:manage-api', 'Exchange the key for an access token')
) AS v(name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = v.name);
-- What a company's API key may do: FULL, VERIFY (sessions, submitting verifications, reading results) or READ_ONLY
ALTER TABLE company_credentials ADD COLUMN IF NOT EXISTS api_access VARCHAR(20) NOT NULL DEFAULT 'FULL'
    CHECK (api_access IN ('FULL', 'VERIFY', 'READ_ONLY'));
