-- Roles Table
CREATE TABLE roles (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

-- Index
CREATE INDEX idx_role_name ON roles(name);

-- Comments
COMMENT ON TABLE roles IS 'User roles for role-based access control';
COMMENT ON COLUMN roles.name IS 'Role name: SUPER_ADMIN, ADMIN, CHECKER, MAKER, VIEWER';
