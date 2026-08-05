CREATE TABLE IF NOT EXISTS backup_codes (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),

    user_id VARCHAR(255) NOT NULL,
    code_hash VARCHAR(255) NOT NULL UNIQUE,
    used_at TIMESTAMP,

    CONSTRAINT fk_backup_codes_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_backup_codes_user_id ON backup_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_backup_codes_code_hash ON backup_codes(code_hash);
