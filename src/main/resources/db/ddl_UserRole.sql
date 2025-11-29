-- User-Role Mapping Table
CREATE TABLE user_roles (
    user_id VARCHAR(255) NOT NULL,
    role_id VARCHAR(255) NOT NULL,
    
    PRIMARY KEY (user_id, role_id),
    
    CONSTRAINT fk_user_roles_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_user_roles_role 
        FOREIGN KEY (role_id) 
        REFERENCES roles(id) 
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

-- Comments
COMMENT ON TABLE user_roles IS 'Many-to-many mapping between users and roles';
