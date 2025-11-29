-- Webhook Deliveries Table
CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id VARCHAR(255) PRIMARY KEY,
    company_id VARCHAR(255) NOT NULL,
    verification_id VARCHAR(255),
    webhook_url VARCHAR(500) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    http_status_code INTEGER,
    response_body TEXT,
    retry_count INTEGER DEFAULT 0 NOT NULL,
    next_retry_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    delivered_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_webhook_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_webhook_company ON webhook_deliveries(company_id);
CREATE INDEX IF NOT EXISTS idx_webhook_status ON webhook_deliveries(status);
CREATE INDEX IF NOT EXISTS idx_webhook_next_retry ON webhook_deliveries(next_retry_at);
CREATE INDEX IF NOT EXISTS idx_webhook_verification ON webhook_deliveries(verification_id);
CREATE INDEX IF NOT EXISTS idx_webhook_event_type ON webhook_deliveries(event_type);

-- Comments
COMMENT ON TABLE webhook_deliveries IS 'Tracks webhook delivery attempts and their status';
COMMENT ON COLUMN webhook_deliveries.event_type IS 'Type of event (e.g., verification.completed, verification.failed)';
COMMENT ON COLUMN webhook_deliveries.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN webhook_deliveries.next_retry_at IS 'Scheduled time for next retry attempt';
COMMENT ON COLUMN webhook_deliveries.status IS 'Delivery status: PENDING, DELIVERED, FAILED, RETRYING';
