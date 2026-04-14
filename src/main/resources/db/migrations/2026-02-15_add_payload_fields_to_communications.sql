-- Add payload_fields column to company_communications
-- Stores per-company configuration for which fields are included in outgoing webhook payloads.
-- Keys: includeIds, includeTier, includeTimestamps, includeResults, includeRawScores, includeApplicant
ALTER TABLE company_communications
    ADD COLUMN IF NOT EXISTS payload_fields JSONB;
