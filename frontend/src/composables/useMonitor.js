import { ref, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { agvApi, taskApi, dispatchApi, websocketService } from '@/api'
import {
  normalizeAgvData, normalizeTaskData, normalizeAlarmData,
  getNodePosition, interpolatePosition
} from '@/utils/helpers'
import { MAP_NODES } from '@/utils/constants'
import {
  mockAgvList,
  mockPendingTasks,
  mockExecutingTasks,
  mockAlerts,
  mockHistoryStats,
  mockCompletedTasks
} from '@/mock/monitorData'

export function useMonitor() {
  const agvList = ref([])
  const pendingTasks = ref([])
  const executingTasks = ref([])
  const alarms = ref([])
  const historyStats = ref(null)
  const completedTasks = ref([])
  const loading = ref(false)
  const error = ref(null)

  let stompClient = null
  let dataRefreshTimer = null
  let agvAnimationTimer = null
  let taskProgressTimer = null

  const agvAnimationState = ref(new Map())

  const loadAgvList = async () => {
    try {
      const data = await agvApi.list()
      agvList.value = (data || []).map(normalizeAgvData)
      initAgvAnimationState()
    } catch (e) {
      console.error('加载AGV列表失败，使用Mock数据', e)
      agvList.value = mockAgvList.map(normalizeAgvData)
      initAgvAnimationState()
    }
  }

  const loadTaskQueue = async () => {
    try {
      const [queue, executing] = await Promise.all([
        taskApi.getQueue(),
        dispatchApi.getExecutingTasks()
      ])
      pendingTasks.value = (queue || []).map(normalizeTaskData)
      executingTasks.value = (executing || []).map(normalizeTaskData)
    } catch (e) {
      console.error('加载任务队列失败，使用Mock数据', e)
      pendingTasks.value = mockPendingTasks.map(normalizeTaskData)
      executingTasks.value = mockExecutingTasks.map(normalizeTaskData)
    }
  }

  const loadAlarms = async () => {
    try {
      const [unhandled, all] = await Promise.all([
        dispatchApi.getUnhandledAlarms(),
        dispatchApi.getAllAlarms()
      ])
      const allAlarms = (all || []).map(normalizeAlarmData)
      allAlarms.forEach(alarm => {
        const unhandledItem = unhandled?.find(u => u.id === alarm.id)
        if (unhandledItem) {
          alarm.handled = false
        }
      })
      alarms.value = allAlarms.sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
    } catch (e) {
      console.error('加载告警失败，使用Mock数据', e)
      alarms.value = mockAlerts.map(normalizeAlarmData)
    }
  }

  const loadStatistics = async () => {
    try {
      const [taskStats, agvStats] = await Promise.all([
        taskApi.getStatistics(),
        agvApi.getStatistics()
      ])
      historyStats.value = {
        ...taskStats,
        ...agvStats
      }
    } catch (e) {
      console.error('加载统计数据失败，使用Mock数据', e)
      historyStats.value = { ...mockHistoryStats }
    }
  }

  const loadCompletedTasks = async () => {
    try {
      const res = await taskApi.query({ pageNum: 1, pageSize: 10, status: 3 })
      completedTasks.value = (res?.content || []).map(normalizeTaskData)
    } catch (e) {
      console.error('加载已完成任务失败，使用Mock数据', e)
      completedTasks.value = mockCompletedTasks.map(normalizeTaskData)
    }
  }

  const handleAlarm = async (alarmId, handleResult, handler = 'admin') => {
    try {
      await dispatchApi.handleAlarm(alarmId, handleResult, handler)
      const alarm = alarms.value.find(a => a.id === alarmId)
      if (alarm) {
        alarm.handled = true
        alarm.handleTime = new Date().toISOString()
        alarm.handleResult = handleResult
        alarm.handler = handler
      }
      ElMessage.success('告警处理成功')
      return true
    } catch (e) {
      ElMessage.error(e.message || '告警处理失败')
      return false
    }
  }

  const updateTaskPriority = async (taskId, priority, operator = 'admin') => {
    try {
      await taskApi.updatePriority({ taskId, priority, operator })
      ElMessage.success('任务优先级已更新')
      await loadTaskQueue()
      return true
    } catch (e) {
      ElMessage.error(e.message || '更新优先级失败')
      return false
    }
  }

  const initAgvAnimationState = () => {
    agvList.value.forEach(agv => {
      if (!agvAnimationState.value.has(agv.id)) {
        const pos = getNodePosition(agv.currentPosition, MAP_NODES)
        agvAnimationState.value.set(agv.id, {
          currentPos: pos,
          targetPos: pos,
          progress: 1,
          targetNode: agv.currentPosition
        })
      }
    })
  }

  const updateAgvPositions = () => {
    agvList.value.forEach(agv => {
      const state = agvAnimationState.value.get(agv.id)
      if (!state) return

      if (state.progress >= 1) {
        const executing = executingTasks.value.find(t => t.assignedAgv === agv.agvNo)
        if (executing && executing.currentPath && executing.currentPath.length > 1) {
          const nextIndex = Math.min(executing.pathIndex + 1, executing.currentPath.length - 1)
          state.targetNode = executing.currentPath[nextIndex]
          state.targetPos = getNodePosition(state.targetNode, MAP_NODES)
          state.progress = 0
        }
      }

      if (state.progress < 1) {
        state.progress = Math.min(state.progress + 0.05, 1)
        state.currentPos = interpolatePosition(
          getNodePosition(agv.currentPosition, MAP_NODES),
          state.targetPos,
          state.progress
        )
      }
    })
  }

  const updateTaskProgress = () => {
    executingTasks.value.forEach(task => {
      if (task.progress < 100) {
        task.progress = Math.min(task.progress + Math.random() * 2, 100)
        if (task.currentPath && task.currentPath.length > 0) {
          const pathProgress = task.progress / 100
          task.pathIndex = Math.floor(pathProgress * (task.currentPath.length - 1))
        }
      }
    })
  }

  const initWebSocket = () => {
    try {
      stompClient = websocketService.connect()
      stompClient.connect({}, () => {
        stompClient.subscribe('/topic/agv-status', (message) => {
          const data = JSON.parse(message.body)
          agvList.value = (data || []).map(normalizeAgvData)
          initAgvAnimationState()
        })

        stompClient.subscribe('/topic/task-queue', (message) => {
          const data = JSON.parse(message.body)
          pendingTasks.value = (data || []).map(normalizeTaskData)
        })

        stompClient.subscribe('/topic/alarms', (message) => {
          const data = JSON.parse(message.body)
          const newAlarm = normalizeAlarmData(data)
          if (newAlarm && !alarms.value.find(a => a.id === newAlarm.id)) {
            alarms.value.unshift(newAlarm)
          }
        })

        stompClient.subscribe('/topic/task-progress', (message) => {
          const data = JSON.parse(message.body)
          const task = executingTasks.value.find(t => t.id === data.taskId)
          if (task) {
            task.progress = data.progress
          }
        })
      })
    } catch (e) {
      console.log('WebSocket连接失败，使用轮询方式')
    }
  }

  const loadAllData = async () => {
    loading.value = true
    try {
      await Promise.all([
        loadAgvList(),
        loadTaskQueue(),
        loadAlarms(),
        loadStatistics(),
        loadCompletedTasks()
      ])
      error.value = null
    } catch (e) {
      error.value = e
    } finally {
      loading.value = false
    }
  }

  const startRealTimeUpdates = () => {
    dataRefreshTimer = setInterval(() => {
      loadAgvList()
      loadTaskQueue()
      loadAlarms()
    }, 5000)

    agvAnimationTimer = setInterval(updateAgvPositions, 100)

    taskProgressTimer = setInterval(updateTaskProgress, 2000)
  }

  const stopRealTimeUpdates = () => {
    if (dataRefreshTimer) {
      clearInterval(dataRefreshTimer)
      dataRefreshTimer = null
    }
    if (agvAnimationTimer) {
      clearInterval(agvAnimationTimer)
      agvAnimationTimer = null
    }
    if (taskProgressTimer) {
      clearInterval(taskProgressTimer)
      taskProgressTimer = null
    }
  }

  const getAgvAnimatedPosition = (agvId) => {
    const state = agvAnimationState.value.get(agvId)
    return state?.currentPos || [0, 0]
  }

  onMounted(() => {
    loadAllData()
    initWebSocket()
    startRealTimeUpdates()
  })

  onUnmounted(() => {
    stopRealTimeUpdates()
    if (stompClient) {
      stompClient.disconnect()
    }
  })

  return {
    agvList,
    pendingTasks,
    executingTasks,
    alarms,
    historyStats,
    completedTasks,
    loading,
    error,
    loadAllData,
    loadAgvList,
    loadTaskQueue,
    loadAlarms,
    loadStatistics,
    loadCompletedTasks,
    handleAlarm,
    updateTaskPriority,
    getAgvAnimatedPosition,
    startRealTimeUpdates,
    stopRealTimeUpdates
  }
}
