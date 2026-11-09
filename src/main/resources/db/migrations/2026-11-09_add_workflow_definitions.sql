-- Workflows merchants build from the service catalog, their published versions, and two starter templates
-- (company_id NULL). Safe to run more than once.
CREATE TABLE IF NOT EXISTS workflow_definition
(
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    created_at       TIMESTAMP(6),
    updated_at       TIMESTAMP(6),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    company_id       VARCHAR(255),
    workflow_key     VARCHAR(100) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    description      TEXT,
    subject_type     VARCHAR(20)  NOT NULL CHECK (subject_type IN ('INDIVIDUAL', 'BUSINESS')),
    draft_graph      JSONB        NOT NULL,
    draft_updated_at TIMESTAMP(6),
    latest_version   INTEGER      NOT NULL DEFAULT 0,
    live_version     INTEGER,
    archived         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_workflow_definition_company_key UNIQUE (company_id, workflow_key)
);
-- Template keys are unique too (NULL company_id would otherwise allow duplicates)
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_definition_template_key ON workflow_definition (workflow_key) WHERE company_id IS NULL;

CREATE TABLE IF NOT EXISTS workflow_version
(
    id             VARCHAR(255)  NOT NULL PRIMARY KEY,
    created_at     TIMESTAMP(6),
    updated_at     TIMESTAMP(6),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    definition_id  VARCHAR(255)  NOT NULL REFERENCES workflow_definition (id),
    version_number INTEGER       NOT NULL,
    graph          JSONB         NOT NULL,
    services       JSONB         NOT NULL,
    price          NUMERIC(19, 4),
    currency       VARCHAR(3),
    notes          TEXT,
    published_by   VARCHAR(255),
    published_at   TIMESTAMP(6)  NOT NULL,
    promoted_at    TIMESTAMP(6),
    CONSTRAINT uq_workflow_version_number UNIQUE (definition_id, version_number)
);

INSERT INTO workflow_definition (id, created_at, updated_at, company_id, workflow_key, name, description, subject_type,
                                 draft_graph, draft_updated_at)
SELECT gen_random_uuid()::text, now(), now(), NULL, 'standard-kyc', 'Standard KYC',
       'ID document, data cross-check, liveness and face match; low match scores go to manual review.', 'INDIVIDUAL',
       '{"nodes": [
          {"id": "start", "type": "START", "label": "Start", "position": {"x": 40, "y": 200}},
          {"id": "doc", "type": "SERVICE", "label": "ID document", "service": "DOCUMENT_CHECK", "position": {"x": 240, "y": 200}},
          {"id": "crosscheck", "type": "SERVICE", "label": "Data cross-check", "service": "DATA_CROSSCHECK", "position": {"x": 460, "y": 200}},
          {"id": "liveness", "type": "SERVICE", "label": "Selfie and liveness", "service": "LIVENESS", "position": {"x": 680, "y": 200}},
          {"id": "face", "type": "SERVICE", "label": "Face match", "service": "FACE_MATCH", "position": {"x": 900, "y": 200}},
          {"id": "score", "type": "CONDITION", "label": "Match strong enough?", "branches": [
             {"label": "Low match score", "when": {"field": "face.match_score", "op": "lt", "value": 0.75}}],
           "position": {"x": 1120, "y": 200}},
          {"id": "review", "type": "MANUAL_REVIEW", "label": "Manual review", "position": {"x": 1340, "y": 80}},
          {"id": "approved", "type": "END", "label": "Approved", "outcome": "APPROVED", "position": {"x": 1340, "y": 260}},
          {"id": "rejected", "type": "END", "label": "Rejected", "outcome": "REJECTED", "position": {"x": 900, "y": 420}}],
        "edges": [
          {"from": "start", "port": "next", "to": "doc"},
          {"from": "doc", "port": "passed", "to": "crosscheck"},
          {"from": "doc", "port": "failed", "to": "rejected"},
          {"from": "crosscheck", "port": "passed", "to": "liveness"},
          {"from": "crosscheck", "port": "failed", "to": "rejected"},
          {"from": "liveness", "port": "passed", "to": "face"},
          {"from": "liveness", "port": "failed", "to": "rejected"},
          {"from": "face", "port": "passed", "to": "score"},
          {"from": "face", "port": "failed", "to": "rejected"},
          {"from": "score", "port": "branch:0", "to": "review"},
          {"from": "score", "port": "else", "to": "approved"}]}'::jsonb,
       now()
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition WHERE company_id IS NULL AND workflow_key = 'standard-kyc');

INSERT INTO workflow_definition (id, created_at, updated_at, company_id, workflow_key, name, description, subject_type,
                                 draft_graph, draft_updated_at)
SELECT gen_random_uuid()::text, now(), now(), NULL, 'id-check', 'ID check only',
       'Capture and read the ID document; nothing else.', 'INDIVIDUAL',
       '{"nodes": [
          {"id": "start", "type": "START", "label": "Start", "position": {"x": 40, "y": 200}},
          {"id": "doc", "type": "SERVICE", "label": "ID document", "service": "DOCUMENT_CHECK", "position": {"x": 260, "y": 200}},
          {"id": "approved", "type": "END", "label": "Approved", "outcome": "APPROVED", "position": {"x": 500, "y": 140}},
          {"id": "rejected", "type": "END", "label": "Rejected", "outcome": "REJECTED", "position": {"x": 500, "y": 280}}],
        "edges": [
          {"from": "start", "port": "next", "to": "doc"},
          {"from": "doc", "port": "passed", "to": "approved"},
          {"from": "doc", "port": "failed", "to": "rejected"}]}'::jsonb,
       now()
WHERE NOT EXISTS (SELECT 1 FROM workflow_definition WHERE company_id IS NULL AND workflow_key = 'id-check');
