CREATE TABLE application_approvals
(
    id                  VARCHAR(255) PRIMARY KEY,
    created_at          TIMESTAMP WITHOUT TIME ZONE,
    updated_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    company_id          VARCHAR(255) NOT NULL,
    application_id      VARCHAR(255) NOT NULL,
    performed_by        UUID         NOT NULL,
    performed_by_action VARCHAR(255) NOT NULL,
    approved_by         UUID,
    final_action        VARCHAR(255),
    performer_note      TEXT,
    approver_note       TEXT,
    end_user_message    TEXT,
    action_performed_at TIMESTAMP WITHOUT TIME ZONE,
    action_approved_at  TIMESTAMP WITHOUT TIME ZONE
);

ALTER TABLE application_approvals
    ADD CONSTRAINT fk_app_approval_company
        FOREIGN KEY (company_id)
            REFERENCES company_tbl(id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

ALTER TABLE application_approvals
    ADD CONSTRAINT fk_app_approval_application
        FOREIGN KEY (application_id)
            REFERENCES company_application(id)
            ON DELETE CASCADE
            ON UPDATE CASCADE;

