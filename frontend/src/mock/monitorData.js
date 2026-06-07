export const mapNodes = [
  { code: 'A01', x: 0, y: 0, type: 'workstation' },
  { code: 'A02', x: 5, y: 0, type: 'workstation' },
  { code: 'A03', x: 10, y: 0, type: 'workstation' },
  { code: 'A04', x: 15, y: 0, type: 'workstation' },
  { code: 'B01', x: 0, y: 5, type: 'storage' },
  { code: 'B02', x: 5, y: 5, type: 'storage' },
  { code: 'B03', x: 10, y: 5, type: 'storage' },
  { code: 'B04', x: 15, y: 5, type: 'storage' },
  { code: 'C01', x: 0, y: 10, type: 'charging' },
  { code: 'C02', x: 5, y: 10, type: 'intersection' },
  { code: 'C03', x: 10, y: 10, type: 'intersection' },
  { code: 'C04', x: 15, y: 10, type: 'charging' },
  { code: 'D01', x: 0, y: 15, type: 'loading' },
  { code: 'D02', x: 7.5, y: 15, type: 'unloading' },
  { code: 'D03', x: 15, y: 15, type: 'loading' }
]

export const mapPaths = [
  ['A01', 'A02'], ['A02', 'A03'], ['A03', 'A04'],
  ['B01', 'B02'], ['B02', 'B03'], ['B03', 'B04'],
  ['C01', 'C02'], ['C02', 'C03'], ['C03', 'C04'],
  ['D01', 'D02'], ['D02', 'D03'],
  ['A01', 'B01'], ['B01', 'C01'], ['C01', 'D01'],
  ['A02', 'B02'], ['B02', 'C02'], ['C02', 'D02'],
  ['A03', 'B03'], ['B03', 'C03'], ['C03', 'D03'],
  ['A04', 'B04'], ['B04', 'C04'], ['C04', 'D03']
]

export const mockAgvList = [
  {
    agvNo: 'AGV-001',
    name: '搬运车1号',
    status: 1,
    batteryLevel: 85,
    currentPosition: 'B02',
    xCoord: 5,
    yCoord: 5,
    currentTask: 'TASK-2024001',
    speed: 1.2,
    loadWeight: 500
  },
  {
    agvNo: 'AGV-002',
    name: '搬运车2号',
    status: 1,
    batteryLevel: 45,
    currentPosition: 'C02',
    xCoord: 5,
    yCoord: 10,
    currentTask: 'TASK-2024002',
    speed: 0.8,
    loadWeight: 300
  },
  {
    agvNo: 'AGV-003',
    name: '搬运车3号',
    status: 0,
    batteryLevel: 95,
    currentPosition: 'A01',
    xCoord: 0,
    yCoord: 0,
    currentTask: null,
    speed: 0,
    loadWeight: 0
  },
  {
    agvNo: 'AGV-004',
    name: '搬运车4号',
    status: 2,
    batteryLevel: 25,
    currentPosition: 'C01',
    xCoord: 0,
    yCoord: 10,
    currentTask: null,
    speed: 0,
    loadWeight: 0
  },
  {
    agvNo: 'AGV-005',
    name: '搬运车5号',
    status: 1,
    batteryLevel: 72,
    currentPosition: 'D02',
    xCoord: 7.5,
    yCoord: 15,
    currentTask: 'TASK-2024003',
    speed: 1.5,
    loadWeight: 800
  },
  {
    agvNo: 'AGV-006',
    name: '搬运车6号',
    status: 3,
    batteryLevel: 60,
    currentPosition: 'A03',
    xCoord: 10,
    yCoord: 0,
    currentTask: 'TASK-2024004',
    speed: 0,
    loadWeight: 450
  }
]

export const mockPendingTasks = [
  {
    id: 101,
    taskNo: 'TASK-2024101',
    taskType: 1,
    priority: 3,
    status: 0,
    startPoint: 'B01',
    endPoint: 'D02',
    estimatedTime: 180,
    createTime: '2024-01-15 10:30:00'
  },
  {
    id: 102,
    taskNo: 'TASK-2024102',
    taskType: 2,
    priority: 2,
    status: 0,
    startPoint: 'A02',
    endPoint: 'B03',
    estimatedTime: 120,
    createTime: '2024-01-15 10:32:00'
  },
  {
    id: 103,
    taskNo: 'TASK-2024103',
    taskType: 1,
    priority: 1,
    status: 0,
    startPoint: 'D01',
    endPoint: 'B04',
    estimatedTime: 200,
    createTime: '2024-01-15 10:35:00'
  },
  {
    id: 104,
    taskNo: 'TASK-2024104',
    taskType: 1,
    priority: 2,
    status: 0,
    startPoint: 'A04',
    endPoint: 'D03',
    estimatedTime: 150,
    createTime: '2024-01-15 10:38:00'
  }
]

export const mockExecutingTasks = [
  {
    id: 1,
    taskNo: 'TASK-2024001',
    taskType: 1,
    priority: 3,
    status: 2,
    startPoint: 'B01',
    endPoint: 'D02',
    assignedAgv: 'AGV-001',
    progress: 65,
    currentPath: ['B01', 'B02', 'C02', 'D02'],
    currentNodeIndex: 1,
    startTime: '2024-01-15 10:25:00',
    estimatedTime: 180,
    elapsedTime: 117
  },
  {
    id: 2,
    taskNo: 'TASK-2024002',
    taskType: 2,
    priority: 2,
    status: 2,
    startPoint: 'A01',
    endPoint: 'B04',
    assignedAgv: 'AGV-002',
    progress: 40,
    currentPath: ['A01', 'B01', 'B02', 'B03', 'B04'],
    currentNodeIndex: 2,
    startTime: '2024-01-15 10:20:00',
    estimatedTime: 240,
    elapsedTime: 96
  },
  {
    id: 3,
    taskNo: 'TASK-2024003',
    taskType: 1,
    priority: 1,
    status: 2,
    startPoint: 'D01',
    endPoint: 'A03',
    assignedAgv: 'AGV-005',
    progress: 80,
    currentPath: ['D01', 'C01', 'B01', 'A01', 'A02', 'A03'],
    currentNodeIndex: 4,
    startTime: '2024-01-15 10:15:00',
    estimatedTime: 300,
    elapsedTime: 240
  }
]

export const mockAlerts = [
  {
    id: 1,
    type: 'deadlock',
    level: 'critical',
    title: '死锁告警',
    message: 'AGV-004 与 AGV-006 在节点 C02-C03 路径发生死锁',
    agvNos: ['AGV-004', 'AGV-006'],
    location: 'C02-C03',
    timestamp: '2024-01-15 10:35:00',
    handled: false
  },
  {
    id: 2,
    type: 'lowBattery',
    level: 'warning',
    title: '低电量告警',
    message: 'AGV-004 电量低于30%，请及时充电',
    agvNos: ['AGV-004'],
    location: 'C01',
    timestamp: '2024-01-15 10:30:00',
    handled: false
  },
  {
    id: 3,
    type: 'pathBlocked',
    level: 'error',
    title: '路径阻塞',
    message: '节点 B03 路径被临时阻塞，请重新规划路径',
    agvNos: [],
    location: 'B03',
    timestamp: '2024-01-15 10:28:00',
    handled: false
  },
  {
    id: 4,
    type: 'lowBattery',
    level: 'warning',
    title: '低电量告警',
    message: 'AGV-002 电量低于50%，请注意安排充电',
    agvNos: ['AGV-002'],
    location: 'C02',
    timestamp: '2024-01-15 10:25:00',
    handled: true
  },
  {
    id: 5,
    type: 'conflict',
    level: 'warning',
    title: '路径冲突',
    message: 'AGV-001 与 AGV-005 在 D02 节点存在潜在路径冲突',
    agvNos: ['AGV-001', 'AGV-005'],
    location: 'D02',
    timestamp: '2024-01-15 10:20:00',
    handled: true
  }
]

export const mockHistoryStats = {
  todayCompleted: 45,
  totalCompleted: 12580,
  avgWaitTime: 2.5,
  avgExecutionTime: 4.8,
  totalMileage: 12568.5,
  todayMileage: 85.6,
  efficiencyTrend: [
    { hour: '00:00', completed: 2, waitTime: 3.2 },
    { hour: '02:00', completed: 1, waitTime: 2.8 },
    { hour: '04:00', completed: 3, waitTime: 2.1 },
    { hour: '06:00', completed: 5, waitTime: 2.5 },
    { hour: '08:00', completed: 8, waitTime: 3.0 },
    { hour: '10:00', completed: 12, waitTime: 2.8 },
    { hour: '12:00', completed: 6, waitTime: 1.8 },
    { hour: '14:00', completed: 10, waitTime: 2.2 },
    { hour: '16:00', completed: 15, waitTime: 2.6 },
    { hour: '18:00', completed: 12, waitTime: 2.4 },
    { hour: '20:00', completed: 8, waitTime: 2.0 },
    { hour: '22:00', completed: 5, waitTime: 2.3 }
  ],
  taskTypeDistribution: [
    { name: '搬运任务', value: 8560, percentage: 68 },
    { name: '拣选任务', value: 3200, percentage: 25 },
    { name: '充电任务', value: 520, percentage: 4 },
    { name: '其他任务', value: 300, percentage: 3 }
  ],
  recentCompletedTasks: [
    { taskNo: 'TASK-2023999', type: 1, startPoint: 'A01', endPoint: 'B03', completedTime: '2024-01-15 10:28:00', duration: 156, mileage: 12.5 },
    { taskNo: 'TASK-2023998', type: 2, startPoint: 'D02', endPoint: 'A04', completedTime: '2024-01-15 10:22:00', duration: 198, mileage: 18.2 },
    { taskNo: 'TASK-2023997', type: 1, startPoint: 'B02', endPoint: 'D01', completedTime: '2024-01-15 10:15:00', duration: 145, mileage: 15.8 },
    { taskNo: 'TASK-2023996', type: 1, startPoint: 'C04', endPoint: 'B01', completedTime: '2024-01-15 10:08:00', duration: 178, mileage: 14.2 },
    { taskNo: 'TASK-2023995', type: 3, startPoint: 'A03', endPoint: 'C01', completedTime: '2024-01-15 10:00:00', duration: 85, mileage: 8.5 }
  ]
}

export const mockCompletedTasks = mockHistoryStats.recentCompletedTasks.map((task, index) => ({
  id: 1000 + index,
  taskNo: task.taskNo,
  taskType: task.type,
  priority: 'MEDIUM',
  startPoint: task.startPoint,
  endPoint: task.endPoint,
  status: 3,
  assignedAgv: `AGV-00${(index % 6) + 1}`,
  progress: 100,
  currentPath: [task.startPoint, 'C02', task.endPoint],
  pathIndex: 2,
  createTime: new Date(Date.now() - (index + 1) * 300000).toISOString(),
  startTime: new Date(Date.now() - (index + 1) * 600000).toISOString(),
  endTime: new Date(Date.now() - index * 300000).toISOString(),
  duration: task.duration,
  mileage: task.mileage
}))

export function getRandomOffset() {
  return (Math.random() - 0.5) * 0.8
}

export function generateMockAgvPositions(agvList) {
  return agvList.map(agv => {
    if (agv.status === 1) {
      return {
        ...agv,
        xCoord: agv.xCoord + getRandomOffset(),
        yCoord: agv.yCoord + getRandomOffset()
      }
    }
    return agv
  })
}
