<template>
  <div class="page-container">
    <div class="page-header">
      <h2>AGV管理</h2>
      <div class="header-actions">
        <el-button @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="4">
        <div class="stat-card status-idle">
          <div class="stat-value">{{ idleCount }}</div>
          <div class="stat-label">空闲</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card status-working">
          <div class="stat-value">{{ workingCount }}</div>
          <div class="stat-label">工作中</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card status-charging">
          <div class="stat-value">{{ chargingCount }}</div>
          <div class="stat-label">充电中</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card status-paused">
          <div class="stat-value">{{ pausedCount }}</div>
          <div class="stat-label">暂停</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card status-fault">
          <div class="stat-value">{{ faultCount }}</div>
          <div class="stat-label">故障</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card status-offline">
          <div class="stat-value">{{ offlineCount }}</div>
          <div class="stat-label">离线</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>AGV状态分布</span>
            </div>
          </template>
          <div ref="statusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>电量分布</span>
            </div>
          </template>
          <div ref="batteryChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>今日任务完成统计</span>
            </div>
          </template>
          <div ref="taskChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>AGV列表</span>
          <div class="filter-bar">
            <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 150px; margin-right: 10px">
              <el-option label="空闲" value="IDLE" />
              <el-option label="工作中" value="WORKING" />
              <el-option label="充电中" value="CHARGING" />
              <el-option label="故障" value="FAULT" />
              <el-option label="离线" value="OFFLINE" />
              <el-option label="暂停" value="PAUSED" />
            </el-select>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索AGV编号"
              clearable
              style="width: 200px"
              :prefix-icon="Search"
            />
          </div>
        </div>
      </template>

      <el-table
        :data="filteredAgvList"
        border
        stripe
        v-loading="loading"
      >
        <el-table-column prop="agvNo" label="AGV编号" width="120" fixed="left" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <span :class="['status-tag', getStatusClass(row.status)]">
              {{ getStatusName(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="电量" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.battery || 0"
              :color="getBatteryColor(row.battery)"
              :stroke-width="12"
            />
          </template>
        </el-table-column>
        <el-table-column prop="currentPosition" label="当前位置" width="120" />
        <el-table-column label="坐标" width="150">
          <template #default="{ row }">
            <span v-if="row.x !== null && row.y !== null">
              ({{ row.x?.toFixed(1) }}, {{ row.y?.toFixed(1) }})
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="angle" label="角度" width="80">
          <template #default="{ row }">
            <span>{{ row.angle !== null ? row.angle + '°' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="speed" label="速度" width="80">
          <template #default="{ row }">
            <span>{{ row.speed !== null ? row.speed + ' m/s' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentTaskId" label="当前任务" width="180">
          <template #default="{ row }">
            <span v-if="row.currentTaskId">{{ row.currentTaskId }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="loadCapacity" label="载重" width="100">
          <template #default="{ row }">
            <span>{{ row.loadCapacity !== null ? row.loadCapacity + ' kg' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180">
          <template #default="{ row }">
            <span>{{ row.lastHeartbeat ? formatDateTime(row.lastHeartbeat) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'WORKING'"
              type="warning"
              link
              @click="handlePause(row)"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'PAUSED'"
              type="success"
              link
              @click="handleResume(row)"
            >
              恢复
            </el-button>
            <el-button
              v-if="row.status === 'IDLE'"
              type="primary"
              link
              @click="handleCharge(row)"
            >
              呼叫充电
            </el-button>
            <el-button
              type="danger"
              link
              @click="handleStop(row)"
            >
              急停
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="detailDialogVisible"
      title="AGV详情"
      width="700px"
    >
      <el-descriptions :column="2" border v-if="currentAgv">
        <el-descriptions-item label="AGV编号">{{ currentAgv.agvNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <span :class="['status-tag', getStatusClass(currentAgv.status)]">
            {{ getStatusName(currentAgv.status) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="电量">
          <el-progress
            :percentage="currentAgv.battery || 0"
            :color="getBatteryColor(currentAgv.battery)"
            :stroke-width="12"
            style="width: 150px"
          />
        </el-descriptions-item>
        <el-descriptions-item label="当前位置">{{ currentAgv.currentPosition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="X坐标">{{ currentAgv.x !== null ? currentAgv.x.toFixed(2) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="Y坐标">{{ currentAgv.y !== null ? currentAgv.y.toFixed(2) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="角度">{{ currentAgv.angle !== null ? currentAgv.angle + '°' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="速度">{{ currentAgv.speed !== null ? currentAgv.speed + ' m/s' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="载重">{{ currentAgv.loadCapacity !== null ? currentAgv.loadCapacity + ' kg' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="最大载重">{{ currentAgv.maxLoadCapacity !== null ? currentAgv.maxLoadCapacity + ' kg' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前任务">{{ currentAgv.currentTaskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="今日完成任务">{{ currentAgv.todayTaskCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="最后心跳">
          {{ currentAgv.lastHeartbeat ? formatDateTime(currentAgv.lastHeartbeat) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="固件版本">{{ currentAgv.firmwareVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentAgv.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(currentAgv.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="故障代码" :span="2">
          <span v-if="currentAgv.faultCode" class="fault-code">{{ currentAgv.faultCode }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="故障信息" :span="2">
          <span v-if="currentAgv.faultMessage" class="fault-message">{{ currentAgv.faultMessage }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="detail-actions" style="margin-top: 20px">
        <el-button
          v-if="currentAgv?.status === 'WORKING'"
          type="warning"
          @click="handlePause(currentAgv)"
        >
          暂停
        </el-button>
        <el-button
          v-if="currentAgv?.status === 'PAUSED'"
          type="success"
          @click="handleResume(currentAgv)"
        >
          恢复
        </el-button>
        <el-button
          v-if="currentAgv?.status === 'IDLE'"
          type="primary"
          @click="handleCharge(currentAgv)"
        >
          呼叫充电
        </el-button>
        <el-button type="danger" @click="handleStop(currentAgv)">紧急停车</el-button>
      </div>
    </el-dialog>

    <el-dialog
      v-model="chargeDialogVisible"
      title="呼叫充电"
      width="400px"
    >
      <el-form :model="chargeForm" label-width="100px">
        <el-form-item label="AGV编号">
          <span>{{ currentAgv?.agvNo }}</span>
        </el-form-item>
        <el-form-item label="充电站" prop="chargingStation">
          <el-select v-model="chargeForm.chargingStation" placeholder="请选择充电站" style="width: 100%">
            <el-option label="D01充电站" value="D01" />
            <el-option label="D02充电站" value="D02" />
            <el-option label="D03充电站" value="D03" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chargeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmCharge">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { agvApi, websocketService } from '@/api'

const loading = ref(false)
const agvList = ref([])
const statusFilter = ref('')
const searchKeyword = ref('')

const statusChartRef = ref(null)
const batteryChartRef = ref(null)
const taskChartRef = ref(null)

let statusChart = null
let batteryChart = null
let taskChart = null
let stompClient = null

const detailDialogVisible = ref(false)
const chargeDialogVisible = ref(false)
const currentAgv = ref(null)

const chargeForm = reactive({
  chargingStation: ''
})

const idleCount = computed(() => agvList.value.filter(a => a.status === 'IDLE').length)
const workingCount = computed(() => agvList.value.filter(a => a.status === 'WORKING').length)
const chargingCount = computed(() => agvList.value.filter(a => a.status === 'CHARGING').length)
const pausedCount = computed(() => agvList.value.filter(a => a.status === 'PAUSED').length)
const faultCount = computed(() => agvList.value.filter(a => a.status === 'FAULT').length)
const offlineCount = computed(() => agvList.value.filter(a => a.status === 'OFFLINE').length)

const filteredAgvList = computed(() => {
  let list = agvList.value
  if (statusFilter.value) {
    list = list.filter(a => a.status === statusFilter.value)
  }
  if (searchKeyword.value) {
    list = list.filter(a =>
      a.agvNo.toLowerCase().includes(searchKeyword.value.toLowerCase())
    )
  }
  return list
})

const getStatusName = (status) => {
  const map = {
    IDLE: '空闲',
    WORKING: '工作中',
    CHARGING: '充电中',
    FAULT: '故障',
    OFFLINE: '离线',
    PAUSED: '暂停'
  }
  return map[status] || status
}

const getStatusClass = (status) => {
  const map = {
    IDLE: 'pending',
    WORKING: 'executing',
    CHARGING: 'assigned',
    FAULT: 'abnormal',
    OFFLINE: 'cancelled',
    PAUSED: 'pending'
  }
  return map[status] || 'pending'
}

const getBatteryColor = (battery) => {
  if (battery >= 60) return '#059669'
  if (battery >= 30) return '#d97706'
  return '#dc2626'
}

const formatDateTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const loadAgvData = async () => {
  loading.value = true
  try {
    const data = await agvApi.list()
    agvList.value = data || []
    updateCharts()
  } catch (error) {
    ElMessage.error(error.message || '加载AGV列表失败')
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => {
  loadAgvData()
}

const showDetail = (row) => {
  currentAgv.value = row
  detailDialogVisible.value = true
}

const handlePause = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要暂停AGV ${row.agvNo} 吗？`, '确认暂停', {
      type: 'warning'
    })
    await agvApi.pause(row.id)
    ElMessage.success('AGV已暂停')
    loadAgvData()
    detailDialogVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '暂停失败')
    }
  }
}

const handleResume = async (row) => {
  try {
    await agvApi.resume(row.id)
    ElMessage.success('AGV已恢复运行')
    loadAgvData()
    detailDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '恢复失败')
  }
}

const handleStop = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要紧急停车AGV ${row.agvNo} 吗？此操作会立即停止AGV！`,
      '确认紧急停车',
      {
        type: 'error',
        confirmButtonText: '确认急停',
        cancelButtonText: '取消'
      }
    )
    await agvApi.stop(row.id)
    ElMessage.success('AGV已紧急停车')
    loadAgvData()
    detailDialogVisible.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const handleCharge = (row) => {
  currentAgv.value = row
  chargeForm.chargingStation = ''
  chargeDialogVisible.value = true
}

const handleConfirmCharge = async () => {
  if (!chargeForm.chargingStation) {
    ElMessage.warning('请选择充电站')
    return
  }
  try {
    await agvApi.charge(currentAgv.value.id, chargeForm.chargingStation)
    ElMessage.success('充电呼叫已发送')
    chargeDialogVisible.value = false
    loadAgvData()
  } catch (error) {
    ElMessage.error(error.message || '呼叫充电失败')
  }
}

const initStatusChart = () => {
  if (!statusChartRef.value) return
  statusChart = echarts.init(statusChartRef.value)
  updateStatusChart()
}

const updateStatusChart = () => {
  if (!statusChart) return
  statusChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '0', type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data: [
        { value: idleCount.value, name: '空闲', itemStyle: { color: '#f59e0b' } },
        { value: workingCount.value, name: '工作中', itemStyle: { color: '#059669' } },
        { value: chargingCount.value, name: '充电中', itemStyle: { color: '#2563eb' } },
        { value: pausedCount.value, name: '暂停', itemStyle: { color: '#6b7280' } },
        { value: faultCount.value, name: '故障', itemStyle: { color: '#dc2626' } },
        { value: offlineCount.value, name: '离线', itemStyle: { color: '#78716c' } }
      ]
    }]
  })
}

const initBatteryChart = () => {
  if (!batteryChartRef.value) return
  batteryChart = echarts.init(batteryChartRef.value)
  updateBatteryChart()
}

const updateBatteryChart = () => {
  if (!batteryChart) return

  let high = 0, medium = 0, low = 0
  agvList.value.forEach(agv => {
    const b = agv.battery || 0
    if (b >= 60) high++
    else if (b >= 30) medium++
    else low++
  })

  batteryChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['充足(≥60%)', '中等(30-60%)', '低(<30%)'] },
    yAxis: { type: 'value' },
    series: [{
      type: 'bar',
      barWidth: '50%',
      data: [
        { value: high, itemStyle: { color: '#059669' } },
        { value: medium, itemStyle: { color: '#d97706' } },
        { value: low, itemStyle: { color: '#dc2626' } }
      ]
    }]
  })
}

const initTaskChart = () => {
  if (!taskChartRef.value) return
  taskChart = echarts.init(taskChartRef.value)
  updateTaskChart()
}

const updateTaskChart = () => {
  if (!taskChart) return

  const top5 = [...agvList.value]
    .sort((a, b) => (b.todayTaskCount || 0) - (a.todayTaskCount || 0))
    .slice(0, 5)

  taskChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: top5.map(a => a.agvNo) },
    series: [{
      type: 'bar',
      barWidth: '60%',
      data: top5.map(a => ({
        value: a.todayTaskCount || 0,
        itemStyle: { color: '#2563eb' }
      })),
      label: { show: true, position: 'right' }
    }]
  })
}

const updateCharts = () => {
  updateStatusChart()
  updateBatteryChart()
  updateTaskChart()
}

const initWebSocket = () => {
  try {
    stompClient = websocketService.connect()
    stompClient.connect({}, () => {
      stompClient.subscribe('/topic/agv-status', (message) => {
        const data = JSON.parse(message.body)
        agvList.value = data || []
        updateCharts()
      })
    })
  } catch (e) {
    console.log('WebSocket连接失败，使用轮询方式')
  }
}

const handleResize = () => {
  statusChart?.resize()
  batteryChart?.resize()
  taskChart?.resize()
}

onMounted(() => {
  loadAgvData()
  nextTick(() => {
    initStatusChart()
    initBatteryChart()
    initTaskChart()
  })
  initWebSocket()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  statusChart?.dispose()
  batteryChart?.dispose()
  taskChart?.dispose()
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
}

.status-idle {
  border-left: 4px solid #f59e0b;
}

.status-working {
  border-left: 4px solid #059669;
}

.status-charging {
  border-left: 4px solid #2563eb;
}

.status-paused {
  border-left: 4px solid #6b7280;
}

.status-fault {
  border-left: 4px solid #dc2626;
}

.status-offline {
  border-left: 4px solid #78716c;
}

.text-muted {
  color: #9ca3af;
}

.fault-code {
  color: #dc2626;
  font-weight: 600;
}

.fault-message {
  color: #dc2626;
}

.detail-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}
</style>
