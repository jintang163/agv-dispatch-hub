CREATE DATABASE IF NOT EXISTS agv_dispatch;

\c agv_dispatch;

CREATE TABLE IF NOT EXISTS agv_map_node (
    id VARCHAR(32) PRIMARY KEY,
    node_code VARCHAR(32) NOT NULL UNIQUE,
    node_name VARCHAR(64),
    x_coord DOUBLE PRECISION NOT NULL,
    y_coord DOUBLE PRECISION NOT NULL,
    node_type VARCHAR(32),
    is_charging_station BOOLEAN DEFAULT FALSE,
    is_transfer_station BOOLEAN DEFAULT FALSE,
    connected_nodes VARCHAR(128),
    remark VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agv_robot (
    id VARCHAR(32) PRIMARY KEY,
    agv_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64),
    status INTEGER NOT NULL,
    current_task_id VARCHAR(32),
    current_position VARCHAR(32),
    battery_level DOUBLE PRECISION,
    x_coord DOUBLE PRECISION,
    y_coord DOUBLE PRECISION,
    angle DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    model VARCHAR(128),
    max_load DOUBLE PRECISION,
    ip_address VARCHAR(128),
    last_heartbeat TIMESTAMP,
    fault_code VARCHAR(256),
    fault_message VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agv_status ON agv_robot(status);

CREATE TABLE IF NOT EXISTS agv_task (
    id VARCHAR(32) PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    task_type INTEGER NOT NULL,
    priority INTEGER NOT NULL,
    status INTEGER NOT NULL,
    agv_id VARCHAR(32),
    start_point VARCHAR(32) NOT NULL,
    end_point VARCHAR(32) NOT NULL,
    load_weight DOUBLE PRECISION,
    cargo_info VARCHAR(128),
    deadline TIMESTAMP,
    remark VARCHAR(512),
    assigned_time TIMESTAMP,
    start_time TIMESTAMP,
    completed_time TIMESTAMP,
    path VARCHAR(256),
    current_step INTEGER,
    wms_order_no VARCHAR(128),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_status ON agv_task(status);
CREATE INDEX IF NOT EXISTS idx_task_priority_deadline ON agv_task(priority, deadline);
CREATE INDEX IF NOT EXISTS idx_task_agv_id ON agv_task(agv_id);
CREATE INDEX IF NOT EXISTS idx_task_create_time ON agv_task(create_time);

CREATE TABLE IF NOT EXISTS agv_task_log (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(32) NOT NULL,
    agv_id VARCHAR(32),
    operation VARCHAR(64) NOT NULL,
    old_status VARCHAR(32),
    new_status VARCHAR(32),
    remark VARCHAR(512),
    operator VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_log_task_id ON agv_task_log(task_id);
CREATE INDEX IF NOT EXISTS idx_task_log_create_time ON agv_task_log(create_time);

CREATE TABLE IF NOT EXISTS agv_conflict_record (
    id BIGSERIAL PRIMARY KEY,
    agv_id1 VARCHAR(32),
    agv_id2 VARCHAR(32),
    conflict_type INTEGER NOT NULL,
    location VARCHAR(32),
    task_id1 VARCHAR(32),
    task_id2 VARCHAR(32),
    resolution VARCHAR(512),
    resolved BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conflict_create_time ON agv_conflict_record(create_time);
