<template>
  <div class="monitor-screen">
    <div class="screen-header">
      <div class="header-left">
        <h1>
          <el-icon><DataBoard /></el-icon>
          AGV 智能调度监控大屏
        </h1>
      </div>
      <div class="header-center">
        <div class="system-time">
          <el-icon><Watch /></el-icon>
          {{ currentTime }}
        </div>
      </div>
      <div class="header-right">
        <div class="status-indicator" :class="{ connected: isConnected }">
          <span class="status-dot"></span>
          {{ isConnected ? '系统正常' : '连接断开' }}
        </div>
        <el-button size="small" :icon="isFullscreen ? Switch : FullScreen" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </el-button>
      </div>
    </div>

    <div class="screen-body">
      <div class="left-panel">
        <div class="panel-wrapper agv-panel">
          <AgvStatusCards :agv-list="agvList" />
        </div>
        <div class="panel-wrapper alert-panel">
          <AlertPanel :alerts="alerts" @handle-alert="handleAlert" />
        </div>
      </div>

      <div class="center-panel">
        <div class="panel-wrapper map-panel">
          <MapVisualization
            :map-nodes="mapNodes"
            :map-paths="mapPaths"
            :agv-list="agvList"
            :executing-tasks="executingTasks"
          />
        </div>
        <div class="panel-wrapper queue-panel">
          <TaskQueueBoard
            :pending-tasks="pendingTasks"
            :executing-tasks="executingTasks"
            @update-priority="handleUpdatePriority"
          />
        </div>
      </div>

      <div class="right-panel">
        <div class="panel-wrapper history-panel">
          <HistoryStatistics :history-stats="historyStats" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataBoard, Watch, FullScreen, Switch
} from '@element-plus/icons-vue'
import MapVisualization from '@/components/monitor-screen/MapVisualization.vue'
import TaskQueueBoard from '@/components/monitor-screen/TaskQueueBoard.vue'
import AgvStatusCards from '@/components/monitor-screen/AgvStatusCards.vue'
import AlertPanel from '@/components/monitor-screen/AlertPanel.vue'
import HistoryStatistics from '@/components/monitor-screen/HistoryStatistics.vue'
import {
  mapNodes,
  mapPaths,
  mockAgvList,
  mockPendingTasks,
  mockExecutingTasks,
  mockAlerts,
  mockHistoryStats,
  generateMockAgvPositions
} from '@/mock/monitorData'

const currentTime = ref('')
const isConnected = ref(true)
const isFullscreen = ref(false)

const agvList = ref([...mockAgvList])
const pendingTasks = ref([...mockPendingTasks])
const executingTasks = ref([...mockExecutingTasks])
const alerts = ref([...mockAlerts])
const historyStats = ref({ ...mockHistoryStats })

let timeTimer = null
let dataTimer = null
let progressTimer = null

const unhandledAlertCount = computed(() => alerts.value.filter(a => !a.handled).length)

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
}

const updateAgvPositions = () => {
  agvList.value = generateMockAgvPositions([...agvList.value])
}

const updateTaskProgress = () => {
  executingTasks.value = executingTasks.value.map(task => {
    if (task.progress < 100) {
      const increment = Math.floor(Math.random() * 3) + 1
      const newProgress = Math.min(task.progress + increment, 100)
      const pathLength = task.currentPath?.length || 0
      const newNodeIndex = pathLength > 0
        ? Math.min(Math.floor((newProgress / 100) * (pathLength - 1)), pathLength - 1)
        : task.currentNodeIndex

      return {
        ...task,
        progress: newProgress,
        elapsedTime: task.elapsedTime + 2,
        currentNodeIndex: newNodeIndex
      }
    }
    return task
  })

  const completedTasks = executingTasks.value.filter(t => t.progress >= 100)
  if (completedTasks.length > 0) {
    completedTasks.forEach(task => {
      addCompletedTask(task)
    })
    executingTasks.value = executingTasks.value.filter(t => t.progress < 100)
  }

  if (pendingTasks.value.length > 0 && executingTasks.value.length < 5) {
    const newTask = pendingTasks.value.shift()
    if (newTask) {
      const availableAgv = agvList.value.find(a => a.status === 0)
      if (availableAgv) {
        executingTasks.value.push({
          ...newTask,
          status: 2,
          assignedAgv: availableAgv.agvNo,
          progress: 0,
          currentPath: [newTask.startPoint, 'C02', newTask.endPoint],
          currentNodeIndex: 0,
          startTime: new Date().toLocaleString('zh-CN'),
          elapsedTime: 0
        })
        availableAgv.status = 1
        availableAgv.currentTask = newTask.taskNo
      }
    }
  }
}

const addCompletedTask = (task) => {
  const newCompleted = {
    taskNo: task.taskNo,
    type: task.taskType,
    startPoint: task.startPoint,
    endPoint: task.endPoint,
    completedTime: new Date().toLocaleString('zh-CN'),
    duration: task.elapsedTime,
    mileage: Math.round(Math.random() * 20 + 10)
  }
  historyStats.value.recentCompletedTasks = [
    newCompleted,
    ...historyStats.value.recentCompletedTasks.slice(0, 4)
  ]
  historyStats.value.todayCompleted = (historyStats.value.todayCompleted || 0) + 1

  const agv = agvList.value.find(a => a.currentTask === task.taskNo)
  if (agv) {
    agv.status = 0
    agv.currentTask = null
    agv.batteryLevel = Math.max(agv.batteryLevel - 5, 10)
  }
}

const handleAlert = (alert) => {
  const index = alerts.value.findIndex(a => a.id === alert.id)
  if (index !== -1) {
    alerts.value[index].handled = true
    ElMessage.success(`告警 ${alert.id} 已处理`)
  }
}

const handleUpdatePriority = ({ taskId, oldIndex, newIndex }) => {
  const list = [...pendingTasks.value]
  const [movedItem] = list.splice(oldIndex, 1)
  list.splice(newIndex, 0, movedItem)
  list.forEach((task, idx) => {
    task.priority = list.length - idx
  })
  pendingTasks.value = list
  ElMessage.success(`任务 ${movedItem.taskNo} 优先级已调整`)
}

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
  dataTimer = setInterval(updateAgvPositions, 2000)
  progressTimer = setInterval(updateTaskProgress, 2000)

  document.addEventListener('fullscreenchange', handleFullscreenChange)

  if (unhandledAlertCount.value > 0) {
    ElMessage.warning(`当前有 ${unhandledAlertCount.value} 条未处理告警`)
  }
})

onUnmounted(() => {
  clearInterval(timeTimer)
  clearInterval(dataTimer)
  clearInterval(progressTimer)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})
</script>

<style lang="scss" scoped>
.monitor-screen {
  height: 100vh;
  width: 100vw;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: fixed;
  top: 0;
  left: 0;
  z-index: 9999;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image:
      linear-gradient(rgba(59, 130, 246, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(59, 130, 246, 0.03) 1px, transparent 1px);
    background-size: 50px 50px;
    pointer-events: none;
  }

  .screen-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 24px;
    background: linear-gradient(90deg, rgba(15, 23, 42, 0.95) 0%, rgba(30, 41, 59, 0.95) 50%, rgba(15, 23, 42, 0.95) 100%);
    border-bottom: 2px solid rgba(59, 130, 246, 0.3);
    position: relative;
    z-index: 10;

    &::before, &::after {
      content: '';
      position: absolute;
      top: 0;
      width: 100px;
      height: 100%;
      pointer-events: none;
    }

    &::before {
      left: 0;
      background: linear-gradient(90deg, rgba(59, 130, 246, 0.2) 0%, transparent 100%);
    }

    &::after {
      right: 0;
      background: linear-gradient(-90deg, rgba(59, 130, 246, 0.2) 0%, transparent 100%);
    }

    .header-left {
      h1 {
        margin: 0;
        font-size: 24px;
        font-weight: 700;
        color: #e0e7ff;
        display: flex;
        align-items: center;
        gap: 12px;
        letter-spacing: 2px;
        text-shadow: 0 0 20px rgba(59, 130, 246, 0.5);

        :deep(.el-icon) {
          color: #3b82f6;
          font-size: 28px;
        }
      }
    }

    .header-center {
      .system-time {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 18px;
        font-weight: 600;
        color: #60a5fa;
        font-family: 'Courier New', monospace;
        padding: 8px 20px;
        background: rgba(59, 130, 246, 0.1);
        border: 1px solid rgba(59, 130, 246, 0.3);
        border-radius: 8px;

        :deep(.el-icon) {
          color: #3b82f6;
        }
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 16px;

      .status-indicator {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 16px;
        background: rgba(239, 68, 68, 0.1);
        border: 1px solid rgba(239, 68, 68, 0.3);
        border-radius: 20px;
        font-size: 13px;
        font-weight: 600;
        color: #f87171;

        &.connected {
          background: rgba(16, 185, 129, 0.1);
          border-color: rgba(16, 185, 129, 0.3);
          color: #34d399;

          .status-dot {
            background: #10b981;
          }
        }

        .status-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #ef4444;
          animation: blink 1.5s infinite;
        }
      }
    }
  }

  .screen-body {
    flex: 1;
    display: grid;
    grid-template-columns: 380px 1fr 420px;
    gap: 12px;
    padding: 12px;
    min-height: 0;
    position: relative;
    z-index: 5;

    .left-panel,
    .center-panel,
    .right-panel {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-height: 0;
    }

    .left-panel {
      .agv-panel {
        flex: 1.2;
        min-height: 0;
      }

      .alert-panel {
        flex: 1;
        min-height: 0;
      }
    }

    .center-panel {
      .map-panel {
        flex: 1.2;
        min-height: 0;
      }

      .queue-panel {
        flex: 1;
        min-height: 0;
      }
    }

    .right-panel {
      .history-panel {
        flex: 1;
        min-height: 0;
      }
    }

    .panel-wrapper {
      background: linear-gradient(135deg, rgba(30, 41, 59, 0.8) 0%, rgba(15, 23, 42, 0.9) 100%);
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 12px;
      overflow: hidden;
      backdrop-filter: blur(10px);
      box-shadow:
        0 4px 20px rgba(0, 0, 0, 0.3),
        inset 0 1px 0 rgba(255, 255, 255, 0.05);
      position: relative;

      &::before {
        content: '';
        position: absolute;
        top: 0;
        left: 20px;
        right: 20px;
        height: 2px;
        background: linear-gradient(90deg,
          transparent 0%,
          rgba(59, 130, 246, 0.5) 20%,
          rgba(59, 130, 246, 0.5) 80%,
          transparent 100%);
      }
    }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}
</style>
