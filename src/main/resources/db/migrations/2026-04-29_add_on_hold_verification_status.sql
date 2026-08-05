ALTER TABLE verification_requests
    DROP CONSTRAINT IF EXISTS verification_requests_status_check;

ALTER TABLE verification_requests
    ADD CONSTRAINT verification_requests_status_check
        CHECK (status IN (
            'SUBMITTED', 'PENDING', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'INACTIVE',
            'SUSPENDED', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVOKED', 'EXPIRED',
            'PROCESSING', 'DRAFT', 'INFO_REQUESTED', 'REJECTED', 'ARCHIVED', 'UNKNOWN',
            'CLOSED', 'ON_HOLD'
        ));
