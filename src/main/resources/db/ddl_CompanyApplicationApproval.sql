CREATE TABLE application_approvals
(
    id                  VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE,
    updated_at          TIMESTAMP WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    company_id          VARCHAR(255) ,
    application_id      VARCHAR(255) ,
    performed_by        VARCHAR(255) ,
    performed_by_action VARCHAR(255) ,
    approved_by         VARCHAR(255),
    final_action        VARCHAR(255),
    approver_note       TEXT,
    end_user_message    TEXT,
    action_performed_at TIMESTAMP WITHOUT TIME ZONE,
    action_approved_at  TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_application_approvals PRIMARY KEY (id)
);