-- Screening and CRA run through sactions-service. Safe to run more than once.

-- Each company's tenant in sactions-service, created on its first screening
CREATE TABLE IF NOT EXISTS screening_tenant
(
    company_id VARCHAR(255) NOT NULL PRIMARY KEY,
    tenant_id  VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now()
);

-- Results the runner now reports (conditions read these). Status stays as is: make a service AVAILABLE
-- through POST /api/v1/workflow/catalog/{code}/status once its billing service charge exists.
UPDATE workflow_service
SET outputs         = '[{"key": "screening.verdict", "type": "string", "values": ["NO_HIT", "POTENTIAL_MATCH", "CONFIRMED_MATCH"]},
                        {"key": "screening.hits", "type": "number"},
                        {"key": "screening.top_score", "type": "number"},
                        {"key": "screening.pep", "type": "boolean"},
                        {"key": "screening.adverse_media", "type": "boolean"}]',
    updated_at      = now()
WHERE code = 'SANCTIONS_SCREENING';

UPDATE workflow_service
SET outputs    = '[{"key": "risk.level", "type": "string", "values": ["LOW", "MEDIUM", "HIGH"]},
                   {"key": "risk.score", "type": "number"},
                   {"key": "risk.complete", "type": "boolean"},
                   {"key": "risk.override_applied", "type": "boolean"}]',
    updated_at = now()
WHERE code = 'CRA';
