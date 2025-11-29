-- Permissions Table
CREATE TABLE permissions (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT
);

-- Indexes
CREATE INDEX idx_permission_name ON permissions(name);
CREATE INDEX idx_resource_action ON permissions(resource, action);

-- Comments
COMMENT ON TABLE permissions IS 'Granular permissions for authorization';
COMMENT ON COLUMN permissions.name IS 'Permission name format: resource:action (e.g., company:create)';
COMMENT ON COLUMN permissions.resource IS 'Resource type: company, application, user, role, kyc';
COMMENT ON COLUMN permissions.action IS 'Action: create, read, update, delete, approve, verify';
