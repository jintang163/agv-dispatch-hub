export const AGV_STATUS = {
  IDLE: { value: 0, label: '空闲', color: '#10b981', class: 'idle' },
  WORKING: { value: 1, label: '工作中', color: '#3b82f6', class: 'working' },
  CHARGING: { value: 2, label: '充电中', color: '#f59e0b', class: 'charging' },
  FAULT: { value: 3, label: '故障', color: '#ef4444', class: 'fault' },
  OFFLINE: { value: 4, label: '离线', color: '#6b7280', class: 'offline' },
  PAUSED: { value: 5, label: '暂停', color: '#8b5cf6', class: 'paused' }
}

export const TASK_STATUS = {
  PENDING: { value: 0, label: '待分配', color: '#f59e0b', class: 'pending' },
  ASSIGNED: { value: 1, label: '已分配', color: '#3b82f6', class: 'assigned' },
  EXECUTING: { value: 2, label: '执行中', color: '#10b981', class: 'executing' },
  COMPLETED: { value: 3, label: '已完成', color: '#6b7280', class: 'completed' },
  CANCELLED: { value: 4, label: '已取消', color: '#6b7280', class: 'cancelled' },
  ABNORMAL: { value: 5, label: '异常', color: '#ef4444', class: 'abnormal' }
}

export const TASK_PRIORITY = {
  HIGH: { value: 'HIGH', label: '高', color: '#dc2626', class: 'high' },
  MEDIUM: { value: 'MEDIUM', label: '中', color: '#d97706', class: 'medium' },
  LOW: { value: 'LOW', label: '低', color: '#059669', class: 'low' }
}

export const TASK_TYPE = {
  TRANSPORT: { value: 'TRANSPORT', label: '搬运' },
  PICKING: { value: 'PICKING', label: '拣选' },
  CHARGING: { value: 'CHARGING', label: '充电' },
  STANDBY: { value: 'STANDBY', label: '待命' }
}

export const ALARM_TYPE = {
  DEADLOCK: { value: 'deadlock', label: '死锁', level: 'critical' },
  LOW_BATTERY: { value: 'lowBattery', label: '低电量', level: 'warning' },
  PATH_BLOCKED: { value: 'pathBlocked', label: '路径阻塞', level: 'error' },
  CONFLICT: { value: 'conflict', label: '路径冲突', level: 'error' },
  OBSTACLE: { value: 'obstacle', label: '障碍物', level: 'warning' },
  SYSTEM: { value: 'system', label: '系统异常', level: 'critical' }
}

export const NODE_TYPE = {
  WORKSTATION: { value: 'workstation', label: '工作站', color: '#3b82f6' },
  STORAGE: { value: 'storage', label: '存储区', color: '#10b981' },
  CHARGING: { value: 'charging', label: '充电站', color: '#f59e0b' },
  INTERSECTION: { value: 'intersection', label: '路口', color: '#a855f7' },
  LOADING: { value: 'loading', label: '装卸区', color: '#eab308' }
}

export const ALARM_LEVEL = {
  CRITICAL: { value: 'critical', label: '严重', color: '#ef4444' },
  ERROR: { value: 'error', label: '错误', color: '#f59e0b' },
  WARNING: { value: 'warning', label: '警告', color: '#3b82f6' }
}

export const MAP_NODES = [
  { code: 'A01', x: 0, y: 0, type: 'workstation', name: '工作站1' },
  { code: 'A02', x: 5, y: 0, type: 'workstation', name: '工作站2' },
  { code: 'A03', x: 10, y: 0, type: 'workstation', name: '工作站3' },
  { code: 'A04', x: 15, y: 0, type: 'workstation', name: '工作站4' },
  { code: 'B01', x: 0, y: 5, type: 'storage', name: '存储区1' },
  { code: 'B02', x: 5, y: 5, type: 'storage', name: '存储区2' },
  { code: 'B03', x: 10, y: 5, type: 'storage', name: '存储区3' },
  { code: 'B04', x: 15, y: 5, type: 'storage', name: '存储区4' },
  { code: 'C01', x: 0, y: 10, type: 'intersection', name: '路口1' },
  { code: 'C02', x: 5, y: 10, type: 'intersection', name: '路口2' },
  { code: 'C03', x: 10, y: 10, type: 'intersection', name: '路口3' },
  { code: 'C04', x: 15, y: 10, type: 'intersection', name: '路口4' },
  { code: 'D01', x: 0, y: 15, type: 'charging', name: '充电站1' },
  { code: 'D02', x: 7.5, y: 15, type: 'charging', name: '充电站2' },
  { code: 'D03', x: 15, y: 15, type: 'charging', name: '充电站3' }
]

export const MAP_PATHS = [
  ['A01', 'A02'], ['A02', 'A03'], ['A03', 'A04'],
  ['A01', 'B01'], ['A02', 'B02'], ['A03', 'B03'], ['A04', 'B04'],
  ['B01', 'B02'], ['B02', 'B03'], ['B03', 'B04'],
  ['B01', 'C01'], ['B02', 'C02'], ['B03', 'C03'], ['B04', 'C04'],
  ['C01', 'C02'], ['C02', 'C03'], ['C03', 'C04'],
  ['C01', 'D01'], ['C02', 'D02'], ['C03', 'D02'], ['C04', 'D03'],
  ['D01', 'D02'], ['D02', 'D03'],
  ['A02', 'C02'], ['B02', 'D02'], ['C02', 'C03']
]
