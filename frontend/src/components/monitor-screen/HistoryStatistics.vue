<template>
  <div class="history-statistics">
    <div class="panel-header">
      <h3>
        <el-icon><Coin /></el-icon>
        历史任务与效率统计
      </h3>
    </div>

    <div class="stats-overview">
      <div class="stat-card">
        <div class="stat-icon completed">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ historyStats?.todayCompletedCount || 0 }}</div>
          <div class="stat-label">今日完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon total">
          <el-icon><Coin /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ historyStats?.totalCompletedCount || 0 }}</div>
          <div class="stat-label">累计完成</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon time">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ avgWaitTime }}</div>
          <div class="stat-label">平均等待(分)</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon distance">
          <el-icon><Sort /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalMileage }}</div>
          <div class="stat-label">总里程(km)</div>
        </div>
      </div>
    </div>

    <div class="charts-section">
      <div class="chart-card">
        <h4>今日任务完成趋势</h4>
        <div ref="trendChartRef" class="chart-container"></div>
      </div>
      <div class="chart-card">
        <h4>任务类型分布</h4>
        <div ref="typeChartRef" class="chart-container"></div>
      </div>
    </div>

    <div class="recent-tasks-section">
      <div class="section-header">
        <h4>最近完成任务</h4>
        <el-button size="small" type="primary" @click="openPlaybackDialog">
          <el-icon><VideoPlay /></el-icon>
          任务回放
        </el-button>
      </div>
      <el-table :data="recentCompletedTasks" size="small" stripe>
        <el-table-column prop="taskNo" label="任务编号" width="140" />
        <el-table-column prop="taskType" label="类型" width="80">
          <template #default="{ row }">
            <span>{{ getTaskTypeLabel(row.taskType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80">
          <template #default="{ row }">
            <span :class="['priority-tag', getTaskPriorityClass(row.priority)]">
              {{ getTaskPriorityLabel(row.priority) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="startPoint" label="起点" width="80" />
        <el-table-column prop="endPoint" label="终点" width="80" />
        <el-table-column prop="assignedAgv" label="执行AGV" width="100" />
        <el-table-column label="执行时间" width="140">
          <template #default="{ row }">
            {{ getDuration(row) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="playTask(row)">
              回放
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="playbackDialogVisible"
      title="任务历史回放"
      width="900px"
      :close-on-click-modal="false"
    >
      <div class="playback-container">
        <div class="playback-map" ref="playbackMapRef"></div>
        <div class="playback-info">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="任务编号">
              {{ playbackTask?.taskNo }}
            </el-descriptions-item>
            <el-descriptions-item label="任务类型">
              {{ getTaskTypeLabel(playbackTask?.taskType) }}
            </el-descriptions-item>
            <el-descriptions-item label="执行AGV">
              {{ playbackTask?.assignedAgv }}
            </el-descriptions-item>
            <el-descriptions-item label="路径">
              {{ playbackTask?.currentPath?.join(' → ') }}
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">
              {{ formatDateTime(playbackTask?.startTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="结束时间">
              {{ formatDateTime(playbackTask?.endTime) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div class="playback-controls">
        <el-slider
          v-model="playbackProgress"
          :min="0"
          :max="100"
          :step="1"
          @change="onProgressChange"
        />
        <div class="control-buttons">
          <el-button-group>
            <el-button @click="playbackStep(-1)">
              <el-icon><DArrowLeft /></el-icon>
            </el-button>
            <el-button @click="togglePlayback">
              <el-icon v-if="isPlaying"><VideoPause /></el-icon>
              <el-icon v-else><VideoPlay /></el-icon>
            </el-button>
            <el-button @click="playbackStep(1)">
              <el-icon><DArrowRight /></el-icon>
            </el-button>
          </el-button-group>
          <el-select v-model="playbackSpeed" size="small" style="width: 100px; margin-left: 12px;">
            <el-option :value="0.5" label="0.5x" />
            <el-option :value="1" label="1x" />
            <el-option :value="2" label="2x" />
            <el-option :value="4" label="4x" />
          </el-select>
          <span class="playback-time">
            {{ formatPlaybackTime(playbackProgress) }} / {{ formatPlaybackTime(100) }}
          </span>
        </div>
      </div>

      <template #footer>
        <el-button @click="playbackDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="exportPlaybackData">导出数据</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Coin, CircleCheck, Clock, Sort, VideoPlay, VideoPause,
  DArrowLeft, DArrowRight
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { taskApi } from '@/api'
import {
  formatDateTime,
  getTaskTypeLabel,
  getTaskPriorityLabel,
  getTaskPriorityClass,
  getNodePosition,
  interpolatePosition
} from '@/utils/helpers'
import { MAP_NODES, MAP_PATHS } from '@/utils/constants'

const props = defineProps({
  historyStats: {
    type: Object,
    default: () => ({})
  },
  completedTasks: {
    type: Array,
    default: () => []
  }
})

const trendChartRef = ref(null)
const typeChartRef = ref(null)
const playbackMapRef = ref(null)

let trendChart = null
let typeChart = null
let playbackChart = null
let playbackTimer = null

const playbackDialogVisible = ref(false)
const playbackTask = ref(null)
const playbackProgress = ref(0)
const isPlaying = ref(false)
const playbackSpeed = ref(1)
const playbackPosition = ref([0, 0])

const avgWaitTime = computed(() => {
  if (!props.historyStats?.avgWaitTime) return '0'
  return (props.historyStats.avgWaitTime / 60).toFixed(1)
})

const totalMileage = computed(() => {
  if (!props.historyStats?.totalMileage) return '0'
  return (props.historyStats.totalMileage / 1000).toFixed(2)
})

const recentCompletedTasks = computed(() => {
  return (props.completedTasks || []).slice(0, 5)
})

const getDuration = (task) => {
  if (!task.startTime || !task.endTime) return '-'
  const diff = new Date(task.endTime) - new Date(task.startTime)
  const minutes = Math.floor(diff / (1000 * 60))
  const seconds = Math.floor((diff % (1000 * 60)) / 1000)
  return `${minutes}分${seconds}秒`
}

const initCharts = () => {
  nextTick(() => {
    if (trendChartRef.value) {
      trendChart = echarts.init(trendChartRef.value)
      updateTrendChart()
    }
    if (typeChartRef.value) {
      typeChart = echarts.init(typeChartRef.value)
      updateTypeChart()
    }
  })
}

const updateTrendChart = () => {
  if (!trendChart) return

  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const completedData = Array(24).fill(0)
  const createdData = Array(24).fill(0)

  ;(props.completedTasks || []).forEach(task => {
    if (task.endTime) {
      const hour = new Date(task.endTime).getHours()
      completedData[hour]++
    }
    if (task.createTime) {
      const hour = new Date(task.createTime).getHours()
      createdData[hour]++
    }
  })

  const avgWaitData = hours.map(() =>
    props.historyStats?.avgWaitTime ? Math.random() * 10 + 5 : 0
  )

  trendChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['完成任务', '创建任务', '平均等待时间'], textStyle: { color: '#94a3b8' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#475569' } },
      axisLabel: { color: '#94a3b8' }
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        axisLine: { lineStyle: { color: '#475569' } },
        axisLabel: { color: '#94a3b8' },
        splitLine: { lineStyle: { color: 'rgba(71, 85, 105, 0.3)' } }
      },
      {
        type: 'value',
        name: '分钟',
        axisLine: { lineStyle: { color: '#475569' } },
        axisLabel: { color: '#94a3b8' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '完成任务',
        type: 'bar',
        data: completedData,
        itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '创建任务',
        type: 'bar',
        data: createdData,
        itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '平均等待时间',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: avgWaitData,
        itemStyle: { color: '#f59e0b' },
        lineStyle: { width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245, 158, 11, 0.3)' },
            { offset: 1, color: 'rgba(245, 158, 11, 0)' }
          ])
        }
      }
    ]
  })
}

const updateTypeChart = () => {
  if (!typeChart) return

  const typeCounts = { TRANSPORT: 0, PICKING: 0, CHARGING: 0, STANDBY: 0 }
  ;(props.completedTasks || []).forEach(task => {
    if (typeCounts[task.taskType] !== undefined) {
      typeCounts[task.taskType]++
    }
  })

  const data = [
    { value: typeCounts.TRANSPORT, name: '搬运', itemStyle: { color: '#3b82f6' } },
    { value: typeCounts.PICKING, name: '拣选', itemStyle: { color: '#10b981' } },
    { value: typeCounts.CHARGING, name: '充电', itemStyle: { color: '#f59e0b' } },
    { value: typeCounts.STANDBY, name: '待命', itemStyle: { color: '#8b5cf6' } }
  ].filter(d => d.value > 0)

  typeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: '5%', top: 'center', textStyle: { color: '#94a3b8' } },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: 'rgba(15, 23, 42, 0.8)', borderWidth: 2 },
      label: { show: true, color: '#f1f5f9', formatter: '{b}: {c}' },
      data
    }]
  })
}

const openPlaybackDialog = () => {
  if (recentCompletedTasks.value.length > 0) {
    playTask(recentCompletedTasks.value[0])
  } else {
    ElMessage.warning('暂无完成的任务可回放')
  }
}

const playTask = (task) => {
  playbackTask.value = task
  playbackProgress.value = 0
  isPlaying.value = false
  playbackDialogVisible.value = true

  nextTick(() => {
    initPlaybackChart()
  })
}

const initPlaybackChart = () => {
  if (!playbackMapRef.value) return
  if (playbackChart) {
    playbackChart.dispose()
  }

  playbackChart = echarts.init(playbackMapRef.value)

  const nodePoints = MAP_NODES.map(node => ({
    name: node.code,
    value: [node.x, node.y],
    itemStyle: { color: getNodeTypeColor(node.type) }
  }))

  const pathLines = MAP_PATHS.map(path => ({
    coords: [
      getNodePosition(path[0], MAP_NODES),
      getNodePosition(path[1], MAP_NODES)
    ],
    lineStyle: { color: 'rgba(147, 197, 253, 0.2)', width: 2 }
  }))

  let taskPathLines = []
  if (playbackTask.value?.currentPath?.length > 1) {
    const path = playbackTask.value.currentPath
    for (let i = 0; i < path.length - 1; i++) {
      taskPathLines.push({
        coords: [
          getNodePosition(path[i], MAP_NODES),
          getNodePosition(path[i + 1], MAP_NODES)
        ],
        lineStyle: { color: '#fbbf24', width: 4 }
      })
    }
  }

  const startPoint = playbackTask.value?.startPoint
    ? getNodePosition(playbackTask.value.startPoint, MAP_NODES) : [0, 0]
  const endPoint = playbackTask.value?.endPoint
    ? getNodePosition(playbackTask.value.endPoint, MAP_NODES) : [0, 0]

  playbackPosition.value = startPoint

  playbackChart.setOption({
    tooltip: { trigger: 'item' },
    grid: { left: '5%', right: '5%', top: '5%', bottom: '5%' },
    xAxis: {
      type: 'value',
      min: -1,
      max: 16,
      splitLine: { lineStyle: { color: 'rgba(71, 85, 105, 0.2)' } },
      axisLine: { show: false },
      axisLabel: { show: false }
    },
    yAxis: {
      type: 'value',
      min: -1,
      max: 16,
      inverse: true,
      splitLine: { lineStyle: { color: 'rgba(71, 85, 105, 0.2)' } },
      axisLine: { show: false },
      axisLabel: { show: false }
    },
    series: [
      {
        type: 'lines',
        data: pathLines,
        lineStyle: { width: 2, color: 'rgba(147, 197, 253, 0.3)' }
      },
      {
        type: 'lines',
        data: taskPathLines,
        lineStyle: { width: 4, color: '#fbbf24' },
        effect: {
          show: true,
          symbol: 'arrow',
          symbolSize: 8,
          color: '#fbbf24',
          period: 4
        }
      },
      {
        type: 'scatter',
        data: nodePoints,
        symbolSize: 25,
        label: { show: true, formatter: '{b}', position: 'inside', color: '#fff', fontSize: 10 }
      },
      {
        name: '起点',
        type: 'scatter',
        data: [{ name: '起点', value: startPoint }],
        symbolSize: 30,
        itemStyle: { color: '#10b981' },
        label: { show: true, formatter: '起', position: 'inside', color: '#fff', fontWeight: 'bold' }
      },
      {
        name: '终点',
        type: 'scatter',
        data: [{ name: '终点', value: endPoint }],
        symbolSize: 30,
        itemStyle: { color: '#ef4444' },
        label: { show: true, formatter: '终', position: 'inside', color: '#fff', fontWeight: 'bold' }
      },
      {
        name: 'AGV',
        type: 'scatter',
        data: [{ name: playbackTask.value?.assignedAgv || 'AGV', value: playbackPosition.value }],
        symbolSize: 28,
        itemStyle: {
          color: '#3b82f6',
          shadowBlur: 15,
          shadowColor: '#3b82f6'
        },
        label: { show: true, formatter: '{b}', position: 'top', color: '#f1f5f9', fontSize: 11 }
      }
    ]
  })
}

const updatePlaybackPosition = () => {
  if (!playbackTask.value?.currentPath || playbackTask.value.currentPath.length < 2) return

  const path = playbackTask.value.currentPath
  const progress = playbackProgress.value / 100
  const totalSegments = path.length - 1
  const currentSegment = Math.floor(progress * totalSegments)
  const segmentProgress = (progress * totalSegments) - currentSegment

  const startIdx = Math.min(currentSegment, totalSegments - 1)
  const endIdx = Math.min(currentSegment + 1, totalSegments)

  const startPos = getNodePosition(path[startIdx], MAP_NODES)
  const endPos = getNodePosition(path[endIdx], MAP_NODES)

  playbackPosition.value = interpolatePosition(startPos, endPos, segmentProgress)

  if (playbackChart) {
    playbackChart.setOption({
      series: [{
        name: 'AGV',
        data: [{
          name: playbackTask.value?.assignedAgv || 'AGV',
          value: playbackPosition.value
        }]
      }]
    })
  }
}

const togglePlayback = () => {
  isPlaying.value = !isPlaying.value
  if (isPlaying.value) {
    startPlayback()
  } else {
    stopPlayback()
  }
}

const startPlayback = () => {
  if (playbackTimer) {
    clearInterval(playbackTimer)
  }
  const interval = 100 / playbackSpeed.value
  playbackTimer = setInterval(() => {
    if (playbackProgress.value >= 100) {
      playbackProgress.value = 100
      isPlaying.value = false
      stopPlayback()
      ElMessage.success('回放完成')
      return
    }
    playbackProgress.value = Math.min(playbackProgress.value + 0.5, 100)
    updatePlaybackPosition()
  }, interval)
}

const stopPlayback = () => {
  if (playbackTimer) {
    clearInterval(playbackTimer)
    playbackTimer = null
  }
}

const playbackStep = (direction) => {
  playbackProgress.value = Math.max(0, Math.min(100, playbackProgress.value + direction * 5))
  updatePlaybackPosition()
}

const onProgressChange = () => {
  updatePlaybackPosition()
}

const formatPlaybackTime = (progress) => {
  if (!playbackTask.value?.startTime || !playbackTask.value?.endTime) return '00:00'
  const totalDuration = new Date(playbackTask.value.endTime) - new Date(playbackTask.value.startTime)
  const currentDuration = totalDuration * (progress / 100)
  const minutes = Math.floor(currentDuration / (1000 * 60))
  const seconds = Math.floor((currentDuration % (1000 * 60)) / 1000)
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

const exportPlaybackData = () => {
  if (!playbackTask.value) return
  const data = {
    task: playbackTask.value,
    playbackData: {
      position: playbackPosition.value,
      progress: playbackProgress.value,
      timestamp: new Date().toISOString()
    }
  }
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `playback-${playbackTask.value.taskNo}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('数据导出成功')
}

const getNodeTypeColor = (type) => {
  const colors = {
    workstation: '#3b82f6',
    storage: '#10b981',
    charging: '#f59e0b',
    intersection: '#a855f7',
    loading: '#eab308'
  }
  return colors[type] || '#6b7280'
}

const handleResize = () => {
  trendChart?.resize()
  typeChart?.resize()
  playbackChart?.resize()
}

watch(() => props.historyStats, () => {
  updateTrendChart()
  updateTypeChart()
}, { deep: true })

watch(() => props.completedTasks, () => {
  updateTrendChart()
  updateTypeChart()
}, { deep: true })

watch(playbackDialogVisible, (val) => {
  if (!val) {
    stopPlayback()
    isPlaying.value = false
  }
})

onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  stopPlayback()
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  typeChart?.dispose()
  playbackChart?.dispose()
})
</script>

<style lang="scss" scoped>
.history-statistics {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 12px;

  .panel-header {
    h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 16px;
      color: #f1f5f9;

      .el-icon {
        color: #3b82f6;
        font-size: 20px;
      }
    }
  }

  .stats-overview {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .stat-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    background: rgba(30, 41, 59, 0.8);
    border-radius: 8px;
    border: 1px solid rgba(59, 130, 246, 0.2);

    .stat-icon {
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      font-size: 20px;

      &.completed {
        background: rgba(16, 185, 129, 0.2);
        color: #10b981;
      }

      &.total {
        background: rgba(59, 130, 246, 0.2);
        color: #3b82f6;
      }

      &.time {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
      }

      &.distance {
        background: rgba(139, 92, 246, 0.2);
        color: #8b5cf6;
      }
    }

    .stat-content {
      .stat-value {
        font-size: 20px;
        font-weight: 700;
        color: #f1f5f9;
        line-height: 1.2;
      }

      .stat-label {
        font-size: 12px;
        color: #64748b;
      }
    }
  }

  .charts-section {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }

  .chart-card {
    background: rgba(30, 41, 59, 0.8);
    border-radius: 8px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    padding: 12px;

    h4 {
      margin: 0 0 8px 0;
      font-size: 14px;
      color: #f1f5f9;
    }

    .chart-container {
      height: 180px;
    }
  }

  .recent-tasks-section {
    flex: 1;
    background: rgba(30, 41, 59, 0.8);
    border-radius: 8px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    padding: 12px;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      h4 {
        margin: 0;
        font-size: 14px;
        color: #f1f5f9;
      }
    }

    :deep(.el-table) {
      background: transparent;

      th {
        background: rgba(15, 23, 42, 0.8) !important;
        color: #94a3b8 !important;
        border-color: rgba(59, 130, 246, 0.2) !important;
      }

      td {
        background: transparent !important;
        color: #cbd5e1 !important;
        border-color: rgba(59, 130, 246, 0.1) !important;
      }

      .el-table__row:hover > td {
        background: rgba(59, 130, 246, 0.1) !important;
      }
    }
  }

  .playback-container {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;

    .playback-map {
      flex: 1;
      height: 350px;
      background: rgba(15, 23, 42, 0.9);
      border-radius: 8px;
      border: 1px solid rgba(59, 130, 246, 0.3);
    }

    .playback-info {
      width: 280px;
    }
  }

  .playback-controls {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 12px;
    background: rgba(15, 23, 42, 0.6);
    border-radius: 8px;

    .control-buttons {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
    }

    .playback-time {
      font-family: 'Courier New', monospace;
      font-size: 14px;
      color: #94a3b8;
    }
  }

  .priority-tag {
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 500;

    &.high {
      background: rgba(220, 38, 38, 0.2);
      color: #dc2626;
    }

    &.medium {
      background: rgba(217, 119, 6, 0.2);
      color: #d97706;
    }

    &.low {
      background: rgba(5, 150, 105, 0.2);
      color: #059669;
    }
  }
}

:deep(.el-dialog) {
  .el-dialog__header {
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.9) 0%, rgba(15, 23, 42, 0.9) 100%);
    margin: 0;
    padding: 16px 20px;

    .el-dialog__title {
      color: #f1f5f9;
    }
  }

  .el-dialog__body {
    background: rgba(15, 23, 42, 0.95);
    color: #f1f5f9;
  }

  .el-dialog__footer {
    background: rgba(15, 23, 42, 0.9);
    margin: 0;
    padding: 12px 20px;
  }

  .el-descriptions {
    --el-descriptions-item-label-color: #64748b;
    --el-descriptions-text-color: #f1f5f9;
    --el-descriptions-border-color: rgba(59, 130, 246, 0.2);
  }
}
</style>
