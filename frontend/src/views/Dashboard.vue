<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6" v-for="stat in statistics" :key="stat.key">
        <div class="stat-card" :style="{ borderLeft: `4px solid ${stat.color}` }">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
          <el-progress
            v-if="stat.progress !== undefined"
            :percentage="stat.progress"
            :stroke-width="6"
            :color="stat.color"
          />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <div class="page-container">
          <h3>任务状态分布</h3>
          <div ref="taskStatusChart" class="chart-container"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-container">
          <h3>AGV状态分布</h3>
          <div ref="agvStatusChart" class="chart-container"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <div class="page-container">
          <h3>今日任务趋势</h3>
          <div ref="taskTrendChart" class="chart-container"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-container">
          <h3>实时AGV位置</h3>
          <div ref="mapChart" class="chart-container" style="height: 400px;"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="table-row">
      <el-col :span="12">
        <div class="page-container">
          <h3>活跃任务</h3>
          <el-table :data="activeTasks" stripe size="small">
            <el-table-column prop="taskNo" label="任务编号" width="180" />
            <el-table-column prop="taskType" label="类型" width="80">
              <template #default="{ row }">
                <span>{{ getTaskTypeLabel(row.taskType) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="100">
              <template #default="{ row }">
                <span :class="['priority-tag', getPriorityClass(row.priority)]">
                  {{ getPriorityLabel(row.priority) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <span :class="['status-tag', getStatusClass(row.status)]">
                  {{ getStatusLabel(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="startPoint" label="起点" width="100" />
            <el-table-column prop="endPoint" label="终点" width="100" />
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-container">
          <h3>AGV列表</h3>
          <el-table :data="agvList" stripe size="small">
            <el-table-column prop="agvNo" label="AGV编号" width="100" />
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <span :class="['status-tag', getAgvStatusClass(row.status)]">
                  {{ getAgvStatusLabel(row.status) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="currentPosition" label="位置" width="100" />
            <el-table-column prop="batteryLevel" label="电量" width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.batteryLevel"
                  :stroke-width="10"
                  :color="getBatteryColor(row.batteryLevel)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import { taskApi, agvApi } from '@/api'

const taskStatusChart = ref(null)
const agvStatusChart = ref(null)
const taskTrendChart = ref(null)
const mapChart = ref(null)

let taskStatusChartInstance = null
let agvStatusChartInstance = null
let taskTrendChartInstance = null
let mapChartInstance = null

const taskStatistics = ref(null)
const agvStatistics = ref(null)
const activeTasks = ref([])
const agvList = ref([])

const statistics = computed(() => [
  { key: 'pending', label: '待分配', value: taskStatistics.value?.pendingCount || 0, color: '#f59e0b', progress: 20 },
  { key: 'executing', label: '执行中', value: taskStatistics.value?.executingCount || 0, color: '#10b981', progress: 45 },
  { key: 'completed', label: '今日完成', value: taskStatistics.value?.todayCompletedCount || 0, color: '#3b82f6', progress: 80 },
  { key: 'abnormal', label: '今日异常', value: taskStatistics.value?.todayAbnormalCount || 0, color: '#ef4444', progress: 5 }
])

const loadData = async () => {
  try {
    taskStatistics.value = await taskApi.getStatistics()
    agvStatistics.value = await agvApi.getStatistics()
    await loadActiveTasks()
    await loadAgvList()
    updateCharts()
  } catch (e) {
    console.error('加载数据失败', e)
  }
}

const loadActiveTasks = async () => {
  const res = await taskApi.query({ pageNum: 1, pageSize: 10, status: 2 })
  activeTasks.value = res?.content || []
}

const loadAgvList = async () => {
  agvList.value = await agvApi.list()
}

const updateCharts = () => {
  updateTaskStatusChart()
  updateAgvStatusChart()
  updateTaskTrendChart()
  updateMapChart()
}

const updateTaskStatusChart = () => {
  if (!taskStatusChartInstance) return
  const data = [
    { value: taskStatistics.value?.pendingCount || 0, name: '待分配', itemStyle: { color: '#f59e0b' } },
    { value: taskStatistics.value?.assignedCount || 0, name: '已分配', itemStyle: { color: '#3b82f6' } },
    { value: taskStatistics.value?.executingCount || 0, name: '执行中', itemStyle: { color: '#10b981' } },
    { value: taskStatistics.value?.todayCompletedCount || 0, name: '已完成', itemStyle: { color: '#6b7280' } }
  ]
  taskStatusChartInstance.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c} ({d}%)' },
      data
    }]
  })
}

const updateAgvStatusChart = () => {
  if (!agvStatusChartInstance) return
  const data = [
    { value: agvStatistics.value?.idle || 0, name: '空闲', itemStyle: { color: '#10b981' } },
    { value: agvStatistics.value?.working || 0, name: '工作中', itemStyle: { color: '#3b82f6' } },
    { value: agvStatistics.value?.charging || 0, name: '充电中', itemStyle: { color: '#f59e0b' } },
    { value: agvStatistics.value?.fault || 0, name: '故障', itemStyle: { color: '#ef4444' } },
    { value: agvStatistics.value?.offline || 0, name: '离线', itemStyle: { color: '#6b7280' } },
    { value: agvStatistics.value?.paused || 0, name: '暂停', itemStyle: { color: '#8b5cf6' } }
  ]
  agvStatusChartInstance.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [{
      type: 'pie',
      radius: '60%',
      data
    }]
  })
}

const updateTaskTrendChart = () => {
  if (!taskTrendChartInstance) return
  const hours = Array.from({ length: 12 }, (_, i) => `${i * 2}:00`)
  const completedData = [5, 8, 12, 15, 18, 22, 25, 28, 30, 32, 35, 38]
  const createdData = [8, 10, 15, 18, 20, 25, 28, 30, 28, 25, 22, 20]

  taskTrendChartInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['创建任务', '完成任务'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: hours },
    yAxis: { type: 'value' },
    series: [
      {
        name: '创建任务',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: createdData,
        itemStyle: { color: '#3b82f6' }
      },
      {
        name: '完成任务',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: completedData,
        itemStyle: { color: '#10b981' }
      }
    ]
  })
}

const updateMapChart = () => {
  if (!mapChartInstance) return

  const nodes = [
    { name: 'A01', x: 0, y: 0 },
    { name: 'A02', x: 5, y: 0 },
    { name: 'A03', x: 10, y: 0 },
    { name: 'A04', x: 15, y: 0 },
    { name: 'B01', x: 0, y: 5 },
    { name: 'B02', x: 5, y: 5 },
    { name: 'B03', x: 10, y: 5 },
    { name: 'B04', x: 15, y: 5 },
    { name: 'C01', x: 0, y: 10 },
    { name: 'C02', x: 5, y: 10 },
    { name: 'C03', x: 10, y: 10 },
    { name: 'C04', x: 15, y: 10 },
    { name: 'D01', x: 0, y: 15 },
    { name: 'D02', x: 7.5, y: 15 },
    { name: 'D03', x: 15, y: 15 }
  ]

  const agvPoints = agvList.value.map(agv => ({
    name: agv.agvNo,
    value: [agv.xCoord || 0, agv.yCoord || 0],
    symbolSize: 20,
    itemStyle: { color: getAgvStatusColor(agv.status) }
  }))

  mapChartInstance.setOption({
    tooltip: { trigger: 'item' },
    grid: { left: '10%', right: '10%', top: '10%', bottom: '10%' },
    xAxis: {
      type: 'value',
      min: -1,
      max: 16,
      splitLine: { show: true, lineStyle: { type: 'dashed' } }
    },
    yAxis: {
      type: 'value',
      min: -1,
      max: 16,
      inverse: true,
      splitLine: { show: true, lineStyle: { type: 'dashed' } }
    },
    series: [
      {
        type: 'scatter',
        data: nodes.map(n => ({ name: n.name, value: [n.x, n.y] })),
        symbolSize: 30,
        label: { show: true, formatter: '{b}', position: 'inside', color: '#fff' },
        itemStyle: { color: '#93c5fd' }
      },
      {
        type: 'scatter',
        data: agvPoints,
        label: { show: true, formatter: '{b}', position: 'top', color: '#1f2937' }
      }
    ]
  })
}

const getTaskTypeLabel = (type) => ({ 1: '搬运', 2: '拣选', 3: '充电', 4: '待命' }[type] || type)
const getStatusLabel = (status) => ({ 0: '待分配', 1: '已分配', 2: '执行中', 3: '完成', 4: '取消', 5: '异常' }[status] || status)
const getStatusClass = (status) => ({ 0: 'pending', 1: 'assigned', 2: 'executing', 3: 'completed', 4: 'cancelled', 5: 'abnormal' }[status] || '')
const getPriorityLabel = (priority) => ({ 3: '高', 2: '中', 1: '低' }[priority] || priority)
const getPriorityClass = (priority) => ({ 3: 'high', 2: 'medium', 1: 'low' }[priority] || '')
const getAgvStatusLabel = (status) => ({ 0: '空闲', 1: '工作中', 2: '充电中', 3: '故障', 4: '离线', 5: '暂停' }[status] || status)
const getAgvStatusClass = (status) => ({ 0: 'executing', 1: 'assigned', 2: 'pending', 3: 'abnormal', 4: 'cancelled', 5: 'pending' }[status] || '')
const getAgvStatusColor = (status) => ({ 0: '#10b981', 1: '#3b82f6', 2: '#f59e0b', 3: '#ef4444', 4: '#6b7280', 5: '#8b5cf6' }[status] || '#6b7280')
const getBatteryColor = (level) => level > 60 ? '#10b981' : level > 30 ? '#f59e0b' : '#ef4444'

let timer = null

onMounted(() => {
  taskStatusChartInstance = echarts.init(taskStatusChart.value)
  agvStatusChartInstance = echarts.init(agvStatusChart.value)
  taskTrendChartInstance = echarts.init(taskTrendChart.value)
  mapChartInstance = echarts.init(mapChart.value)

  loadData()
  timer = setInterval(loadData, 5000)

  window.addEventListener('resize', () => {
    taskStatusChartInstance?.resize()
    agvStatusChartInstance?.resize()
    taskTrendChartInstance?.resize()
    mapChartInstance?.resize()
  })
})

onUnmounted(() => {
  clearInterval(timer)
  taskStatusChartInstance?.dispose()
  agvStatusChartInstance?.dispose()
  taskTrendChartInstance?.dispose()
  mapChartInstance?.dispose()
})
</script>

<style lang="scss" scoped>
.dashboard {
  .stat-row {
    margin-bottom: 20px;
  }

  .chart-row {
    margin-bottom: 20px;
  }

  .table-row {
    margin-bottom: 20px;
  }

  h3 {
    margin: 0 0 16px 0;
    font-size: 16px;
    font-weight: 500;
    color: #1f2937;
  }
}
</style>
