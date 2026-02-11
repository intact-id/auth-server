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
    customer_data     JSONB,
    metadata          JSONB,
    risk_score        DOUBLE PRECISION,
    overall_decision  VARCHAR(50),
    completed_at      TIMESTAMP WITHOUT TIME ZONE,
    webhook_url       VARCHAR(255),
    failure_reason    VARCHAR(100),
    error_details     TEXT,
    CONSTRAINT pk_verification_requests PRIMARY KEY (id)
);

ALTER TABLE verification_requests
    ADD CONSTRAINT FK_VERIFICATION_REQUESTS_ON_COMPANY FOREIGN KEY (company_id) REFERENCES company_tbl (id);
