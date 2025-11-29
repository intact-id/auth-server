-- Role-Permission Mapping Table
CREATE TABLE role_permissions (
    role_id VARCHAR(255) NOT NULL,
    permission_id VARCHAR(255) NOT NULL,
    
    PRIMARY KEY (role_id, permission_id),
    
    CONSTRAINT fk_role_permissions_role 
        FOREIGN KEY (role_id) 
        REFERENCES roles(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_role_permissions_permission 
        FOREIGN KEY (permission_id) 
        REFERENCES permissions(id) 
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);

-- Comments
COMMENT ON TABLE role_permissions IS 'Many-to-many mapping between roles and permissions';
