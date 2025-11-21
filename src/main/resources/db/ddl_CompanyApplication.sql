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
    CONSTRAINT pk_companyapplication PRIMARY KEY (id)
);

ALTER TABLE company_application
    ADD CONSTRAINT uc_companyapplication_application_number UNIQUE (application_number);