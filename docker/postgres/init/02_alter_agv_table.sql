-- AGV表新增字段
ALTER TABLE agv_robot ADD COLUMN IF NOT EXISTS allowed_task_types VARCHAR(256);

-- 初始化现有AGV的允许任务类型（默认支持所有类型）
UPDATE agv_robot SET allowed_task_types = '1,2,3,4' WHERE allowed_task_types IS NULL;
