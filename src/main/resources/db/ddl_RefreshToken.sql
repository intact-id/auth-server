-- Refresh Tokens Table
CREATE TABLE refresh_tokens (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    user_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    CONSTRAINT fk_refresh_tokens_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_expires_at ON refresh_tokens(expires_at);

-- Comments
COMMENT ON TABLE refresh_tokens IS 'JWT refresh tokens for session management';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the refresh token';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Token revoked manually (logout)';
COMMENT ON COLUMN refresh_tokens.ip_address IS 'IP address where token was issued';
COMMENT ON COLUMN refresh_tokens.user_agent IS 'Browser/client user agent';
