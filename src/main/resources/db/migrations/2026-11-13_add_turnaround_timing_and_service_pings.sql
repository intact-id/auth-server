-- Turnaround time (TAT) per stage and per check, and service health checks. Safe to run more than once.
-- Hibernate (ddl-auto: update) also creates these; this file records them.

-- When the checks last started; created_at → processing_started_at is time queued or held.
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP(6);

-- When each check's service call started and how long it took.
ALTER TABLE verification_steps ADD COLUMN IF NOT EXISTS started_at  TIMESTAMP(6);
ALTER TABLE verification_steps ADD COLUMN IF NOT EXISTS duration_ms BIGINT;

-- One row per service per minute; rows older than 30 days are deleted daily.
CREATE TABLE IF NOT EXISTS service_pings (
    id          BIGSERIAL PRIMARY KEY,
    service     VARCHAR(40)  NOT NULL,
    up          BOOLEAN      NOT NULL,
    latency_ms  BIGINT       NOT NULL,
    status_code INTEGER,
    error       VARCHAR(300),
    checked_at  TIMESTAMP(6) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_service_pings_service_checked ON service_pings (service, checked_at);

-- Stats filter verifications by submission time.
CREATE INDEX IF NOT EXISTS idx_verification_requests_created_at ON verification_requests (created_at);
