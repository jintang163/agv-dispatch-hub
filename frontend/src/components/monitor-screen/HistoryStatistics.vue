<template>
  <div class="history-statistics">
    <div class="panel-header">
      <h3>
        <el-icon><DataAnalysis /></el-icon>
        历史任务与效率统计
      </h3>
      <div class="replay-controls">
        <el-button
          size="small"
          type="primary"
          :icon="isReplaying ? VideoPause : VideoPlay"
          @click="toggleReplay"
        >
          {{ isReplaying ? '暂停回放' : '任务回放' }}
        </el-button>
      </div>
    </div>

    <div class="stats-overview">
      <div class="stat-card" v-for="stat in overviewStats" :key="stat.key">
        <div class="stat-icon" :style="{ background: stat.color + '20' }">
          <el-icon :style="{ color: stat.color }"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <div class="charts-section">
      <div class="chart-card">
        <div class="chart-header">
          <h4>今日任务完成趋势</h4>
          <div class="chart-legend">
            <span class="legend-item">
              <span class="legend-dot" style="background: #3b82f6;"></span>
              完成数量
            </span>
            <span class="legend-item">
              <span class="legend-dot" style="background: #f59e0b;"></span>
              平均等待(分)
            </span>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-body"></div>
      </div>

      <div class="chart-card">
        <div class="chart-header">
          <h4>任务类型分布</h4>
        </div>
        <div ref="pieChartRef" class="chart-body"></div>
      </div>
    </div>

    <div class="recent-tasks">
      <div class="section-header">
        <h4>
          <el-icon><List /></el-icon>
          最近完成任务
        </h4>
      </div>
      <div class="tasks-table">
        <el-table :data="recentCompletedTasks" stripe size="small">
          <el-table-column prop="taskNo" label="任务编号" width="160" />
          <el-table-column prop="type" label="类型" width="80">
            <template #default="{ row }">
              <span :class="['type-tag', getTypeClass(row.type)]">
                {{ getTypeLabel(row.type) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="路径" min-width="150">
            <template #default="{ row }">
              <span class="route-text">
                {{ row.startPoint }}
                <el-icon><ArrowRight /></el-icon>
                {{ row.endPoint }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时(秒)" width="90" align="center" />
          <el-table-column prop="mileage" label="里程(米)" width="90" align="center" />
          <el-table-column prop="completedTime" label="完成时间" width="170" />
        </el-table>
      </div>
    </div>

    <div v-if="isReplaying" class="replay-player">
      <div class="player-header">
        <span class="player-title">
          <el-icon><VideoPlay /></el-icon>
          任务回放中 - {{ currentReplayTask?.taskNo || '---' }}
        </span>
        <div class="player-controls">
          <el-button size="small" :icon="DArrowLeft" @click="prevReplay" />
          <el-button size="small" :icon="isReplaying ? VideoPause : VideoPlay" @click="toggleReplay" />
          <el-button size="small" :icon="DArrowRight" @click="nextReplay" />
        </div>
      </div>
      <div class="player-progress">
        <el-slider
          v-model="replayProgress"
          :min="0"
          :max="100"
          :step="1"
          @change="onReplaySeek"
        />
      </div>
      <div class="player-info">
        <span v-if="currentReplayTask">
          路径: {{ currentReplayTask.startPoint }} → {{ currentReplayTask.endPoint }}
        </span>
        <span>进度: {{ replayProgress }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import {
  DataAnalysis, VideoPlay, VideoPause, List, ArrowRight,
  Check, Clock, Coin, Timer, DArrowLeft, DArrowRight
} from '@element-plus/icons-vue'

const props = defineProps({
  historyStats: {
    type: Object,
    default: () => ({})
  }
})

const trendChartRef = ref(null)
const pieChartRef = ref(null)
let trendChartInstance = null
let pieChartInstance = null

const isReplaying = ref(false)
const replayProgress = ref(0)
const currentReplayIndex = ref(0)
let replayTimer = null

const overviewStats = computed(() => [
  { key: 'today', label: '今日完成', value: props.historyStats.todayCompleted || 0, color: '#3b82f6', icon: Check },
  { key: 'total', label: '累计完成', value: (props.historyStats.totalCompleted || 0).toLocaleString(), color: '#10b981', icon: List },
  { key: 'waitTime', label: '平均等待', value: `${props.historyStats.avgWaitTime || 0} 分钟`, color: '#f59e0b', icon: Clock },
  { key: 'execTime', label: '平均执行', value: `${props.historyStats.avgExecutionTime || 0} 分钟`, color: '#8b5cf6', icon: Timer },
  { key: 'mileage', label: '总里程', value: `${(props.historyStats.totalMileage || 0).toLocaleString()} km`, color: '#ec4899', icon: Coin },
  { key: 'todayMileage', label: '今日里程', value: `${props.historyStats.todayMileage || 0} km`, color: '#06b6d4', icon: Coin }
])

const recentCompletedTasks = computed(() => props.historyStats.recentCompletedTasks || [])

const getTypeLabel = (type) => ({ 1: '搬运', 2: '拣选', 3: '充电', 4: '待命' }[type] || type)
const getTypeClass = (type) => ({ 1: 'transport', 2: 'pick', 3: 'charge', 4: 'idle' }[type] || '')

const currentReplayTask = computed(() => {
  const tasks = recentCompletedTasks.value
  return tasks[currentReplayIndex.value] || null
})

const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChartInstance = echarts.init(trendChartRef.value)
  updateTrendChart()
}

const updateTrendChart = () => {
  if (!trendChartInstance) return

  const data = props.historyStats.efficiencyTrend || []
  const hours = data.map(d => d.hour)
  const completedData = data.map(d => d.completed)
  const waitTimeData = data.map(d => d.waitTime)

  trendChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['完成数量', '平均等待时间'],
      textStyle: { color: '#9ca3af', fontSize: 11 },
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '18%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: '#374151' } },
      axisLabel: { color: '#9ca3af', fontSize: 10 }
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9ca3af', fontSize: 10 }
      },
      {
        type: 'value',
        name: '分钟',
        splitLine: { show: false },
        axisLine: { lineStyle: { color: '#374151' } },
        axisLabel: { color: '#9ca3af', fontSize: 10 }
      }
    ],
    series: [
      {
        name: '完成数量',
        type: 'bar',
        data: completedData,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#60a5fa' },
            { offset: 1, color: '#3b82f6' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        barWidth: '40%'
      },
      {
        name: '平均等待时间',
        type: 'line',
        yAxisIndex: 1,
        data: waitTimeData,
        smooth: true,
        itemStyle: { color: '#f59e0b' },
        lineStyle: { width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245, 158, 11, 0.3)' },
            { offset: 1, color: 'rgba(245, 158, 11, 0.05)' }
          ])
        }
      }
    ]
  })
}

const initPieChart = () => {
  if (!pieChartRef.value) return

  pieChartInstance = echarts.init(pieChartRef.value)
  updatePieChart()
}

const updatePieChart = () => {
  if (!pieChartInstance) return

  const data = props.historyStats.taskTypeDistribution || []

  pieChartInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center',
      textStyle: { color: '#9ca3af', fontSize: 11 }
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: 'rgba(0,0,0,0.3)',
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'inside',
          formatter: '{d}%',
          color: '#fff',
          fontSize: 11,
          fontWeight: 600
        },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        },
        data: data.map((item, idx) => ({
          ...item,
          itemStyle: {
            color: ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6'][idx]
          }
        }))
      }
    ]
  })
}

const toggleReplay = () => {
  isReplaying.value = !isReplaying.value
  if (isReplaying.value) {
    startReplay()
  } else {
    stopReplay()
  }
}

const startReplay = () => {
  stopReplay()
  replayTimer = setInterval(() => {
    replayProgress.value += 1
    if (replayProgress.value >= 100) {
      nextReplay()
    }
  }, 100)
}

const stopReplay = () => {
  if (replayTimer) {
    clearInterval(replayTimer)
    replayTimer = null
  }
}

const nextReplay = () => {
  const tasks = recentCompletedTasks.value
  if (currentReplayIndex.value < tasks.length - 1) {
    currentReplayIndex.value++
  } else {
    currentReplayIndex.value = 0
  }
  replayProgress.value = 0
}

const prevReplay = () => {
  const tasks = recentCompletedTasks.value
  if (currentReplayIndex.value > 0) {
    currentReplayIndex.value--
  } else {
    currentReplayIndex.value = tasks.length - 1
  }
  replayProgress.value = 0
}

const onReplaySeek = (val) => {
  replayProgress.value = val
}

const handleResize = () => {
  trendChartInstance?.resize()
  pieChartInstance?.resize()
}

watch(
  () => props.historyStats,
  () => {
    nextTick(() => {
      updateTrendChart()
      updatePieChart()
    })
  },
  { deep: true }
)

onMounted(() => {
  setTimeout(() => {
    initTrendChart()
    initPieChart()
  }, 100)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  stopReplay()
  window.removeEventListener('resize', handleResize)
  trendChartInstance?.dispose()
  pieChartInstance?.dispose()
})
</script>

<style lang="scss" scoped>
.history-statistics {
  height: 100%;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: #e0e7ff;
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .stats-overview {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 12px;
    padding: 16px;

    .stat-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      transition: all 0.3s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.08);
        transform: translateY(-2px);
      }

      .stat-icon {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;

        :deep(.el-icon) {
          font-size: 20px;
        }
      }

      .stat-info {
        min-width: 0;

        .stat-value {
          font-size: 16px;
          font-weight: 700;
          color: #e0e7ff;
          white-space: nowrap;
        }

        .stat-label {
          font-size: 11px;
          color: #9ca3af;
        }
      }
    }
  }

  .charts-section {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: 16px;
    padding: 0 16px 16px;

    .chart-card {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      padding: 16px;

      .chart-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 10px;

        h4 {
          margin: 0;
          font-size: 13px;
          font-weight: 600;
          color: #e0e7ff;
        }

        .chart-legend {
          display: flex;
          gap: 16px;
          font-size: 11px;
          color: #9ca3af;

          .legend-item {
            display: flex;
            align-items: center;
            gap: 6px;

            .legend-dot {
              width: 8px;
              height: 8px;
              border-radius: 50%;
            }
          }
        }
      }

      .chart-body {
        height: 220px;
      }
    }
  }

  .recent-tasks {
    flex: 1;
    padding: 0 16px 16px;
    min-height: 0;
    display: flex;
    flex-direction: column;

    .section-header {
      margin-bottom: 10px;

      h4 {
        margin: 0;
        font-size: 13px;
        font-weight: 600;
        color: #e0e7ff;
        display: flex;
        align-items: center;
        gap: 6px;
      }
    }

    .tasks-table {
      flex: 1;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      overflow: hidden;

      :deep(.el-table) {
        --el-table-bg-color: transparent;
        --el-table-tr-bg-color: transparent;
        --el-table-header-bg-color: rgba(0, 0, 0, 0.2);
        --el-table-text-color: #d1d5db;
        --el-table-header-text-color: #9ca3af;
        --el-table-border-color: rgba(255, 255, 255, 0.1);
        --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
      }

      :deep(.el-table th) {
        background: rgba(0, 0, 0, 0.2) !important;
        font-weight: 600;
      }

      .type-tag {
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 11px;
        font-weight: 500;

        &.transport {
          background: rgba(59, 130, 246, 0.2);
          color: #60a5fa;
        }

        &.pick {
          background: rgba(16, 185, 129, 0.2);
          color: #34d399;
        }

        &.charge {
          background: rgba(245, 158, 11, 0.2);
          color: #fbbf24;
        }

        &.idle {
          background: rgba(107, 114, 128, 0.2);
          color: #9ca3af;
        }
      }

      .route-text {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: #d1d5db;

        :deep(.el-icon) {
          color: #6b7280;
          font-size: 12px;
        }
      }
    }
  }

  .replay-player {
    margin: 0 16px 16px;
    padding: 16px;
    background: linear-gradient(90deg, rgba(59, 130, 246, 0.15) 0%, rgba(139, 92, 246, 0.15) 100%);
    border: 1px solid rgba(59, 130, 246, 0.3);
    border-radius: 10px;

    .player-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .player-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
        font-weight: 600;
        color: #e0e7ff;
      }

      .player-controls {
        display: flex;
        gap: 8px;
      }
    }

    .player-progress {
      margin-bottom: 8px;

      :deep(.el-slider__runway) {
        background: rgba(0, 0, 0, 0.3);
      }

      :deep(.el-slider__bar) {
        background: linear-gradient(90deg, #3b82f6, #8b5cf6);
      }
    }

    .player-info {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: #9ca3af;
    }
  }
}
</style>
