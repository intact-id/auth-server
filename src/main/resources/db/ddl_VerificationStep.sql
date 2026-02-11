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
    CONSTRAINT pk_verification_steps PRIMARY KEY (id)
);

ALTER TABLE verification_steps
    ADD CONSTRAINT FK_VERIFICATION_STEPS_ON_VERIFICATION FOREIGN KEY (verification_id) REFERENCES verification_requests (id);