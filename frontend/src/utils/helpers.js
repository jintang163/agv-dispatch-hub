import { AGV_STATUS, TASK_STATUS, TASK_PRIORITY, TASK_TYPE, ALARM_TYPE, ALARM_LEVEL, NODE_TYPE } from './constants'

export const getAgvStatusInfo = (status) => {
  if (typeof status === 'number') {
    return Object.values(AGV_STATUS).find(s => s.value === status) || AGV_STATUS.OFFLINE
  }
  return AGV_STATUS[status] || AGV_STATUS.OFFLINE
}

export const getAgvStatusLabel = (status) => getAgvStatusInfo(status).label
export const getAgvStatusColor = (status) => getAgvStatusInfo(status).color
export const getAgvStatusClass = (status) => getAgvStatusInfo(status).class

export const getTaskStatusInfo = (status) => {
  if (typeof status === 'number') {
    return Object.values(TASK_STATUS).find(s => s.value === status) || TASK_STATUS.PENDING
  }
  return TASK_STATUS[status] || TASK_STATUS.PENDING
}

export const getTaskStatusLabel = (status) => getTaskStatusInfo(status).label
export const getTaskStatusColor = (status) => getTaskStatusInfo(status).color
export const getTaskStatusClass = (status) => getTaskStatusInfo(status).class

export const getTaskPriorityInfo = (priority) => {
  return TASK_PRIORITY[priority] || TASK_PRIORITY.LOW
}

export const getTaskPriorityLabel = (priority) => getTaskPriorityInfo(priority).label
export const getTaskPriorityColor = (priority) => getTaskPriorityInfo(priority).color
export const getTaskPriorityClass = (priority) => getTaskPriorityInfo(priority).class

export const getTaskTypeLabel = (type) => {
  return TASK_TYPE[type]?.label || type
}

export const getAlarmTypeInfo = (type) => {
  return ALARM_TYPE[type] || { label: type, level: 'warning' }
}

export const getAlarmTypeLabel = (type) => getAlarmTypeInfo(type).label
export const getAlarmLevelInfo = (level) => {
  return ALARM_LEVEL[level?.toUpperCase?.()] || ALARM_LEVEL.WARNING
}

export const getAlarmLevelColor = (level) => getAlarmLevelInfo(level).color

export const getNodeTypeInfo = (type) => {
  return NODE_TYPE[type] || { label: type, color: '#6b7280' }
}

export const getNodeTypeColor = (type) => getNodeTypeInfo(type).color

export const getBatteryColor = (level) => {
  if (level >= 60) return '#10b981'
  if (level >= 30) return '#f59e0b'
  return '#ef4444'
}

export const formatDateTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

export const formatDuration = (ms) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  if (hours > 0) {
    return `${hours}小时${minutes % 60}分钟`
  }
  if (minutes > 0) {
    return `${minutes}分钟${seconds % 60}秒`
  }
  return `${seconds}秒`
}

export const getWaitTime = (createTime) => {
  const now = new Date()
  const created = new Date(createTime)
  return formatDuration(now - created)
}

export const normalizeAgvData = (agv) => {
  if (!agv) return null
  return {
    id: agv.id,
    agvNo: agv.agvNo,
    name: agv.name,
    status: agv.status,
    battery: agv.battery ?? agv.batteryLevel,
    currentPosition: agv.currentPosition,
    x: agv.x ?? agv.xCoord,
    y: agv.y ?? agv.yCoord,
    angle: agv.angle,
    speed: agv.speed,
    load: agv.load ?? agv.loadCapacity,
    currentTaskId: agv.currentTaskId,
    currentTask: agv.currentTask,
    lastHeartbeat: agv.lastHeartbeat
  }
}

export const normalizeTaskData = (task) => {
  if (!task) return null
  return {
    id: task.id,
    taskNo: task.taskNo,
    taskType: task.taskType,
    priority: task.priority,
    status: task.status,
    startPoint: task.startPoint,
    endPoint: task.endPoint,
    progress: task.progress,
    assignedAgv: task.assignedAgv || task.agvNo,
    createTime: task.createTime,
    startTime: task.startTime,
    endTime: task.endTime,
    deadline: task.deadline,
    currentPath: task.currentPath || [],
    pathIndex: task.pathIndex || 0
  }
}

export const normalizeAlarmData = (alarm) => {
  if (!alarm) return null
  return {
    id: alarm.id,
    type: alarm.type || alarm.alarmType,
    level: alarm.level || 'warning',
    title: alarm.title || alarm.message,
    message: alarm.message || alarm.description,
    agvIds: alarm.agvIds || alarm.agvNo ? [alarm.agvNo] : [],
    location: alarm.location || alarm.nodeCode,
    createTime: alarm.createTime || alarm.timestamp,
    handled: alarm.handled || false,
    handleTime: alarm.handleTime,
    handleResult: alarm.handleResult,
    handler: alarm.handler
  }
}

export const getNodePosition = (nodeCode, nodes) => {
  const node = nodes?.find(n => n.code === nodeCode)
  return node ? [node.x, node.y] : [0, 0]
}

export const generatePathLines = (paths, nodes) => {
  return paths.map(path => ({
    coords: [
      getNodePosition(path[0], nodes),
      getNodePosition(path[1], nodes)
    ],
    lineStyle: {
      color: 'rgba(147, 197, 253, 0.3)',
      width: 2
    }
  }))
}

export const generateTaskPathAnimation = (taskPath, nodes) => {
  if (!taskPath || taskPath.length < 2) return []
  const lines = []
  for (let i = 0; i < taskPath.length - 1; i++) {
    lines.push({
      coords: [
        getNodePosition(taskPath[i], nodes),
        getNodePosition(taskPath[i + 1], nodes)
      ],
      lineStyle: {
        color: '#fbbf24',
        width: 3
      }
    })
  }
  return lines
}

export const interpolatePosition = (start, end, progress) => {
  return [
    start[0] + (end[0] - start[0]) * progress,
    start[1] + (end[1] - start[1]) * progress
  ]
}

export const calculateDistance = (pos1, pos2) => {
  const dx = pos2[0] - pos1[0]
  const dy = pos2[1] - pos1[1]
  return Math.sqrt(dx * dx + dy * dy)
}
