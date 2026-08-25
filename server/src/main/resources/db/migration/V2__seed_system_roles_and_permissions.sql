INSERT INTO sys_role (code, name, description, data_scope, system_role, sort_order)
VALUES
    ('SUPER_ADMIN', 'Super Administrator', 'Full platform access', 'ALL', TRUE, 10),
    ('ORG_ADMIN', 'Organization Administrator', 'Organization-wide administration', 'ALL', TRUE, 20),
    ('CAMPUS_ADMIN', 'Campus Administrator', 'Administration for assigned campuses', 'ASSIGNED_CAMPUSES', TRUE, 30),
    ('FINANCE', 'Finance', 'Finance operations for assigned campuses', 'ASSIGNED_CAMPUSES', TRUE, 40),
    ('ACADEMIC', 'Academic', 'Academic operations for assigned campuses', 'ASSIGNED_CAMPUSES', TRUE, 50),
    ('TEACHER', 'Teacher', 'Teaching operations for assigned campuses', 'ASSIGNED_CAMPUSES', TRUE, 60),
    ('PARENT', 'Parent', 'Access to the parent account and linked learners', 'SELF', TRUE, 70),
    ('STUDENT', 'Student', 'Access to the student account', 'SELF', TRUE, 80);

INSERT INTO sys_permission (code, name, resource, action_code, sort_order)
VALUES
    ('platform.manage', 'Manage platform settings', 'platform', 'manage', 10),
    ('campus.read', 'View campuses', 'campus', 'read', 20),
    ('campus.manage', 'Manage campuses', 'campus', 'manage', 30),
    ('user.read', 'View users', 'user', 'read', 40),
    ('user.manage', 'Manage users', 'user', 'manage', 50),
    ('role.read', 'View roles', 'role', 'read', 60),
    ('role.manage', 'Manage roles', 'role', 'manage', 70),
    ('finance.read', 'View finance data', 'finance', 'read', 80),
    ('finance.manage', 'Manage finance data', 'finance', 'manage', 90),
    ('academic.read', 'View academic data', 'academic', 'read', 100),
    ('academic.manage', 'Manage academic data', 'academic', 'manage', 110),
    ('teaching.manage', 'Manage teaching activities', 'teaching', 'manage', 120),
    ('family.self', 'Access linked family data', 'family', 'self', 130),
    ('student.self', 'Access own student data', 'student', 'self', 140),
    ('audit.read', 'View audit records', 'audit', 'read', 150);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
CROSS JOIN sys_permission permission
WHERE role.code = 'SUPER_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
CROSS JOIN sys_permission permission
WHERE role.code = 'ORG_ADMIN'
  AND permission.code <> 'platform.manage';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'campus.read', 'campus.manage', 'user.read', 'user.manage',
    'academic.read', 'academic.manage', 'teaching.manage', 'audit.read'
)
WHERE role.code = 'CAMPUS_ADMIN';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'campus.read', 'user.read', 'finance.read', 'finance.manage'
)
WHERE role.code = 'FINANCE';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'campus.read', 'user.read', 'academic.read', 'academic.manage', 'teaching.manage'
)
WHERE role.code = 'ACADEMIC';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code IN (
    'campus.read', 'academic.read', 'teaching.manage'
)
WHERE role.code = 'TEACHER';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'family.self'
WHERE role.code = 'PARENT';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.code = 'student.self'
WHERE role.code = 'STUDENT';
