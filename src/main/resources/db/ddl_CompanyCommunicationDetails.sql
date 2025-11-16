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
    webhook_enabled       BOOLEAN,
    email_notifications   BOOLEAN,
    sms_notifications     BOOLEAN,
    notification_settings JSONB,
    CONSTRAINT pk_company_communications PRIMARY KEY (id)
);

ALTER TABLE company_communications
    ADD CONSTRAINT uc_company_communications_company UNIQUE (company_id);

CREATE INDEX idx_comm_email ON company_communications (email);

CREATE INDEX idx_comm_phone ON company_communications (phone);

ALTER TABLE company_communications
    ADD CONSTRAINT FK_COMPANY_COMMUNICATIONS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id);