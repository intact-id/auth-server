CREATE TABLE user_sessions (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    user_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255),
    refresh_token_id VARCHAR(255),
    session_id VARCHAR(255) UNIQUE NOT NULL,
    login_at TIMESTAMP NOT NULL,
    logout_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,

    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_sessions_company
        FOREIGN KEY (company_id) REFERENCES company_tbl(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_sessions_refresh_token
        FOREIGN KEY (refresh_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_company_id ON user_sessions(company_id);
CREATE INDEX idx_user_sessions_login_at ON user_sessions(login_at);
CREATE INDEX idx_user_sessions_status ON user_sessions(status);
