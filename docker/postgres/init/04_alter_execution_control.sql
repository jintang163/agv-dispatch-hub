-- 执行控制与告警模块数据库变更脚本
-- 执行时间: 2026-06-06

\c agv_dispatch;

-- 创建告警记录表
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

-- 创建告警记录表索引
CREATE INDEX IF NOT EXISTS idx_alarm_type ON agv_alarm_record(alarm_type);
CREATE INDEX IF NOT EXISTS idx_alarm_level ON agv_alarm_record(alarm_level);
CREATE INDEX IF NOT EXISTS idx_alarm_handled ON agv_alarm_record(handled);
CREATE INDEX IF NOT EXISTS idx_alarm_create_time ON agv_alarm_record(create_time);
CREATE INDEX IF NOT EXISTS idx_alarm_agv_id ON agv_alarm_record(agv_id);
CREATE INDEX IF NOT EXISTS idx_alarm_task_id ON agv_alarm_record(task_id);

-- 添加表和字段注释
COMMENT ON TABLE agv_alarm_record IS '告警记录表';
COMMENT ON COLUMN agv_alarm_record.id IS '主键ID';
COMMENT ON COLUMN agv_alarm_record.alarm_type IS '告警类型: 1-任务执行超时,2-节点占用超时,3-AGV心跳超时,4-AGV故障,5-路径阻塞,6-冲突检测,7-死锁检测,8-低电量,9-任务失败,10-通信异常';
COMMENT ON COLUMN agv_alarm_record.alarm_level IS '告警级别: WARNING-警告, ERROR-错误';
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

-- 修改任务表，增加执行控制相关字段
ALTER TABLE IF EXISTS agv_task
ADD COLUMN IF NOT EXISTS execution_status INTEGER,
ADD COLUMN IF NOT EXISTS current_node VARCHAR(32),
ADD COLUMN IF NOT EXISTS estimated_complete_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER,
ADD COLUMN IF NOT EXISTS node_timeout_seconds INTEGER,
ADD COLUMN IF NOT EXISTS dispatched_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS last_node_arrival_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS paused_time TIMESTAMP,
ADD COLUMN IF NOT EXISTS original_path VARCHAR(1024),
ADD COLUMN IF NOT EXISTS total_steps INTEGER,
ADD COLUMN IF NOT EXISTS total_distance DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS estimated_time DOUBLE PRECISION;

-- 为任务表新增字段添加注释
COMMENT ON COLUMN agv_task.execution_status IS '执行状态: 0-正常执行,1-暂停,2-阻塞,3-超时,4-异常';
COMMENT ON COLUMN agv_task.current_node IS '当前所在节点编号';
COMMENT ON COLUMN agv_task.estimated_complete_time IS '预计完成时间';
COMMENT ON COLUMN agv_task.timeout_seconds IS '任务整体超时时间（秒）';
COMMENT ON COLUMN agv_task.node_timeout_seconds IS '单节点超时时间（秒）';
COMMENT ON COLUMN agv_task.dispatched_time IS '调度下发时间';
COMMENT ON COLUMN agv_task.last_node_arrival_time IS '最近节点到达时间';
COMMENT ON COLUMN agv_task.paused_time IS '暂停时间';
COMMENT ON COLUMN agv_task.original_path IS '原始路径节点序列';
COMMENT ON COLUMN agv_task.total_steps IS '总步数';
COMMENT ON COLUMN agv_task.total_distance IS '总距离（米）';
COMMENT ON COLUMN agv_task.estimated_time IS '预计执行时间（秒）';

-- 为任务表增加新索引
CREATE INDEX IF NOT EXISTS idx_execution_status ON agv_task(execution_status);
CREATE INDEX IF NOT EXISTS idx_start_time ON agv_task(start_time);

-- 初始化现有数据的默认值
UPDATE agv_task SET execution_status = 0 WHERE execution_status IS NULL;
UPDATE agv_task SET timeout_seconds = 3600 WHERE timeout_seconds IS NULL;
UPDATE agv_task SET node_timeout_seconds = 120 WHERE node_timeout_seconds IS NULL;
