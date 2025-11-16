CREATE TABLE company_tbl
(
    id                       VARCHAR(255) NOT NULL,
    created_at               TIMESTAMP WITHOUT TIME ZONE,
    updated_at               TIMESTAMP WITHOUT TIME ZONE,
    created_by               VARCHAR(255),
    updated_by               VARCHAR(255),
    company_identifier_code VARCHAR(255) NOT NULL,
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
    CONSTRAINT pk_company PRIMARY KEY (id)
);

ALTER TABLE company_tbl
    ADD CONSTRAINT uc_company_company_identifier_code UNIQUE (company_identifier_code);