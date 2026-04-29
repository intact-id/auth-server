CREATE TABLE IF NOT EXISTS company_billing_profiles
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
    effective_to                DATE
);

ALTER TABLE company_billing_profiles
    ADD CONSTRAINT fk_company_billing_profile_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id);

CREATE TABLE IF NOT EXISTS billing_invoices
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
    currency            VARCHAR(10)  NOT NULL,
    subtotal            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax                 NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total               NUMERIC(19, 4) NOT NULL DEFAULT 0,
    amount_paid         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL,
    recipient_email     VARCHAR(255),
    generated_at        TIMESTAMP WITHOUT TIME ZONE,
    sent_at             TIMESTAMP WITHOUT TIME ZONE,
    due_at              DATE
);

ALTER TABLE billing_invoices
    ADD CONSTRAINT fk_billing_invoice_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id);

ALTER TABLE billing_invoices
    ADD CONSTRAINT fk_billing_invoice_profile
        FOREIGN KEY (billing_profile_id) REFERENCES company_billing_profiles (id);

CREATE TABLE IF NOT EXISTS billing_invoice_lines
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
    amount        NUMERIC(19, 4) NOT NULL DEFAULT 0
);

ALTER TABLE billing_invoice_lines
    ADD CONSTRAINT fk_billing_invoice_line_invoice
        FOREIGN KEY (invoice_id) REFERENCES billing_invoices (id);

CREATE TABLE IF NOT EXISTS billing_charges
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
    charged_at              TIMESTAMP WITHOUT TIME ZONE
);

ALTER TABLE billing_charges
    ADD CONSTRAINT fk_billing_charge_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id);

ALTER TABLE billing_charges
    ADD CONSTRAINT fk_billing_charge_profile
        FOREIGN KEY (billing_profile_id) REFERENCES company_billing_profiles (id);

ALTER TABLE billing_charges
    ADD CONSTRAINT fk_billing_charge_verification
        FOREIGN KEY (verification_request_id) REFERENCES verification_requests (id);

ALTER TABLE billing_charges
    ADD CONSTRAINT fk_billing_charge_invoice
        FOREIGN KEY (invoice_id) REFERENCES billing_invoices (id);

CREATE TABLE IF NOT EXISTS wallet_transactions
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
    balance_after           NUMERIC(19, 4) NOT NULL DEFAULT 0
);

ALTER TABLE wallet_transactions
    ADD CONSTRAINT fk_wallet_transaction_company
        FOREIGN KEY (company_id) REFERENCES company_tbl (id);

ALTER TABLE wallet_transactions
    ADD CONSTRAINT fk_wallet_transaction_profile
        FOREIGN KEY (billing_profile_id) REFERENCES company_billing_profiles (id);
