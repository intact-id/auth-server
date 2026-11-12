-- Manual review record on verification_requests. Safe to run more than once.
-- Reviews used to overwrite risk_score (0.1 / 0.9) and failure_reason (reviewer notes) and kept no
-- reviewer; these columns hold the review separately so the automated evidence survives.

ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS review_reason       VARCHAR(500);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS review_requested_at TIMESTAMP(6);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS review_assigned_to  VARCHAR(255);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS review_assigned_at  TIMESTAMP(6);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS reviewed_by         VARCHAR(255);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS reviewed_at         TIMESTAMP(6);
ALTER TABLE verification_requests ADD COLUMN IF NOT EXISTS review_notes        TEXT;

-- Cases already waiting: count their wait from when they were submitted.
UPDATE verification_requests
SET review_requested_at = created_at
WHERE status = 'PENDING_APPROVAL' AND review_requested_at IS NULL;

-- The review queue filters on status and assignee.
CREATE INDEX IF NOT EXISTS idx_verification_requests_review_queue
    ON verification_requests (status, review_assigned_to);
