-- Each server (develop, production) is one environment with its own database, so nothing records an
-- environment any more and a company has one API key pair.

-- 1. One key pair per company. Keep the pair the company actually used most recently, so the integration
--    that is running today keeps working; a pair never used counts as older than one that was.
--    (The base api_key_* columns used to mirror the production pair.)
WITH chosen AS (
    SELECT id,
           dev_api_key_hash IS NOT NULL
               AND (COALESCE(prod_api_key_hash, api_key_hash) IS NULL
                    OR COALESCE(dev_last_used_at, '-infinity') > COALESCE(prod_last_used_at, last_used_at, '-infinity'))
               AS use_dev
    FROM company_credentials
)
UPDATE company_credentials c
SET api_key_hash         = CASE WHEN chosen.use_dev THEN c.dev_api_key_hash ELSE COALESCE(c.prod_api_key_hash, c.api_key_hash) END,
    api_secret_hash      = CASE WHEN chosen.use_dev THEN c.dev_api_secret_hash ELSE COALESCE(c.prod_api_secret_hash, c.api_secret_hash) END,
    status               = CASE WHEN chosen.use_dev THEN COALESCE(c.dev_status, c.status) ELSE COALESCE(c.prod_status, c.status) END,
    expires_at           = CASE WHEN chosen.use_dev THEN COALESCE(c.dev_expires_at, c.expires_at) ELSE COALESCE(c.prod_expires_at, c.expires_at) END,
    last_used_at         = GREATEST(c.last_used_at, c.dev_last_used_at, c.prod_last_used_at),
    rate_limit_per_hour  = CASE WHEN chosen.use_dev THEN COALESCE(c.dev_rate_limit_per_hour, c.rate_limit_per_hour) ELSE COALESCE(c.prod_rate_limit_per_hour, c.rate_limit_per_hour) END,
    rate_limit_per_day   = CASE WHEN chosen.use_dev THEN COALESCE(c.dev_rate_limit_per_day, c.rate_limit_per_day) ELSE COALESCE(c.prod_rate_limit_per_day, c.rate_limit_per_day) END,
    rate_limit_per_month = CASE WHEN chosen.use_dev THEN COALESCE(c.dev_rate_limit_per_month, c.rate_limit_per_month) ELSE COALESCE(c.prod_rate_limit_per_month, c.rate_limit_per_month) END
FROM chosen
WHERE chosen.id = c.id;

ALTER TABLE company_credentials
    DROP COLUMN IF EXISTS dev_api_key_hash,
    DROP COLUMN IF EXISTS dev_api_secret_hash,
    DROP COLUMN IF EXISTS dev_expires_at,
    DROP COLUMN IF EXISTS dev_last_used_at,
    DROP COLUMN IF EXISTS dev_rate_limit_per_day,
    DROP COLUMN IF EXISTS dev_rate_limit_per_hour,
    DROP COLUMN IF EXISTS dev_rate_limit_per_month,
    DROP COLUMN IF EXISTS dev_status,
    DROP COLUMN IF EXISTS dev_total_failed_requests,
    DROP COLUMN IF EXISTS dev_total_requests,
    DROP COLUMN IF EXISTS dev_total_successful_requests,
    DROP COLUMN IF EXISTS prod_api_key_hash,
    DROP COLUMN IF EXISTS prod_api_secret_hash,
    DROP COLUMN IF EXISTS prod_expires_at,
    DROP COLUMN IF EXISTS prod_last_used_at,
    DROP COLUMN IF EXISTS prod_rate_limit_per_day,
    DROP COLUMN IF EXISTS prod_rate_limit_per_hour,
    DROP COLUMN IF EXISTS prod_rate_limit_per_month,
    DROP COLUMN IF EXISTS prod_status,
    DROP COLUMN IF EXISTS prod_total_failed_requests,
    DROP COLUMN IF EXISTS prod_total_requests,
    DROP COLUMN IF EXISTS prod_total_successful_requests;

-- 2. Verifications and widget drafts no longer record an environment
ALTER TABLE verification_requests DROP COLUMN IF EXISTS environment;
ALTER TABLE kyc_drafts DROP COLUMN IF EXISTS environment;

-- 3. Publishing makes a version live: bring workflows whose newest version was never made live up to date
UPDATE workflow_definition
SET live_version = latest_version
WHERE company_id IS NOT NULL
  AND latest_version > 0
  AND (live_version IS NULL OR live_version < latest_version);
