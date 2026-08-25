CREATE TABLE org_campus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    legal_name VARCHAR(160),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/London',
    country_code CHAR(2) NOT NULL DEFAULT 'GB',
    address_line_1 VARCHAR(160),
    address_line_2 VARCHAR(160),
    city VARCHAR(80),
    postal_code VARCHAR(20),
    contact_email VARCHAR(160),
    contact_phone VARCHAR(32),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_org_campus_code UNIQUE (code),
    CONSTRAINT chk_org_campus_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(160),
    phone VARCHAR(32),
    preferred_language VARCHAR(16) NOT NULL DEFAULT 'en-GB',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/London',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6),
    last_login_at TIMESTAMP(6),
    password_changed_at TIMESTAMP(6),
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sys_user_username UNIQUE (username),
    CONSTRAINT uq_sys_user_email UNIQUE (email),
    CONSTRAINT uq_sys_user_phone UNIQUE (phone),
    CONSTRAINT chk_sys_user_status CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    data_scope VARCHAR(32) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sys_role_code UNIQUE (code),
    CONSTRAINT chk_sys_role_data_scope CHECK (data_scope IN ('ALL', 'ASSIGNED_CAMPUSES', 'SELF')),
    CONSTRAINT chk_sys_role_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(120) NOT NULL,
    permission_type VARCHAR(16) NOT NULL DEFAULT 'ACTION',
    resource VARCHAR(100) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sys_permission_code UNIQUE (code),
    CONSTRAINT fk_sys_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission (id),
    CONSTRAINT chk_sys_permission_type CHECK (permission_type IN ('MENU', 'API', 'ACTION')),
    CONSTRAINT chk_sys_permission_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_by BIGINT,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_by BIGINT,
    granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE
);

CREATE TABLE sys_user_campus (
    user_id BIGINT NOT NULL,
    campus_id BIGINT NOT NULL,
    primary_campus BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_by BIGINT,
    assigned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, campus_id),
    CONSTRAINT fk_sys_user_campus_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_campus_campus FOREIGN KEY (campus_id) REFERENCES org_campus (id) ON DELETE CASCADE
);

CREATE TABLE sys_login_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username_attempted VARCHAR(160) NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(120),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    request_id VARCHAR(64),
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sys_login_audit_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_sys_login_audit_event CHECK (event_type IN ('LOGIN', 'LOGOUT', 'TOKEN_REFRESH'))
);

CREATE TABLE sys_operation_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    module VARCHAR(64) NOT NULL,
    action_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(100),
    request_id VARCHAR(64),
    http_method VARCHAR(12),
    request_path VARCHAR(255),
    ip_address VARCHAR(45),
    result_status VARCHAR(16) NOT NULL,
    duration_ms BIGINT,
    detail JSON,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sys_operation_audit_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_sys_operation_audit_result CHECK (result_status IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_org_campus_status ON org_campus (status);
CREATE INDEX idx_sys_user_status ON sys_user (status);
CREATE INDEX idx_sys_role_status ON sys_role (status);
CREATE INDEX idx_sys_permission_parent ON sys_permission (parent_id);
CREATE INDEX idx_sys_permission_resource_action ON sys_permission (resource, action_code);
CREATE INDEX idx_sys_user_role_role ON sys_user_role (role_id, user_id);
CREATE INDEX idx_sys_role_permission_permission ON sys_role_permission (permission_id, role_id);
CREATE INDEX idx_sys_user_campus_campus ON sys_user_campus (campus_id, user_id);
CREATE INDEX idx_sys_login_audit_user_time ON sys_login_audit (user_id, occurred_at);
CREATE INDEX idx_sys_login_audit_username_time ON sys_login_audit (username_attempted, occurred_at);
CREATE INDEX idx_sys_login_audit_request ON sys_login_audit (request_id);
CREATE INDEX idx_sys_operation_audit_user_time ON sys_operation_audit (user_id, occurred_at);
CREATE INDEX idx_sys_operation_audit_resource ON sys_operation_audit (resource_type, resource_id);
CREATE INDEX idx_sys_operation_audit_request ON sys_operation_audit (request_id);
CREATE INDEX idx_sys_operation_audit_time ON sys_operation_audit (occurred_at);
