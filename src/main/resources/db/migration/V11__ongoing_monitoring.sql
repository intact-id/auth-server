-- Ongoing monitoring: approved people, businesses and their directors and owners are screened again on a
-- schedule; a change (new match, a match newly sanctioned or PEP, a match removed) raises an alert and the
-- company is called back (webhook screening.updated).

-- Per company: off until the company turns it on
CREATE TABLE IF NOT EXISTS monitoring_settings
(
    id                VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at        TIMESTAMP(6),
    updated_at        TIMESTAMP(6),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    company_id        VARCHAR(255) NOT NULL UNIQUE,
    enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    -- DAILY, WEEKLY or MONTHLY
    frequency         VARCHAR(10)  NOT NULL DEFAULT 'DAILY' CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    -- Who is enrolled automatically when a verification is approved
    include_people    BOOLEAN      NOT NULL DEFAULT TRUE,
    include_businesses BOOLEAN     NOT NULL DEFAULT TRUE,
    include_parties   BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Someone being re-screened. Holds the details screening needs (personal data: removed with the verification's)
CREATE TABLE IF NOT EXISTS monitored_subjects
(
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at       TIMESTAMP(6),
    updated_at       TIMESTAMP(6),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    company_id       VARCHAR(255) NOT NULL,
    -- The approved verification they came from; party_id when they are a director or owner on it
    verification_id  VARCHAR(255) NOT NULL,
    party_id         VARCHAR(255),
    external_reference VARCHAR(255),
    -- PERSON or BUSINESS
    kind             VARCHAR(10)  NOT NULL CHECK (kind IN ('PERSON', 'BUSINESS')),
    display_name     VARCHAR(500) NOT NULL,
    first_name       VARCHAR(255),
    last_name        VARCHAR(255),
    date_of_birth    VARCHAR(20),
    business_name    VARCHAR(500),
    jurisdiction     VARCHAR(10),
    -- ACTIVE or STOPPED
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'STOPPED')),
    stopped_reason   VARCHAR(500),
    stopped_at       TIMESTAMP(6),
    -- The last result: verdict and the matched watchlist entries [{entityId, caption, score, datasets, topics}]
    verdict          VARCHAR(20),
    matches          JSONB,
    -- Entries a reviewer said are not this subject: {entityId: [topics when dismissed]}
    dismissed        JSONB,
    last_screened_at TIMESTAMP(6),
    next_screen_at   TIMESTAMP(6),
    failures         INTEGER      NOT NULL DEFAULT 0,
    open_alerts      INTEGER      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_monitored_subject ON monitored_subjects (verification_id, COALESCE(party_id, ''));
CREATE INDEX IF NOT EXISTS idx_monitored_subjects_due ON monitored_subjects (next_screen_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_monitored_subjects_company ON monitored_subjects (company_id, status);

-- A change found by re-screening, for the company to confirm or dismiss
CREATE TABLE IF NOT EXISTS monitoring_alerts
(
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at       TIMESTAMP(6),
    updated_at       TIMESTAMP(6),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    company_id       VARCHAR(255) NOT NULL,
    subject_id       VARCHAR(255) NOT NULL REFERENCES monitored_subjects (id) ON DELETE CASCADE,
    -- NEW_MATCH, MATCH_CHANGED (now sanctioned or PEP), MATCH_REMOVED (delisted)
    kind             VARCHAR(20)  NOT NULL CHECK (kind IN ('NEW_MATCH', 'MATCH_CHANGED', 'MATCH_REMOVED')),
    previous_verdict VARCHAR(20),
    verdict          VARCHAR(20),
    -- The watchlist entries this alert is about, and the topics they gained
    matches          JSONB        NOT NULL,
    -- OPEN, CONFIRMED (it is them), DISMISSED (not them), INFO (nothing to decide: a match went away)
    status           VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'CONFIRMED', 'DISMISSED', 'INFO')),
    resolved_by      VARCHAR(255),
    resolved_at      TIMESTAMP(6),
    resolution_note  VARCHAR(1000)
);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_company ON monitoring_alerts (company_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_subject ON monitoring_alerts (subject_id, created_at DESC);
