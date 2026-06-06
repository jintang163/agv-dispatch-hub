-- 路径规划与冲突解决模块数据库变更脚本
-- 执行时间: 2026-06-06

\c agv_dispatch;

-- 修改地图节点表，增加关键节点标识
ALTER TABLE IF EXISTS agv_map_node
ADD COLUMN IF NOT EXISTS is_intersection BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS is_passage BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS is_critical_point BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS passage_capacity INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS speed_limit DOUBLE PRECISION;

-- 创建死锁记录表
CREATE TABLE IF NOT EXISTS agv_deadlock_record (
    id BIGSERIAL PRIMARY KEY,
    wait_chain VARCHAR(1024) NOT NULL,
    agv_count INTEGER NOT NULL,
    selected_agv_id VARCHAR(32),
    resolution_strategy INTEGER,
    resolution_detail VARCHAR(1024),
    resolved BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deadlock_create_time ON agv_deadlock_record(create_time);
CREATE INDEX IF NOT EXISTS idx_deadlock_resolved ON agv_deadlock_record(resolved);

COMMENT ON TABLE agv_deadlock_record IS '死锁记录表';
COMMENT ON COLUMN agv_deadlock_record.wait_chain IS '等待链JSON数组';
COMMENT ON COLUMN agv_deadlock_record.agv_count IS '涉及AGV数量';
COMMENT ON COLUMN agv_deadlock_record.selected_agv_id IS '选择的牺牲AGV ID';
COMMENT ON COLUMN agv_deadlock_record.resolution_strategy IS '解决策略: 1-等待,2-绕行,3-让行,4-重分配,5-死锁恢复';
COMMENT ON COLUMN agv_deadlock_record.resolution_detail IS '解决详情';
COMMENT ON COLUMN agv_deadlock_record.resolved IS '是否已解决';
COMMENT ON COLUMN agv_deadlock_record.create_time IS '创建时间';
COMMENT ON COLUMN agv_deadlock_record.resolved_time IS '解决时间';

-- 为冲突记录表增加解决策略字段
ALTER TABLE IF EXISTS agv_conflict_record
ADD COLUMN IF NOT EXISTS resolution_strategy INTEGER;

COMMENT ON COLUMN agv_conflict_record.resolution_strategy IS '解决策略: 1-等待,2-绕行,3-让行,4-重分配,5-死锁恢复';

-- 初始化示例地图节点数据（标记路口和关键节点）
UPDATE agv_map_node SET is_intersection = TRUE, is_critical_point = TRUE WHERE node_type = 'INTERSECTION';
UPDATE agv_map_node SET is_passage = TRUE WHERE node_type = 'PASSAGE';
UPDATE agv_map_node SET is_critical_point = TRUE WHERE is_charging_station = TRUE OR is_transfer_station = TRUE;

-- 设置默认限速
UPDATE agv_map_node SET speed_limit = 1.0 WHERE speed_limit IS NULL;
UPDATE agv_map_node SET speed_limit = 0.5 WHERE is_intersection = TRUE;
