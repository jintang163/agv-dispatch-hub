<template>
  <div class="page-container">
    <div class="page-header">
      <h2>任务队列</h2>
      <div class="header-actions">
        <el-button @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新队列
        </el-button>
        <el-button type="primary" @click="handleRefreshQueue">
          <el-icon><Sort /></el-icon>
          重新排序
        </el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="6">
        <div class="stat-card priority-high">
          <div class="stat-value">{{ highPriorityCount }}</div>
          <div class="stat-label">高优先级</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card priority-medium">
          <div class="stat-value">{{ mediumPriorityCount }}</div>
          <div class="stat-label">中优先级</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card priority-low">
          <div class="stat-value">{{ lowPriorityCount }}</div>
          <div class="stat-label">低优先级</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card total">
          <div class="stat-value">{{ queueSize }}</div>
          <div class="stat-label">队列总数</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>优先级分布</span>
            </div>
          </template>
          <div ref="priorityChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>截止时间分布</span>
            </div>
          </template>
          <div ref="deadlineChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>待分配任务队列</span>
          <el-tag type="info">按优先级和截止时间排序</el-tag>
        </div>
      </template>

      <div class="queue-container" v-loading="loading">
        <div
          v-for="(task, index) in taskQueue"
          :key="task.id"
          class="queue-item"
          :class="`priority-${task.priority.toLowerCase()}`"
          draggable="true"
          @dragstart="handleDragStart($event, index)"
          @dragover="handleDragOver"
          @drop="handleDrop($event, index)"
          @dragend="handleDragEnd"
        >
          <div class="queue-order">
            <span class="order-badge">{{ index + 1 }}</span>
          </div>
          <div class="queue-content">
            <div class="queue-header">
              <span class="task-no">{{ task.taskNo }}</span>
              <span :class="['priority-tag', task.priority.toLowerCase()]">
                {{ getPriorityName(task.priority) }}
              </span>
              <span class="task-type">{{ getTaskTypeName(task.taskType) }}</span>
              <span v-if="index < 3" class="badge urgent">紧急</span>
            </div>
            <div class="queue-info">
              <span class="info-item">
                <el-icon><Position /></el-icon>
                {{ task.startPoint }} → {{ task.endPoint }}
              </span>
              <span class="info-item">
                <el-icon><Clock /></el-icon>
                截止: {{ task.deadline ? formatDateTime(task.deadline) : '无' }}
              </span>
              <span class="info-item">
                <el-icon><Timer /></el-icon>
                等待: {{ getWaitTime(task.createTime) }}
              </span>
            </div>
            <div class="queue-progress">
              <el-progress
                :percentage="getQueuePositionPercent(index)"
                :color="getProgressColor(task.priority)"
                :show-text="false"
                :stroke-width="4"
              />
            </div>
          </div>
          <div class="queue-actions">
            <el-button
              type="primary"
              size="small"
              @click.stop="handleTopPriority(task)"
            >
              置顶
            </el-button>
            <el-button
              type="success"
              size="small"
              @click.stop="handleAutoAssign(task)"
            >
              自动分配
            </el-button>
          </div>
        </div>

        <el-empty v-if="taskQueue.length === 0 && !loading" description="暂无待分配任务" />
      </div>
    </el-card>

    <el-dialog
      v-model="topPriorityDialogVisible"
      title="设置高优先级插队"
      width="400px"
    >
      <el-form :model="topPriorityForm" label-width="100px">
        <el-form-item label="任务编号">
          <span>{{ currentTask?.taskNo }}</span>
        </el-form-item>
        <el-form-item label="新优先级" prop="priority">
          <el-select v-model="topPriorityForm.priority" style="width: 100%">
            <el-option label="高（插队到最前）" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="topPriorityForm.operator" placeholder="请输入操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="topPriorityDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmTopPriority">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Sort, Position, Clock, Timer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { taskApi, websocketService } from '@/api'

const loading = ref(false)
const taskQueue = ref([])
const queueSize = ref(0)
const dragIndex = ref(-1)

const priorityChartRef = ref(null)
const deadlineChartRef = ref(null)

let priorityChart = null
let deadlineChart = null
let stompClient = null

const highPriorityCount = ref(0)
const mediumPriorityCount = ref(0)
const lowPriorityCount = ref(0)

const topPriorityDialogVisible = ref(false)
const currentTask = ref(null)

const topPriorityForm = reactive({
  taskId: '',
  priority: 'HIGH',
  operator: ''
})

const getPriorityName = (priority) => {
  const map = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[priority] || priority
}

const getTaskTypeName = (type) => {
  const map = { TRANSPORT: '搬运', PICKING: '拣选', CHARGING: '充电', STANDBY: '待命' }
  return map[type] || type
}

const formatDateTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const getWaitTime = (createTime) => {
  const now = new Date()
  const created = new Date(createTime)
  const diff = now - created
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60) / (1000 * 60))
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  }
  return `${minutes}分钟`
}

const getQueuePositionPercent = (index) => {
  if (taskQueue.value.length === 0) return 0
  return Math.round(((index + 1) / taskQueue.value.length) * 100)
}

const getProgressColor = (priority) => {
  const colors = {
    HIGH: '#dc2626',
    MEDIUM: '#d97706',
    LOW: '#059669'
  }
  return colors[priority] || '#059669'
}

const loadQueueData = async () => {
  loading.value = true
  try {
    const [queue, size] = await Promise.all([
      taskApi.getQueue(),
      taskApi.getQueueSize()
    ])
    taskQueue.value = queue || []
    queueSize.value = size || 0
    calculatePriorityCounts()
    updateCharts()
  } catch (error) {
    ElMessage.error(error.message || '加载队列失败')
  } finally {
    loading.value = false
  }
}

const calculatePriorityCounts = () => {
  highPriorityCount.value = taskQueue.value.filter(t => t.priority === 'HIGH').length
  mediumPriorityCount.value = taskQueue.value.filter(t => t.priority === 'MEDIUM').length
  lowPriorityCount.value = taskQueue.value.filter(t => t.priority === 'LOW').length
}

const handleRefresh = () => {
  loadQueueData()
}

const handleRefreshQueue = async () => {
  try {
    await taskApi.refreshQueue()
    ElMessage.success('队列重新排序成功')
    loadQueueData()
  } catch (error) {
    ElMessage.error(error.message || '重新排序失败')
  }
}

const handleDragStart = (event, index) => {
  dragIndex.value = index
  event.dataTransfer.effectAllowed = 'move'
}

const handleDragOver = (event) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

const handleDrop = async (event, targetIndex) => {
  event.preventDefault()
  if (dragIndex.value === -1 || dragIndex.value === targetIndex) return

  const sourceIndex = dragIndex.value
  const sourceTask = taskQueue.value[sourceIndex]
  const targetTask = taskQueue.value[targetIndex]

  if (sourceIndex < targetIndex) {
    ElMessage.warning('只能将任务向上移动（提高优先级）')
    dragIndex.value = -1
    return
  }

  try {
    await taskApi.updatePriority({
      taskId: sourceTask.id,
      priority: targetTask.priority,
      operator: 'system'
    })
    ElMessage.success('任务优先级已调整')
    loadQueueData()
  } catch (error) {
    ElMessage.error(error.message || '调整失败')
  } finally {
    dragIndex.value = -1
  }
}

const handleDragEnd = () => {
  dragIndex.value = -1
}

const handleTopPriority = (task) => {
  currentTask.value = task
  topPriorityForm.taskId = task.id
  topPriorityForm.priority = 'HIGH'
  topPriorityForm.operator = ''
  topPriorityDialogVisible.value = true
}

const handleConfirmTopPriority = async () => {
  try {
    await taskApi.updatePriority(topPriorityForm)
    ElMessage.success('任务已设置为高优先级，已插队到队列前面')
    topPriorityDialogVisible.value = false
    loadQueueData()
  } catch (error) {
    ElMessage.error(error.message || '设置失败')
  }
}

const handleAutoAssign = async (task) => {
  try {
    await taskApi.autoAssign(task.id)
    ElMessage.success('自动分配成功')
    loadQueueData()
  } catch (error) {
    ElMessage.error(error.message || '分配失败')
  }
}

const initPriorityChart = () => {
  if (!priorityChartRef.value) return
  priorityChart = echarts.init(priorityChartRef.value)
  updatePriorityChart()
}

const updatePriorityChart = () => {
  if (!priorityChart) return
  priorityChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '0' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c} ({d}%)' },
      data: [
        { value: highPriorityCount.value, name: '高优先级', itemStyle: { color: '#dc2626' } },
        { value: mediumPriorityCount.value, name: '中优先级', itemStyle: { color: '#d97706' } },
        { value: lowPriorityCount.value, name: '低优先级', itemStyle: { color: '#059669' } }
      ]
    }]
  })
}

const initDeadlineChart = () => {
  if (!deadlineChartRef.value) return
  deadlineChart = echarts.init(deadlineChartRef.value)
  updateDeadlineChart()
}

const updateDeadlineChart = () => {
  if (!deadlineChart) return

  const now = new Date()
  let urgent = 0
  let normal = 0
  let noDeadline = 0

  taskQueue.value.forEach(task => {
    if (!task.deadline) {
      noDeadline++
    } else {
      const deadline = new Date(task.deadline)
      const diffHours = (deadline - now) / (1000 * 60 * 60)
      if (diffHours < 2) {
        urgent++
      } else {
        normal++
      }
    }
  })

  deadlineChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['紧急(<2h)', '正常', '无截止时间'] },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      barWidth: '50%',
      data: [
        { value: urgent, itemStyle: { color: '#dc2626' } },
        { value: normal, itemStyle: { color: '#2563eb' } },
        { value: noDeadline, itemStyle: { color: '#6b7280' } }
      ]
    }]
  })
}

const updateCharts = () => {
  updatePriorityChart()
  updateDeadlineChart()
}

const initWebSocket = () => {
  try {
    stompClient = websocketService.connect()
    stompClient.connect({}, () => {
      stompClient.subscribe('/topic/task-queue', (message) => {
        const data = JSON.parse(message.body)
        taskQueue.value = data || []
        calculatePriorityCounts()
        updateCharts()
      })
    })
  } catch (e) {
    console.log('WebSocket连接失败，使用轮询方式')
  }
}

const handleResize = () => {
  priorityChart?.resize()
  deadlineChart?.resize()
}

onMounted(() => {
  loadQueueData()
  nextTick(() => {
    initPriorityChart()
    initDeadlineChart()
  })
  initWebSocket()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  priorityChart?.dispose()
  deadlineChart?.dispose()
  if (stompClient) {
    stompClient.disconnect()
  }
})
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 10px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.priority-high {
  border-left: 4px solid #dc2626;
}

.priority-medium {
  border-left: 4px solid #d97706;
}

.priority-low {
  border-left: 4px solid #059669;
}

.total {
  border-left: 4px solid #2563eb;
}

.queue-container {
  max-height: 600px;
  overflow-y: auto;
}

.queue-item {
  display: flex;
  align-items: center;
  padding: 16px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  border-left: 4px solid #059669;
  cursor: move;
  transition: all 0.3s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    transform: translateX(4px);
  }

  &.priority-high {
    border-left-color: #dc2626;
    background: linear-gradient(90deg, #fef2f2 0%, #fff 100%);
  }

  &.priority-medium {
    border-left-color: #d97706;
    background: linear-gradient(90deg, #fffbeb 0%, #fff 100%);
  }

  &.priority-low {
    border-left-color: #059669;
  }
}

.queue-order {
  margin-right: 16px;
}

.order-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f3f4f6;
  font-weight: 600;
  font-size: 16px;
  color: #4b5563;
}

.queue-item:first-child .order-badge {
  background: #dc2626;
  color: #fff;
}

.queue-item:nth-child(2) .order-badge {
  background: #d97706;
  color: #fff;
}

.queue-item:nth-child(3) .order-badge {
  background: #f59e0b;
  color: #fff;
}

.queue-content {
  flex: 1;
  min-width: 0;
}

.queue-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.task-no {
  font-weight: 600;
  color: #1f2937;
}

.task-type {
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 2px 8px;
  border-radius: 4px;
}

.badge.urgent {
  font-size: 11px;
  background: #fee2e2;
  color: #dc2626;
  padding: 2px 8px;
  border-radius: 4px;
  animation: blink 1.5s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.queue-info {
  display: flex;
  gap: 20px;
  margin-bottom: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #6b7280;

  .el-icon {
    font-size: 14px;
  }
}

.queue-progress {
  margin-top: 8px;
}

.queue-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-left: 16px;
}
</style>
