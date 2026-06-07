-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    email VARCHAR(128),
    role INTEGER NOT NULL DEFAULT 3,
    status INTEGER NOT NULL DEFAULT 1,
    avatar VARCHAR(256),
    remark VARCHAR(512),
    last_login_ip VARCHAR(64),
    last_login_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_sys_user_role ON sys_user(role);
CREATE INDEX IF NOT EXISTS idx_sys_user_status ON sys_user(status);

-- 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGSERIAL PRIMARY KEY,
    operation_type INTEGER NOT NULL,
    operator VARCHAR(64) NOT NULL,
    operator_name VARCHAR(64),
    operation_ip VARCHAR(32),
    operation_detail VARCHAR(512),
    task_id VARCHAR(64),
    task_no VARCHAR(64),
    agv_id VARCHAR(64),
    agv_no VARCHAR(64),
    before_data VARCHAR(1024),
    after_data VARCHAR(1024),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    execute_time BIGINT
);

CREATE INDEX IF NOT EXISTS idx_operation_type ON operation_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_operator ON operation_log(operator);
CREATE INDEX IF NOT EXISTS idx_create_time ON operation_log(create_time);
CREATE INDEX IF NOT EXISTS idx_task_id ON operation_log(task_id);
CREATE INDEX IF NOT EXISTS idx_agv_id ON operation_log(agv_id);

-- 初始化默认用户
INSERT INTO sys_user (username, password, real_name, phone, email, role, status, remark)
VALUES ('admin', 'admin123', '系统管理员', '13800138000', 'admin@example.com', 1, 1, '系统默认管理员账号')
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_user (username, password, real_name, phone, email, role, status, remark)
VALUES ('dispatcher', 'dispatcher123', '调度员张三', '13800138001', 'dispatcher@example.com', 2, 1, '调度员账号')
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_user (username, password, real_name, phone, email, role, status, remark)
VALUES ('viewer', 'viewer123', '查看员李四', '13800138002', 'viewer@example.com', 3, 1, '只读查看账号')
ON CONFLICT (username) DO NOTHING;
