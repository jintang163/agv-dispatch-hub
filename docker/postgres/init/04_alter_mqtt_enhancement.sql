-- MQTT模块增强 - 数据库变更脚本
-- 用于支持任务执行反馈、远程控制响应等新功能

-- 1. 为agv_task表添加progress字段（进度百分比）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS progress INTEGER;
COMMENT ON COLUMN agv_task.progress IS '进度百分比（AGV上报）';

-- 2. 为agv_task表添加execution_status字段（执行状态）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS execution_status INTEGER;
COMMENT ON COLUMN agv_task.execution_status IS '任务执行状态：1-待下发 2-已下发 3-执行中 4-节点到达 5-作业中 6-已暂停 7-已完成 8-已取消 9-执行超时 10-执行失败 11-重新调度';

-- 3. 为agv_task表添加paused_time字段（暂停时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS paused_time TIMESTAMP;
COMMENT ON COLUMN agv_task.paused_time IS '暂停时间';

-- 4. 为agv_task表添加dispatched_time字段（下发时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS dispatched_time TIMESTAMP;
COMMENT ON COLUMN agv_task.dispatched_time IS '任务下发时间';

-- 5. 为agv_task表添加last_node_arrival_time字段（最近节点到达时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS last_node_arrival_time TIMESTAMP;
COMMENT ON COLUMN agv_task.last_node_arrival_time IS '最近一次节点到达时间';

-- 6. 为agv_task表添加estimated_complete_time字段（预计完成时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS estimated_complete_time TIMESTAMP;
COMMENT ON COLUMN agv_task.estimated_complete_time IS '预计完成时间';

-- 7. 为agv_task表添加timeout_seconds字段（任务超时时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER;
COMMENT ON COLUMN agv_task.timeout_seconds IS '任务超时时间（秒）';

-- 8. 为agv_task表添加node_timeout_seconds字段（节点占用超时时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS node_timeout_seconds INTEGER;
COMMENT ON COLUMN agv_task.node_timeout_seconds IS '节点占用超时时间（秒）';

-- 9. 为agv_task表添加original_path字段（原始路径）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS original_path VARCHAR(1024);
COMMENT ON COLUMN agv_task.original_path IS '原始路径（用于绕行后对比）';

-- 10. 为agv_task表添加total_steps字段（总步骤数）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS total_steps INTEGER;
COMMENT ON COLUMN agv_task.total_steps IS '总步骤数';

-- 11. 为agv_task表添加total_distance字段（路径总长度）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS total_distance DOUBLE PRECISION;
COMMENT ON COLUMN agv_task.total_distance IS '路径总长度（米）';

-- 12. 为agv_task表添加estimated_time字段（预计行驶时间）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS estimated_time DOUBLE PRECISION;
COMMENT ON COLUMN agv_task.estimated_time IS '预计行驶时间（秒）';

-- 13. 为agv_task表添加current_node字段（当前所在节点）
ALTER TABLE agv_task ADD COLUMN IF NOT EXISTS current_node VARCHAR(32);
COMMENT ON COLUMN agv_task.current_node IS '当前所在节点编号';

-- 14. 创建索引
CREATE INDEX IF NOT EXISTS idx_task_execution_status ON agv_task(execution_status);

-- 15. 创建告警记录表
CREATE TABLE IF NOT EXISTS agv_alarm_record (
    id BIGSERIAL PRIMARY KEY,
    alarm_type INTEGER NOT NULL,
    alarm_level VARCHAR(16) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    agv_id VARCHAR(32),
    task_id VARCHAR(32),
    node_code VARCHAR(32),
    handled BOOLEAN DEFAULT FALSE,
    handle_result VARCHAR(512),
    handler VARCHAR(32),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    handle_time TIMESTAMP
);

-- 16. 创建告警表索引
CREATE INDEX IF NOT EXISTS idx_alarm_type ON agv_alarm_record(alarm_type);
CREATE INDEX IF NOT EXISTS idx_alarm_level ON agv_alarm_record(alarm_level);
CREATE INDEX IF NOT EXISTS idx_alarm_handled ON agv_alarm_record(handled);
CREATE INDEX IF NOT EXISTS idx_alarm_create_time ON agv_alarm_record(create_time);

COMMENT ON TABLE agv_alarm_record IS '告警记录表';
COMMENT ON COLUMN agv_alarm_record.alarm_type IS '告警类型：1-任务超时 2-节点占用超时 3-心跳超时 4-AGV故障 5-路径阻塞 6-冲突检测 7-死锁检测 8-低电量 9-任务失败 10-通信异常';
COMMENT ON COLUMN agv_alarm_record.alarm_level IS '告警级别：WARNING-警告 ERROR-错误';
COMMENT ON COLUMN agv_alarm_record.title IS '告警标题';
COMMENT ON COLUMN agv_alarm_record.description IS '告警详情描述';
COMMENT ON COLUMN agv_alarm_record.agv_id IS '关联的AGV ID';
COMMENT ON COLUMN agv_alarm_record.task_id IS '关联的任务ID';
COMMENT ON COLUMN agv_alarm_record.node_code IS '关联的节点编号';
COMMENT ON COLUMN agv_alarm_record.handled IS '是否已处理';
COMMENT ON COLUMN agv_alarm_record.handle_result IS '处理结果';
COMMENT ON COLUMN agv_alarm_record.handler IS '处理人';
COMMENT ON COLUMN agv_alarm_record.create_time IS '创建时间（告警产生时间）';
COMMENT ON COLUMN agv_alarm_record.handle_time IS '处理时间';
