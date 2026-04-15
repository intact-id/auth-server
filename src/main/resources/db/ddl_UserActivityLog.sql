CREATE TABLE user_activity_logs (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    session_id_fk VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    company_id VARCHAR(255),
    activity_type VARCHAR(50) NOT NULL,
    http_method VARCHAR(16),
    path VARCHAR(500) NOT NULL,
    query_string TEXT,
    status_code INTEGER,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    ip_address VARCHAR(45),

    CONSTRAINT fk_user_activity_logs_session
        FOREIGN KEY (session_id_fk) REFERENCES user_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_activity_logs_company
        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_activity_logs_session_id ON user_activity_logs(session_id_fk);
CREATE INDEX idx_user_activity_logs_user_id ON user_activity_logs(user_id);
CREATE INDEX idx_user_activity_logs_company_id ON user_activity_logs(company_id);
CREATE INDEX idx_user_activity_logs_started_at ON user_activity_logs(started_at);
CREATE INDEX idx_user_activity_logs_http_method ON user_activity_logs(http_method);
CREATE INDEX idx_user_activity_logs_path ON user_activity_logs(path);
