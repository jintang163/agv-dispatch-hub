<template>
  <div class="page-container">
    <div class="page-header">
      <h2>调度控制</h2>
      <div class="header-actions">
        <el-button @click="handleInitGraph">
          <el-icon><SetUp /></el-icon>
          初始化地图
        </el-button>
        <el-button type="primary" @click="handleDetectConflicts">
          <el-icon><Warning /></el-icon>
          检测冲突
        </el-button>
        <el-button type="success" @click="handleResolveAll">
          <el-icon><Check /></el-icon>
          解决全部冲突
        </el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="6">
        <div class="stat-card conflict-active">
          <div class="stat-value">{{ unresolvedConflictCount }}</div>
          <div class="stat-label">未解决冲突</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card conflict-headon">
          <div class="stat-value">{{ headOnCount }}</div>
          <div class="stat-label">对向冲突</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card conflict-cross">
          <div class="stat-value">{{ crossCount }}</div>
          <div class="stat-label">交叉冲突</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card conflict-follow">
          <div class="stat-value">{{ followCount }}</div>
          <div class="stat-label">跟车冲突</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>仓库地图 - 实时路径</span>
            </div>
          </template>
          <div ref="mapChartRef" class="map-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>冲突类型分布</span>
            </div>
          </template>
          <div ref="conflictChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>冲突记录</span>
              <div class="filter-bar">
                <el-select v-model="conflictStatusFilter" placeholder="状态筛选" clearable style="width: 150px; margin-right: 10px">
                  <el-option label="未解决" value="PENDING" />
                  <el-option label="解决中" value="RESOLVING" />
                  <el-option label="已解决" value="RESOLVED" />
                  <el-option label="已忽略" value="IGNORED" />
                </el-select>
                <el-button @click="loadConflictList">刷新</el-button>
              </div>
            </div>
          </template>

          <el-table :data="conflictList" border stripe v-loading="conflictLoading">
            <el-table-column prop="id" label="冲突ID" width="100" />
            <el-table-column prop="conflictType" label="冲突类型" width="120">
              <template #default="{ row }">
                <el-tag :type="getConflictTypeClass(row.conflictType)">
                  {{ getConflictTypeName(row.conflictType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="agvId1" label="AGV1" width="120" />
            <el-table-column prop="agvId2" label="AGV2" width="120" />
            <el-table-column prop="conflictPoint" label="冲突点" width="100" />
            <el-table-column label="任务1优先级" width="120">
              <template #default="{ row }">
                <span v-if="row.task1Priority" :class="['priority-tag', row.task1Priority.toLowerCase()]">
                  {{ getPriorityName(row.task1Priority) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="任务2优先级" width="120">
              <template #default="{ row }">
                <span v-if="row.task2Priority" :class="['priority-tag', row.task2Priority.toLowerCase()]">
                  {{ getPriorityName(row.task2Priority) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="resolutionStrategy" label="解决策略" width="150">
              <template #default="{ row }">
                <span v-if="row.resolutionStrategy">{{ row.resolutionStrategy }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="让行AGV" width="100">
              <template #default="{ row }">
                <span v-if="row.yieldAgvId">{{ row.yieldAgvId }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getConflictStatusClass(row.status)">
                  {{ getConflictStatusName(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="发生时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="resolveTime" label="解决时间" width="180">
              <template #default="{ row }">
                {{ row.resolveTime ? formatDateTime(row.resolveTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING'"
                  type="primary"
                  size="small"
                  @click="handleResolveConflict(row)"
                >
                  解决
                </el-button>
                <el-button type="primary" link @click="showConflictDetail(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>路径规划测试</span>
            </div>
          </template>
          <el-form :model="pathForm" label-width="100px">
            <el-form-item label="起点">
              <el-input v-model="pathForm.startPoint" placeholder="如: A01" style="width: 200px" />
            </el-form-item>
            <el-form-item label="终点">
              <el-input v-model="pathForm.endPoint" placeholder="如: C04" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handlePlanPath">规划路径</el-button>
              <el-button @click="handleLoadOccupiedPaths">查看占用路径</el-button>
            </el-form-item>
          </el-form>
          <div v-if="plannedPath.length > 0" class="path-result">
            <h4>规划结果：</h4>
            <div class="path-display">
              <span
                v-for="(node, index) in plannedPath"
                :key="index"
                class="path-node"
              >
                {{ node }}
                <span v-if="index < plannedPath.length - 1" class="path-arrow">→</span>
              </span>
            </div>
            <div class="path-info">
              <span>总节点数：{{ plannedPath.length }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>当前占用路径</span>
            </div>
          </template>
          <div v-if="occupiedPaths.length > 0">
            <div
              v-for="(item, index) in occupiedPaths"
              :key="index"
              class="occupied-item"
            >
              <div class="occupied-header">
                <span class="agv-tag">AGV: {{ item.agvId }}</span>
                <span class="step-tag">步骤: {{ item.currentStep }}/{{ item.totalSteps }}</span>
              </div>
              <div class="occupied-path">
                <span
                  v-for="(node, nodeIndex) in item.path"
                  :key="nodeIndex"
                  :class="['path-node', { 'occupied': nodeIndex <= item.currentStep + 2, 'lookahead': nodeIndex > item.currentStep && nodeIndex <= item.currentStep + 2 }]"
                >
                  {{ node }}
                </span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无占用路径" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="detailDialogVisible"
      title="冲突详情"
      width="600px"
    >
      <el-descriptions :column="2" border v-if="currentConflict">
        <el-descriptions-item label="冲突ID">{{ currentConflict.id }}</el-descriptions-item>
        <el-descriptions-item label="冲突类型">
          <el-tag :type="getConflictTypeClass(currentConflict.conflictType)">
            {{ getConflictTypeName(currentConflict.conflictType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AGV1">{{ currentConflict.agvId1 }}</el-descriptions-item>
        <el-descriptions-item label="AGV2">{{ currentConflict.agvId2 }}</el-descriptions-item>
        <el-descriptions-item label="冲突点">{{ currentConflict.conflictPoint }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getConflictStatusClass(currentConflict.status)">
            {{ getConflictStatusName(currentConflict.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务1">{{ currentConflict.task1Id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务2">{{ currentConflict.task2Id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务1优先级">
          <span v-if="currentConflict.task1Priority" :class="['priority-tag', currentConflict.task1Priority.toLowerCase()]">
            {{ getPriorityName(currentConflict.task1Priority) }}
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="任务2优先级">
          <span v-if="currentConflict.task2Priority" :class="['priority-tag', currentConflict.task2Priority.toLowerCase()]">
            {{ getPriorityName(currentConflict.task2Priority) }}
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ formatDateTime(currentConflict.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="解决时间">{{ currentConflict.resolveTime ? formatDateTime(currentConflict.resolveTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="解决策略">{{ currentConflict.resolutionStrategy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="让行AGV">{{ currentConflict.yieldAgvId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="等待时间(秒)">{{ currentConflict.waitTime || 0 }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentConflict.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SetUp, Warning, Check } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { dispatchApi, websocketService } from '@/api'

const conflictLoading = ref(false)
const conflictList = ref([])
const conflictStatusFilter = ref('')

const unresolvedConflictCount = ref(0)
const headOnCount = ref(0)
const crossCount = ref(0)
const followCount = ref(0)

const mapChartRef = ref(null)
const conflictChartRef = ref(null)

let mapChart = null
let conflictChart = null
let stompClient = null

const detailDialogVisible = ref(false)
const currentConflict = ref(null)

const pathForm = reactive({
  startPoint: '',
  endPoint: ''
})

const plannedPath = ref([])
const occupiedPaths = ref([])

const getConflictTypeName = (type) => {
  const map = {
    HEAD_ON: '对向冲突',
    CROSS: '交叉冲突',
    FOLLOW: '跟车冲突',
    RESOURCE: '资源冲突'
  }
  return map[type] || type
}

const getConflictTypeClass = (type) => {
  const map = {
    HEAD_ON: 'danger',
    CROSS: 'warning',
    FOLLOW: 'info',
    RESOURCE: 'warning'
  }
  return map[type] || 'info'
}

const getConflictStatusName = (status) => {
  const map = {
    PENDING: '未解决',
    RESOLVING: '解决中',
    RESOLVED: '已解决',
    IGNORED: '已忽略'
  }
  return map[status] || status
}

const getConflictStatusClass = (status) => {
  const map = {
    PENDING: 'danger',
    RESOLVING: 'warning',
    RESOLVED: 'success',
    IGNORED: 'info'
  }
  return map[status] || 'info'
}

const getPriorityName = (priority) => {
  const map = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[priority] || priority
}

const formatDateTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const loadConflictList = async () => {
  conflictLoading.value = true
  try {
    let data
    if (conflictStatusFilter.value) {
      data = await dispatchApi.detectConflicts()
    } else {
      data = await dispatchApi.getUnresolvedConflicts()
    }
    conflictList.value = data || []
    calculateConflictStats()
    updateConflictChart()
  } catch (error) {
    ElMessage.error(error.message || '加载冲突列表失败')
  } finally {
    conflictLoading.value = false
  }
}

const calculateConflictStats = () => {
  const list = conflictList.value
  unresolvedConflictCount.value = list.filter(c => c.status === 'PENDING').length
  headOnCount.value = list.filter(c => c.conflictType === 'HEAD_ON' && c.status === 'PENDING').length
  crossCount.value = list.filter(c => c.conflictType === 'CROSS' && c.status === 'PENDING').length
  followCount.value = list.filter(c => c.conflictType === 'FOLLOW' && c.status === 'PENDING').length
}

const handleInitGraph = async () => {
  try {
    await dispatchApi.initGraph()
    ElMessage.success('地图初始化成功')
    updateMapChart()
  } catch (error) {
    ElMessage.error(error.message || '初始化失败')
  }
}

const handleDetectConflicts = async () => {
  try {
    await dispatchApi.detectConflicts()
    ElMessage.success('冲突检测完成')
    loadConflictList()
  } catch (error) {
    ElMessage.error(error.message || '冲突检测失败')
  }
}

const handleResolveAll = async () => {
  try {
    await ElMessageBox.confirm('确定要自动解决所有未解决的冲突吗？', '确认解决', {
      type: 'warning'
    })
    await dispatchApi.resolveAllConflicts()
    ElMessage.success('所有冲突已解决')
    loadConflictList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解决失败')
    }
  }
}

const handleResolveConflict = async (row) => {
  try {
    await dispatchApi.resolveConflict(row.id)
    ElMessage.success('冲突已解决')
    loadConflictList()
  } catch (error) {
    ElMessage.error(error.message || '解决失败')
  }
}

const showConflictDetail = (row) => {
  currentConflict.value = row
  detailDialogVisible.value = true
}

const handlePlanPath = async () => {
  if (!pathForm.startPoint || !pathForm.endPoint) {
    ElMessage.warning('请输入起点和终点')
    return
  }
  try {
    const data = await dispatchApi.planPath(pathForm.startPoint, pathForm.endPoint)
    plannedPath.value = data || []
    updateMapChart()
  } catch (error) {
    ElMessage.error(error.message || '路径规划失败')
  }
}

const handleLoadOccupiedPaths = async () => {
  try {
    const data = await dispatchApi.getOccupiedPaths()
    occupiedPaths.value = data || []
  } catch (error) {
    ElMessage.error(error.message || '获取占用路径失败')
  }
}

const initMapChart = () => {
  if (!mapChartRef.value) return
  mapChart = echarts.init(mapChartRef.value)
  updateMapChart()
}

const updateMapChart = () => {
  if (!mapChart) return

  const nodes = [
    { name: 'A01', x: 0, y: 0, type: 'normal' },
    { name: 'A02', x: 5, y: 0, type: 'normal' },
    { name: 'A03', x: 10, y: 0, type: 'normal' },
    { name: 'A04', x: 15, y: 0, type: 'normal' },
    { name: 'B01', x: 0, y: 5, type: 'normal' },
    { name: 'B02', x: 5, y: 5, type: 'normal' },
    { name: 'B03', x: 10, y: 5, type: 'normal' },
    { name: 'B04', x: 15, y: 5, type: 'normal' },
    { name: 'C01', x: 0, y: 10, type: 'normal' },
    { name: 'C02', x: 5, y: 10, type: 'normal' },
    { name: 'C03', x: 10, y: 10, type: 'normal' },
    { name: 'C04', x: 15, y: 10, type: 'normal' },
    { name: 'D01', x: 0, y: 15, type: 'charging' },
    { name: 'D02', x: 7.5, y: 15, type: 'charging' },
    { name: 'D03', x: 15, y: 15, type: 'charging' }
  ]

  const links = []
  for (let i = 0; i < nodes.length; i++) {
    for (let j = i + 1; j < nodes.length; j++) {
      const n1 = nodes[i]
      const n2 = nodes[j]
      const dist = Math.abs(n1.x - n2.x) + Math.abs(n1.y - n2.y)
      if (dist === 5) {
        links.push({ source: n1.name, target: n2.name })
      }
    }
  }

  let pathLineData = []
  if (plannedPath.value.length > 1) {
    for (let i = 0; i < plannedPath.value.length - 1; i++) {
      pathLineData.push([
        { name: plannedPath.value[i] },
        { name: plannedPath.value[i + 1] }
      ])
    }
  }

  const normalNodes = nodes.filter(n => n.type === 'normal')
  const chargingNodes = nodes.filter(n => n.type === 'charging')
  const pathNodes = plannedPath.value.length > 0
    ? nodes.filter(n => plannedPath.value.includes(n.name))
    : []

  mapChart.setOption({
    tooltip: { show: true },
    grid: { left: '10%', right: '10%', top: '10%', bottom: '10%' },
    xAxis: { show: false, min: -1, max: 16 },
    yAxis: { show: false, min: -1, max: 16, inverse: true },
    series: [
      {
        type: 'graph',
        layout: 'none',
        coordinateSystem: 'cartesian2d',
        data: nodes.map(n => ({
          name: n.name,
          x: n.x,
          y: n.y,
          symbolSize: n.type === 'charging' ? 30 : 20,
          itemStyle: {
            color: n.type === 'charging' ? '#3b82f6' : '#6b7280'
          }
        })),
        links: links.map(l => ({
          source: l.source,
          target: l.target,
          lineStyle: { color: '#d1d5db', width: 2 }
        })),
        label: { show: true, position: 'bottom', fontSize: 12, color: '#374151' },
        roam: false
      },
      {
        type: 'lines',
        coordinateSystem: 'cartesian2d',
        data: pathLineData,
        lineStyle: { color: '#dc2626', width: 4, opacity: 0.8 },
        effect: { show: true, period: 4, delay: 300, symbol: 'arrow', symbolSize: 8 }
      },
      {
        type: 'scatter',
        coordinateSystem: 'cartesian2d',
        data: pathNodes.map(n => ({
          name: n.name,
          value: [n.x, n.y]
        })),
        symbolSize: 25,
        itemStyle: { color: '#dc2626' },
        label: { show: false }
      }
    ]
  })
}

const initConflictChart = () => {
  if (!conflictChartRef.value) return
  conflictChart = echarts.init(conflictChartRef.value)
  updateConflictChart()
}

const updateConflictChart = () => {
  if (!conflictChart) return

  const totalHeadOn = conflictList.value.filter(c => c.conflictType === 'HEAD_ON').length
  const totalCross = conflictList.value.filter(c => c.conflictType === 'CROSS').length
  const totalFollow = conflictList.value.filter(c => c.conflictType === 'FOLLOW').length
  const totalResource = conflictList.value.filter(c => c.conflictType === 'RESOURCE').length

  conflictChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '0' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c} ({d}%)' },
      data: [
        { value: totalHeadOn, name: '对向冲突', itemStyle: { color: '#dc2626' } },
        { value: totalCross, name: '交叉冲突', itemStyle: { color: '#f59e0b' } },
        { value: totalFollow, name: '跟车冲突', itemStyle: { color: '#2563eb' } },
        { value: totalResource, name: '资源冲突', itemStyle: { color: '#8b5cf6' } }
      ]
    }]
  })
}

const initWebSocket = () => {
  try {
    stompClient = websocketService.connect()
    stompClient.connect({}, () => {
      stompClient.subscribe('/topic/conflicts', (message) => {
        const data = JSON.parse(message.body)
        conflictList.value = data || []
        calculateConflictStats()
        updateConflictChart()
      })
    })
  } catch (e) {
    console.log('WebSocket连接失败，使用轮询方式')
  }
}

const handleResize = () => {
  mapChart?.resize()
  conflictChart?.resize()
}

onMounted(() => {
  loadConflictList()
  handleLoadOccupiedPaths()
  nextTick(() => {
    initMapChart()
    initConflictChart()
  })
  initWebSocket()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  mapChart?.dispose()
  conflictChart?.dispose()
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

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conflict-active {
  border-left: 4px solid #dc2626;
}

.conflict-headon {
  border-left: 4px solid #b91c1c;
}

.conflict-cross {
  border-left: 4px solid #d97706;
}

.conflict-follow {
  border-left: 4px solid #2563eb;
}

.map-container {
  width: 100%;
  height: 350px;
}

.path-result {
  margin-top: 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;

  h4 {
    margin-bottom: 12px;
    color: #1f2937;
  }
}

.path-display {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.path-node {
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-weight: 500;
  color: #1f2937;

  &.occupied {
    background: #fee2e2;
    border-color: #fca5a5;
    color: #dc2626;
  }

  &.lookahead {
    background: #fef3c7;
    border-color: #fcd34d;
    color: #d97706;
  }
}

.path-arrow {
  color: #6b7280;
  margin-left: 8px;
}

.path-info {
  font-size: 13px;
  color: #6b7280;
}

.occupied-item {
  padding: 12px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.occupied-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.agv-tag {
  font-weight: 600;
  color: #1f2937;
}

.step-tag {
  font-size: 12px;
  color: #6b7280;
}

.occupied-path {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
</style>
