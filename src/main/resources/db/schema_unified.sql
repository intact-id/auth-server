-- =====================================================================================
-- Unified schema bootstrap — auth-server
--
-- Consolidates every file under db/ddl_*.sql and db/migrations/*.sql into a single,
-- dependency-ordered script that reflects the FINAL current schema (not a replay of
-- each historical migration step).
--
-- Intended for a FRESH, empty database only. Do NOT run this against a database that
-- already has these tables — it will fail on existing objects. For an existing
-- database, apply db/migrations/*.sql in filename (date) order instead.
--
-- Fixes one bug found while consolidating: ddl_UserActivityLog.sql and
-- ddl_WebhookDelivery.sql both FK'd to a table named "companies", which does not
-- exist anywhere in this schema (the real table is company_tbl). Corrected below.
-- =====================================================================================

BEGIN;

-- ─── roles ──────────────────────────────────────────────────────────────────────────
CREATE TABLE roles (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_role_name ON roles(name);

COMMENT ON TABLE roles IS 'User roles for role-based access control';
COMMENT ON COLUMN roles.name IS 'Role name: SUPER_ADMIN, ADMIN, CHECKER, MAKER, VIEWER';

-- ─── permissions ────────────────────────────────────────────────────────────────────
CREATE TABLE permissions (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE INDEX idx_permission_name ON permissions(name);
CREATE INDEX idx_resource_action ON permissions(resource, action);

COMMENT ON TABLE permissions IS 'Granular permissions for authorization';
COMMENT ON COLUMN permissions.name IS 'Permission name format: resource:action (e.g., company:create)';
COMMENT ON COLUMN permissions.resource IS 'Resource type: company, application, user, role, kyc';
COMMENT ON COLUMN permissions.action IS 'Action: create, read, update, delete, approve, verify';

-- ─── role_permissions (needs roles, permissions) ───────────────────────────────────
CREATE TABLE role_permissions (
    role_id VARCHAR(255) NOT NULL,
    permission_id VARCHAR(255) NOT NULL,

    PRIMARY KEY (role_id, permission_id),

    CONSTRAINT fk_role_permissions_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,

    CONSTRAINT fk_role_permissions_permission
        FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS 'Many-to-many mapping between roles and permissions';

-- ─── company_application ────────────────────────────────────────────────────────────
CREATE TABLE company_application
(
    id                   VARCHAR(255) NOT NULL,
    created_at           TIMESTAMP WITHOUT TIME ZONE,
    updated_at           TIMESTAMP WITHOUT TIME ZONE,
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255),
    application_number   VARCHAR(50)  NOT NULL,
    legal_name           VARCHAR(255) NOT NULL,
    trading_name         VARCHAR(255),
    registration_number  VARCHAR(255),
    email                VARCHAR(255) NOT NULL,
    phone                VARCHAR(255),
    website              VARCHAR(255),
    country              VARCHAR(2),
    business_type        VARCHAR(255),
    contact_person_name  VARCHAR(255),
    contact_person_title VARCHAR(100),
    contact_person_email VARCHAR(255),
    contact_person_phone VARCHAR(50),
    CONSTRAINT pk_companyapplication PRIMARY KEY (id),
    CONSTRAINT uc_companyapplication_application_number UNIQUE (application_number)
);

-- ─── company_application_approvals ─────────────────────────────────────────────────
-- company_id is only available after checker approval; maker stage has no company yet
CREATE TABLE company_application_approvals
(
    id                  VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE,
    updated_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    company_id          VARCHAR(255),
    application_id      VARCHAR(255),
    performed_by        VARCHAR(255),
    performed_by_action VARCHAR(255),
    approved_by         VARCHAR(255),
    final_action        VARCHAR(255),
    approver_note       TEXT,
    end_user_message    TEXT,
    action_performed_at TIMESTAMP WITHOUT TIME ZONE,
    action_approved_at  TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_application_approvals PRIMARY KEY (id)
);

-- ─── company_tbl ────────────────────────────────────────────────────────────────────
CREATE TABLE company_tbl
(
    id                       VARCHAR(255) NOT NULL,
    created_at               TIMESTAMP WITHOUT TIME ZONE,
    updated_at               TIMESTAMP WITHOUT TIME ZONE,
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    company_identifier_code  VARCHAR(255) NOT NULL,
    legal_name               VARCHAR(255) NOT NULL,
    trading_name             VARCHAR(255),
    registration_number      VARCHAR(255),
    country                  VARCHAR(2),
    business_type            VARCHAR(255),
    status                   VARCHAR(50)  NOT NULL,
    approved_at              TIMESTAMP WITHOUT TIME ZONE,
    approved_by              UUID,
    application_id           UUID,
    extra_details            JSONB,
    CONSTRAINT pk_company PRIMARY KEY (id),
    CONSTRAINT uc_company_company_identifier_code UNIQUE (company_identifier_code)
);

-- ─── users (needs company_tbl) ─────────────────────────────────────────────────────
-- Email uniqueness is scoped per company (not global); username stays globally unique
-- as the login identifier. Rows with no company (super admins) get their own partial
-- unique index since NULL company_id values don't collide in a composite UNIQUE.
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    email_verified BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    password_changed_at TIMESTAMP,
    must_change_password BOOLEAN DEFAULT FALSE,

    company_id VARCHAR(255),

    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES company_tbl(id),
    CONSTRAINT uk_email_company UNIQUE (email, company_id)
);

CREATE INDEX idx_username ON users(username);
CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_status ON users(status);
CREATE INDEX idx_company_id ON users(company_id);
CREATE UNIQUE INDEX uk_email_no_company ON users (email) WHERE company_id IS NULL;

COMMENT ON TABLE users IS 'System users with authentication and account security features';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hashed password';
COMMENT ON COLUMN users.failed_login_attempts IS 'Counter for failed login attempts, resets on successful login';
COMMENT ON COLUMN users.locked_until IS 'Account locked until this timestamp after 5 failed attempts';
COMMENT ON COLUMN users.must_change_password IS 'Force password change on next login';

-- ─── user_roles (needs users, roles) ───────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255) NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

COMMENT ON TABLE user_roles IS 'Many-to-many mapping between users and roles';

-- ─── refresh_tokens (needs users) ──────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    user_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    session_id VARCHAR(255),

    ip_address VARCHAR(45),
    user_agent TEXT,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens(session_id);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens for session management';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the refresh token';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Token revoked manually (logout)';
COMMENT ON COLUMN refresh_tokens.ip_address IS 'IP address where token was issued';
COMMENT ON COLUMN refresh_tokens.user_agent IS 'Browser/client user agent';

-- ─── company_credentials (needs company_tbl) ───────────────────────────────────────
CREATE TABLE company_credentials
(
    id                             VARCHAR(255) NOT NULL,
    created_at                     TIMESTAMP WITHOUT TIME ZONE,
    updated_at                     TIMESTAMP WITHOUT TIME ZONE,
    created_by                     VARCHAR(255),
    updated_by                     VARCHAR(255),
    company_id                     VARCHAR(255) NOT NULL,
    api_key_hash                   VARCHAR(255),
    api_secret_hash                VARCHAR(255),
    status                         VARCHAR(255) NOT NULL,
    last_used_at                   TIMESTAMP WITHOUT TIME ZONE,
    expires_at                     TIMESTAMP WITHOUT TIME ZONE,
    rate_limit_per_hour            INTEGER,
    rate_limit_per_day             INTEGER,
    rate_limit_per_month           INTEGER,
    total_requests                 BIGINT NOT NULL DEFAULT 0,
    total_successful_requests      BIGINT NOT NULL DEFAULT 0,
    total_failed_requests          BIGINT NOT NULL DEFAULT 0,
    security_settings              JSONB,
    metadata                       JSONB,
    dev_api_key_hash                VARCHAR(255),
    dev_api_secret_hash             VARCHAR(255),
    prod_api_key_hash               VARCHAR(255),
    prod_api_secret_hash            VARCHAR(255),
    dev_status                      VARCHAR(255),
    prod_status                     VARCHAR(255),
    dev_last_used_at                TIMESTAMP WITHOUT TIME ZONE,
    prod_last_used_at               TIMESTAMP WITHOUT TIME ZONE,
    dev_expires_at                  TIMESTAMP WITHOUT TIME ZONE,
    prod_expires_at                 TIMESTAMP WITHOUT TIME ZONE,
    dev_rate_limit_per_hour         INTEGER,
    dev_rate_limit_per_day          INTEGER,
    dev_rate_limit_per_month        INTEGER,
    prod_rate_limit_per_hour        INTEGER,
    prod_rate_limit_per_day         INTEGER,
    prod_rate_limit_per_month       INTEGER,
    dev_total_requests              BIGINT NOT NULL DEFAULT 0,
    dev_total_successful_requests   BIGINT NOT NULL DEFAULT 0,
    dev_total_failed_requests       BIGINT NOT NULL DEFAULT 0,
    prod_total_requests             BIGINT NOT NULL DEFAULT 0,
    prod_total_successful_requests  BIGINT NOT NULL DEFAULT 0,
    prod_total_failed_requests      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_companycredentials PRIMARY KEY (id),
    CONSTRAINT uc_companycredentials_api_key_hash UNIQUE (api_key_hash),
    CONSTRAINT uc_companycredentials_company UNIQUE (company_id),
    CONSTRAINT uc_companycredentials_dev_api_key_hash UNIQUE (dev_api_key_hash),
    CONSTRAINT uc_companycredentials_prod_api_key_hash UNIQUE (prod_api_key_hash),
    CONSTRAINT FK_COMPANYCREDENTIALS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id)
);

-- ─── company_communications (needs company_tbl) ────────────────────────────────────
CREATE TABLE company_communications
(
    id                    VARCHAR(255) NOT NULL,
    created_at            TIMESTAMP WITHOUT TIME ZONE,
    updated_at            TIMESTAMP WITHOUT TIME ZONE,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    company_id            VARCHAR(255) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    phone                 VARCHAR(255),
    website               VARCHAR(255),
    secondary_email       VARCHAR(255),
    secondary_phone       VARCHAR(255),
    support_email         VARCHAR(255),
    billing_email         VARCHAR(255),
    webhook_url           VARCHAR(255),
    webhook_secret        VARCHAR(255),
    webhook_enabled       BOOLEAN,
    email_notifications   BOOLEAN,
    sms_notifications     BOOLEAN,
    notification_settings JSONB,
    payload_fields        JSONB,
    CONSTRAINT pk_company_communications PRIMARY KEY (id),
    CONSTRAINT uc_company_communications_company UNIQUE (company_id),
    CONSTRAINT FK_COMPANY_COMMUNICATIONS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id)
);

CREATE INDEX idx_comm_email ON company_communications (email);
CREATE INDEX idx_comm_phone ON company_communications (phone);

-- ─── notifications (needs company_tbl) ─────────────────────────────────────────────
CREATE TABLE notifications
(
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    company_id VARCHAR(255),
    title      VARCHAR(255)                            NOT NULL,
    message    TEXT                                     NOT NULL,
    type       VARCHAR(255)                            NOT NULL,
    status     VARCHAR(255)                            NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE              NOT NULL,
    read_at    TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT FK_NOTIFICATIONS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id)
);

-- ─── verification_requests (needs company_tbl) ─────────────────────────────────────
CREATE TABLE verification_requests
(
    id                VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    company_id        VARCHAR(255) NOT NULL,
    tier              VARCHAR(20)  NOT NULL,
    verification_type VARCHAR(20),
    status            VARCHAR(255) NOT NULL,
    environment       VARCHAR(10)  NOT NULL DEFAULT 'PROD',
    customer_data     JSONB,
    metadata          JSONB,
    risk_score        DOUBLE PRECISION,
    overall_decision  VARCHAR(50),
    completed_at      TIMESTAMP WITHOUT TIME ZONE,
    webhook_url       VARCHAR(255),
    failure_reason    VARCHAR(100),
    error_details     TEXT,
    billing_amount    NUMERIC(19, 4),
    billed_at         TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_verification_requests PRIMARY KEY (id),
    CONSTRAINT FK_VERIFICATION_REQUESTS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id),
    CONSTRAINT verification_requests_status_check CHECK (status IN (
        'SUBMITTED', 'PENDING', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'INACTIVE',
        'SUSPENDED', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVOKED', 'EXPIRED',
        'PROCESSING', 'DRAFT', 'INFO_REQUESTED', 'REJECTED', 'ARCHIVED', 'UNKNOWN',
        'CLOSED', 'ON_HOLD'
    ))
);

CREATE INDEX idx_verification_company_env ON verification_requests(company_id, environment);

-- ─── verification_documents (needs verification_requests) ─────────────────────────
CREATE TABLE verification_documents
(
    id                VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITHOUT TIME ZONE,
    updated_at        TIMESTAMP WITHOUT TIME ZONE,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    verification_id   VARCHAR(255) NOT NULL,
    document_type     VARCHAR(50)  NOT NULL,
    file_path         VARCHAR(255) NOT NULL,
    file_size         BIGINT,
    mime_type         VARCHAR(100),
    original_filename VARCHAR(255),
    extracted_data    JSONB,
    CONSTRAINT pk_verification_documents PRIMARY KEY (id),
    CONSTRAINT FK_VERIFICATION_DOCUMENTS_ON_VERIFICATION FOREIGN KEY (verification_id) REFERENCES verification_requests (id)
);

-- ─── verification_steps (needs verification_requests) ─────────────────────────────
CREATE TABLE verification_steps
(
    id               VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP WITHOUT TIME ZONE,
    updated_at       TIMESTAMP WITHOUT TIME ZONE,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    verification_id  VARCHAR(255) NOT NULL,
    step_name        VARCHAR(50)  NOT NULL,
    status           VARCHAR(255) NOT NULL,
    result           JSONB,
    confidence_score DOUBLE PRECISION,
    executed_at      TIMESTAMP WITHOUT TIME ZONE,
    error_message    VARCHAR(255),
    CONSTRAINT pk_verification_steps PRIMARY KEY (id),
    CONSTRAINT FK_VERIFICATION_STEPS_ON_VERIFICATION FOREIGN KEY (verification_id) REFERENCES verification_requests (id)
);

-- ─── user_sessions (needs users, company_tbl, refresh_tokens) ─────────────────────
CREATE TABLE user_sessions (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    user_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255),
    refresh_token_id VARCHAR(255),
    session_id VARCHAR(255) UNIQUE NOT NULL,
    login_at TIMESTAMP NOT NULL,
    logout_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,

    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_sessions_company
        FOREIGN KEY (company_id) REFERENCES company_tbl(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_sessions_refresh_token
        FOREIGN KEY (refresh_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_company_id ON user_sessions(company_id);
CREATE INDEX idx_user_sessions_login_at ON user_sessions(login_at);
CREATE INDEX idx_user_sessions_status ON user_sessions(status);

-- ─── user_activity_logs (needs user_sessions, users, company_tbl) ─────────────────
-- NOTE: original ddl_UserActivityLog.sql FK'd company_id to a non-existent "companies"
-- table; corrected here to company_tbl (the table this app actually creates/uses).
CREATE TABLE user_activity_logs (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    session_id_fk VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255),
    activity_type VARCHAR(50) NOT NULL,
    http_method VARCHAR(16),
    path VARCHAR(500) NOT NULL,
    query_string TEXT,
    status_code INTEGER,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    ip_address VARCHAR(45),

    CONSTRAINT fk_user_activity_logs_session
        FOREIGN KEY (session_id_fk) REFERENCES user_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_logs_company
        FOREIGN KEY (company_id) REFERENCES company_tbl(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_activity_logs_session_id ON user_activity_logs(session_id_fk);
CREATE INDEX idx_user_activity_logs_user_id ON user_activity_logs(user_id);
CREATE INDEX idx_user_activity_logs_company_id ON user_activity_logs(company_id);
CREATE INDEX idx_user_activity_logs_started_at ON user_activity_logs(started_at);
CREATE INDEX idx_user_activity_logs_http_method ON user_activity_logs(http_method);
CREATE INDEX idx_user_activity_logs_path ON user_activity_logs(path);

-- ─── webhook_deliveries (needs company_tbl) ────────────────────────────────────────
-- NOTE: original ddl_WebhookDelivery.sql FK'd company_id to a non-existent "companies"
-- table; corrected here to company_tbl.
CREATE TABLE webhook_deliveries (
    id VARCHAR(255) PRIMARY KEY,
    company_id VARCHAR(255) NOT NULL,
    verification_id VARCHAR(255),
    webhook_url VARCHAR(500) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    http_status_code INTEGER,
    response_body TEXT,
    retry_count INTEGER DEFAULT 0 NOT NULL,
    next_retry_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    delivered_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_webhook_company FOREIGN KEY (company_id) REFERENCES company_tbl(id) ON DELETE CASCADE
);

CREATE INDEX idx_webhook_company ON webhook_deliveries(company_id);
CREATE INDEX idx_webhook_status ON webhook_deliveries(status);
CREATE INDEX idx_webhook_next_retry ON webhook_deliveries(next_retry_at);
CREATE INDEX idx_webhook_verification ON webhook_deliveries(verification_id);
CREATE INDEX idx_webhook_event_type ON webhook_deliveries(event_type);

COMMENT ON TABLE webhook_deliveries IS 'Tracks webhook delivery attempts and their status';
COMMENT ON COLUMN webhook_deliveries.event_type IS 'Type of event (e.g., verification.completed, verification.failed)';
COMMENT ON COLUMN webhook_deliveries.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN webhook_deliveries.next_retry_at IS 'Scheduled time for next retry attempt';
COMMENT ON COLUMN webhook_deliveries.status IS 'Delivery status: PENDING, DELIVERED, FAILED, RETRYING';

-- ─── backup_codes (needs users) ────────────────────────────────────────────────────
CREATE TABLE backup_codes (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    user_id VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL UNIQUE,
    used_at TIMESTAMP,

    CONSTRAINT fk_backup_codes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_backup_codes_user_id ON backup_codes(user_id);
CREATE INDEX idx_backup_codes_code_hash ON backup_codes(code_hash);

-- ─── trusted_devices (needs users) ─────────────────────────────────────────────────
CREATE TABLE trusted_devices (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    user_id VARCHAR(255) NOT NULL,
    device_token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_label VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    last_used_at TIMESTAMP,

    CONSTRAINT fk_trusted_devices_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_trusted_devices_token_hash ON trusted_devices(device_token_hash);
CREATE INDEX idx_trusted_devices_user_id ON trusted_devices(user_id);
CREATE INDEX idx_trusted_devices_expires_at ON trusted_devices(expires_at);

-- ─── company_billing_profiles (needs company_tbl) ──────────────────────────────────
CREATE TABLE company_billing_profiles
(
    id                          VARCHAR(255) PRIMARY KEY,
    created_at                  TIMESTAMP WITHOUT TIME ZONE,
    updated_at                  TIMESTAMP WITHOUT TIME ZONE,
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    company_id                  VARCHAR(255) NOT NULL UNIQUE,
    billing_tier_code           VARCHAR(50)  NOT NULL,
    billing_mode                VARCHAR(20)  NOT NULL,
    unit_price                  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency                    VARCHAR(10)  NOT NULL DEFAULT 'USD',
    billing_email               VARCHAR(255),
    bill_on_submission          BOOLEAN      NOT NULL DEFAULT FALSE,
    bill_on_completed           BOOLEAN      NOT NULL DEFAULT TRUE,
    bill_on_rejected            BOOLEAN      NOT NULL DEFAULT TRUE,
    bill_on_failed_non_system   BOOLEAN      NOT NULL DEFAULT TRUE,
    bill_on_failed_system       BOOLEAN      NOT NULL DEFAULT FALSE,
    current_balance             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    low_balance_threshold       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit_limit                NUMERIC(19, 4) NOT NULL DEFAULT 0,
    invoice_due_days            INTEGER      NOT NULL DEFAULT 30,
    status                      VARCHAR(50)  NOT NULL,
    effective_from              DATE,
    effective_to                DATE,
    CONSTRAINT fk_company_billing_profile_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id)
);

-- ─── billing_invoices (needs company_tbl, company_billing_profiles) ───────────────
CREATE TABLE billing_invoices
(
    id                  VARCHAR(255) PRIMARY KEY,
    created_at          TIMESTAMP WITHOUT TIME ZONE,
    updated_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    company_id          VARCHAR(255) NOT NULL,
    billing_profile_id  VARCHAR(255) NOT NULL,
    invoice_number      VARCHAR(255) NOT NULL UNIQUE,
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    billing_mode        VARCHAR(20)  NOT NULL,
    currency             VARCHAR(10)  NOT NULL,
    subtotal            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax                 NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total                NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_paid         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL,
    recipient_email     VARCHAR(255),
    generated_at        TIMESTAMP WITHOUT TIME ZONE,
    sent_at             TIMESTAMP WITHOUT TIME ZONE,
    due_at              DATE,
    CONSTRAINT fk_billing_invoice_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id),
    CONSTRAINT fk_billing_invoice_profile
        FOREIGN KEY (billing_profile_id) REFERENCES company_billing_profiles (id)
);

-- ─── billing_invoice_lines (needs billing_invoices) ────────────────────────────────
CREATE TABLE billing_invoice_lines
(
    id            VARCHAR(255) PRIMARY KEY,
    created_at    TIMESTAMP WITHOUT TIME ZONE,
    updated_at    TIMESTAMP WITHOUT TIME ZONE,
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    invoice_id    VARCHAR(255) NOT NULL,
    line_type     VARCHAR(50)  NOT NULL,
    description   TEXT         NOT NULL,
    quantity      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    unit_price    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    CONSTRAINT fk_billing_invoice_line_invoice
        FOREIGN KEY (invoice_id) REFERENCES billing_invoices (id)
);

-- ─── billing_charges (needs company_tbl, company_billing_profiles, verification_requests, billing_invoices)
CREATE TABLE billing_charges
(
    id                      VARCHAR(255) PRIMARY KEY,
    created_at              TIMESTAMP WITHOUT TIME ZONE,
    updated_at              TIMESTAMP WITHOUT TIME ZONE,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    company_id              VARCHAR(255) NOT NULL,
    billing_profile_id      VARCHAR(255) NOT NULL,
    verification_request_id VARCHAR(255) NOT NULL UNIQUE,
    invoice_id              VARCHAR(255),
    billing_event           VARCHAR(30)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    billing_tier_code       VARCHAR(50)  NOT NULL,
    currency                VARCHAR(10)  NOT NULL,
    unit_price              NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount                  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    decision_reason         TEXT,
    chargeable_at           TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    charged_at              TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_billing_charge_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id),
    CONSTRAINT fk_billing_charge_profile
        FOREIGN KEY (billing_profile_id) REFERENCES company_billing_profiles (id),
    CONSTRAINT fk_billing_charge_verification
        FOREIGN KEY (verification_request_id) REFERENCES verification_requests (id),
    CONSTRAINT fk_billing_charge_invoice
        FOREIGN KEY (invoice_id) REFERENCES billing_invoices (id)
);

-- ─── wallet_transactions (needs company_tbl, company_billing_profiles) ────────────
CREATE TABLE wallet_transactions
(
    id                      VARCHAR(255) PRIMARY KEY,
    created_at              TIMESTAMP WITHOUT TIME ZONE,
    updated_at              TIMESTAMP WITHOUT TIME ZONE,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    company_id              VARCHAR(255) NOT NULL,
    billing_profile_id      VARCHAR(255) NOT NULL,
    type                    VARCHAR(20)  NOT NULL,
    amount                  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency                VARCHAR(10)  NOT NULL,
    reference               VARCHAR(255),
    description             TEXT,
    verification_request_id VARCHAR(255),
    balance_before          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    balance_after           NUMERIC(19, 4) NOT NULL DEFAULT 0,
    CONSTRAINT fk_wallet_transaction_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id),
    CONSTRAINT fk_wallet_transaction_profile
        FOREIGN KEY (billing_profile_id) REFERENCES company_billing_profiles (id)
);

COMMIT;
