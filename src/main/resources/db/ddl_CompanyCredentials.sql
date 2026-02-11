CREATE TABLE company_credentials
(
    id                        VARCHAR(255) NOT NULL,
    created_at                TIMESTAMP WITHOUT TIME ZONE,
    updated_at                TIMESTAMP WITHOUT TIME ZONE,
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),
    company_id                VARCHAR(255) NOT NULL,
    api_key_hash              VARCHAR(255),
    api_secret_hash           VARCHAR(255),
    status                    VARCHAR(255) NOT NULL,
    last_used_at              TIMESTAMP WITHOUT TIME ZONE,
    expires_at                TIMESTAMP WITHOUT TIME ZONE,
    rate_limit_per_hour       INTEGER,
    rate_limit_per_day        INTEGER,
    rate_limit_per_month      INTEGER,
    total_requests            BIGINT,
    total_successful_requests BIGINT,
    total_failed_requests     BIGINT,
    security_settings         JSONB,
    metadata                  JSONB,
    CONSTRAINT pk_companycredentials PRIMARY KEY (id)
);

ALTER TABLE company_credentials
    ADD CONSTRAINT uc_companycredentials_api_key_hash UNIQUE (api_key_hash);

ALTER TABLE company_credentials
    ADD CONSTRAINT uc_companycredentials_company UNIQUE (company_id);

ALTER TABLE company_credentials
    ADD CONSTRAINT FK_COMPANYCREDENTIALS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id);
