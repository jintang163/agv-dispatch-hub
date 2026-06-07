<template>
  <div class="operation-log">
    <div class="page-header">
      <h2>操作日志</h2>
      <div class="header-actions">
        <el-button :icon="Refresh" @click="loadLogs">刷新</el-button>
        <el-button
          v-if="userStore.hasPermission('log:export')"
          type="primary"
          :icon="Download"
          @click="handleExport"
        >
          导出
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon blue">
              <el-icon :size="32"><Operation /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.total || 0 }}</div>
              <div class="stat-label">总操作次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon green">
              <el-icon :size="32"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.success || 0 }}</div>
              <div class="stat-label">成功</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon red">
              <el-icon :size="32"><CircleClose /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.failed || 0 }}</div>
              <div class="stat-label">失败</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon purple">
              <el-icon :size="32"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.userCount || 0 }}</div>
              <div class="stat-label">活跃用户</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="操作人">
          <el-input
            v-model="filterForm.operator"
            placeholder="用户名"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filterForm.operationType" placeholder="全部" clearable style="width: 180px">
            <el-option
              v-for="type in operationTypes"
              :key="type.value"
              :label="type.label"
              :value="type.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="任务ID">
          <el-input
            v-model="filterForm.taskId"
            placeholder="任务ID"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="AGV ID">
          <el-input
            v-model="filterForm.agvId"
            placeholder="AGV ID"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.success" placeholder="全部" clearable style="width: 120px">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadLogs">搜索</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="logList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="操作类型" width="180">
          <template #default="{ row }">
            <el-tag :type="getOperationTypeTag(row.operationType)" size="small">
              {{ getOperationTypeLabel(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="operationIp" label="操作IP" width="140" />
        <el-table-column prop="taskNo" label="任务编号" width="140" />
        <el-table-column prop="agvNo" label="AGV编号" width="100" />
        <el-table-column label="操作描述" min-width="250">
          <template #default="{ row }">
            <el-tooltip :content="row.operationDetail" placement="top" :disabled="!row.operationDetail">
              <span>{{ row.operationDetail || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="执行时间" width="120" align="center">
          <template #default="{ row }">
            {{ row.executeTime ? row.executeTime + 'ms' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" width="180" align="center">
          <template #default="{ row }">
            {{ row.createTime }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="日志ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag :type="getOperationTypeTag(detailData.operationType)" size="small">
            {{ getOperationTypeLabel(detailData.operationType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailData.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detailData.operator }}</el-descriptions-item>
        <el-descriptions-item label="操作IP">{{ detailData.operationIp }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="任务编号">{{ detailData.taskNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="AGV编号">{{ detailData.agvNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行时间">{{ detailData.executeTime ? detailData.executeTime + 'ms' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailData.success ? 'success' : 'danger'" size="small">
            {{ detailData.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">
          {{ detailData.operationDetail || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2" v-if="detailData.errorMessage">
          <span style="color: #f56c6c">{{ detailData.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="修改前数据" :span="2" v-if="detailData.beforeData">
          <pre class="json-preview">{{ detailData.beforeData }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="修改后数据" :span="2" v-if="detailData.afterData">
          <pre class="json-preview">{{ detailData.afterData }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Download, Operation, CircleCheck, CircleClose, User } from '@element-plus/icons-vue'
import { operationLogApi } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const detailVisible = ref(false)
const detailData = ref({})
const logList = ref([])

const operationTypes = [
  { value: 'USER_LOGIN', label: '用户登录', type: '' },
  { value: 'USER_LOGOUT', label: '用户登出', type: '' },
  { value: 'TASK_CREATE', label: '任务创建', type: 'primary' },
  { value: 'TASK_DISPATCH', label: '任务下发', type: 'primary' },
  { value: 'TASK_PRIORITY_UPDATE', label: '任务插队', type: 'warning' },
  { value: 'TASK_CANCEL', label: '任务取消', type: 'danger' },
  { value: 'TASK_PAUSE', label: '任务暂停', type: 'warning' },
  { value: 'TASK_RESUME', label: '任务恢复', type: 'success' },
  { value: 'TASK_REASSIGN', label: '任务重分配', type: 'warning' },
  { value: 'TASK_COMPLETE', label: '任务完成', type: 'success' },
  { value: 'AGV_PAUSE', label: 'AGV暂停', type: 'warning' },
  { value: 'AGV_RESUME', label: 'AGV恢复', type: 'success' },
  { value: 'AGV_STOP', label: 'AGV急停', type: 'danger' },
  { value: 'AGV_CHARGE', label: 'AGV充电', type: 'primary' },
  { value: 'AGV_MANUAL_CONTROL', label: 'AGV手动控制', type: 'warning' },
  { value: 'CONFLICT_RESOLVE', label: '冲突解决', type: 'warning' },
  { value: 'DEADLOCK_RESOLVE', label: '死锁解决', type: 'danger' },
  { value: 'PATH_BLOCKED_CLEAR', label: '路径阻塞清除', type: 'success' },
  { value: 'ALARM_HANDLE', label: '告警处理', type: 'warning' },
  { value: 'USER_CREATE', label: '用户创建', type: 'primary' },
  { value: 'USER_UPDATE', label: '用户更新', type: 'primary' },
  { value: 'USER_DELETE', label: '用户删除', type: 'danger' },
  { value: 'SYSTEM_CONFIG_UPDATE', label: '系统配置更新', type: 'warning' }
]

const filterForm = reactive({
  operator: '',
  operationType: '',
  taskId: '',
  agvId: '',
  dateRange: [],
  success: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const statistics = reactive({
  total: 0,
  success: 0,
  failed: 0,
  userCount: 0
})

function getOperationTypeLabel(type) {
  const found = operationTypes.find(t => t.value === type)
  return found ? found.label : type
}

function getOperationTypeTag(type) {
  const found = operationTypes.find(t => t.value === type)
  return found ? found.type : 'info'
}

async function loadLogs() {
  loading.value = true
  try {
    const params = {
      operator: filterForm.operator || undefined,
      operationType: filterForm.operationType || undefined,
      taskId: filterForm.taskId || undefined,
      agvId: filterForm.agvId || undefined,
      startTime: filterForm.dateRange?.[0],
      endTime: filterForm.dateRange?.[1],
      success: filterForm.success,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = await operationLogApi.list(params)
    logList.value = res.content || res
    pagination.total = res.totalElements || res.length || 0
  } catch (e) {
    console.error('Load logs error:', e)
    loadMockLogs()
  } finally {
    loading.value = false
  }
}

function loadMockLogs() {
  logList.value = [
    { id: 1, operationType: 'USER_LOGIN', operator: 'admin', operatorName: '系统管理员', operationIp: '127.0.0.1', taskNo: null, agvNo: null, operationDetail: '用户登录系统', executeTime: 15, success: true, createTime: '2026-06-07 10:30:00', beforeData: null, afterData: null, errorMessage: null },
    { id: 2, operationType: 'TASK_CREATE', operator: 'dispatcher', operatorName: '调度员张三', operationIp: '192.168.1.100', taskNo: 'T202606070001', agvNo: null, operationDetail: '创建搬运任务：从N001到N010，优先级HIGH', executeTime: 25, success: true, createTime: '2026-06-07 10:32:00', beforeData: null, afterData: '{"taskType":"TRANSPORT","startPoint":"N001","endPoint":"N010"}', errorMessage: null },
    { id: 3, operationType: 'TASK_PRIORITY_UPDATE', operator: 'dispatcher', operatorName: '调度员张三', operationIp: '192.168.1.100', taskNo: 'T202606070001', agvNo: null, operationDetail: '任务插队：将任务优先级从MEDIUM调整为HIGH', executeTime: 18, success: true, createTime: '2026-06-07 10:33:00', beforeData: '{"priority":"MEDIUM"}', afterData: '{"priority":"HIGH"}', errorMessage: null },
    { id: 4, operationType: 'TASK_DISPATCH', operator: 'dispatcher', operatorName: '调度员张三', operationIp: '192.168.1.100', taskNo: 'T202606070001', agvNo: 'AGV001', operationDetail: '手动下发任务到AGV001', executeTime: 45, success: true, createTime: '2026-06-07 10:35:00', beforeData: null, afterData: null, errorMessage: null },
    { id: 5, operationType: 'AGV_MANUAL_CONTROL', operator: 'dispatcher', operatorName: '调度员张三', operationIp: '192.168.1.100', taskNo: null, agvNo: 'AGV001', operationDetail: '手动干预AGV001：执行暂停操作', executeTime: 22, success: true, createTime: '2026-06-07 10:40:00', beforeData: '{"status":"WORKING"}', afterData: '{"status":"PAUSED"}', errorMessage: null },
    { id: 6, operationType: 'AGV_PAUSE', operator: 'dispatcher', operatorName: '调度员张三', operationIp: '192.168.1.100', taskNo: null, agvNo: 'AGV001', operationDetail: '暂停AGV001', executeTime: 20, success: true, createTime: '2026-06-07 10:40:05', beforeData: null, afterData: null, errorMessage: null },
    { id: 7, operationType: 'ALARM_HANDLE', operator: 'admin', operatorName: '系统管理员', operationIp: '127.0.0.1', taskNo: null, agvNo: 'AGV002', operationDetail: '处理低电量告警：AGV002电量低于20%', executeTime: 30, success: true, createTime: '2026-06-07 10:45:00', beforeData: '{"level":"LOW","handled":false}', afterData: '{"level":"LOW","handled":true}', errorMessage: null },
    { id: 8, operationType: 'TASK_CANCEL', operator: 'dispatcher', operatorName: '调度员张三', operationIp: '192.168.1.100', taskNo: 'T202606070002', agvNo: null, operationDetail: '取消任务：用户取消订单', executeTime: 28, success: true, createTime: '2026-06-07 11:00:00', beforeData: null, afterData: null, errorMessage: null },
    { id: 9, operationType: 'CONFLICT_RESOLVE', operator: 'admin', operatorName: '系统管理员', operationIp: '127.0.0.1', taskNo: null, agvNo: 'AGV001,AGV002', operationDetail: '解决AGV001与AGV002在节点N005的路径冲突', executeTime: 55, success: true, createTime: '2026-06-07 11:10:00', beforeData: null, afterData: null, errorMessage: null },
    { id: 10, operationType: 'USER_CREATE', operator: 'admin', operatorName: '系统管理员', operationIp: '127.0.0.1', taskNo: null, agvNo: null, operationDetail: '创建新用户：testuser（调度员）', executeTime: 35, success: true, createTime: '2026-06-07 11:30:00', beforeData: null, afterData: '{"username":"testuser","role":"DISPATCHER"}', errorMessage: null }
  ]
  pagination.total = 10
  statistics.total = 156
  statistics.success = 148
  statistics.failed = 8
  statistics.userCount = 5
}

async function loadStatistics() {
  try {
    const params = {
      startTime: filterForm.dateRange?.[0],
      endTime: filterForm.dateRange?.[1]
    }
    const res = await operationLogApi.getStatistics(params)
    if (res) {
      Object.assign(statistics, res)
    }
  } catch (e) {
    console.error('Load statistics error:', e)
  }
}

function resetFilter() {
  filterForm.operator = ''
  filterForm.operationType = ''
  filterForm.taskId = ''
  filterForm.agvId = ''
  filterForm.dateRange = []
  filterForm.success = null
  pagination.pageNum = 1
  loadLogs()
  loadStatistics()
}

function viewDetail(row) {
  detailData.value = { ...row }
  detailVisible.value = true
}

function handleExport() {
  ElMessage.info('导出功能开发中...')
}

onMounted(() => {
  loadLogs()
  loadStatistics()
})
</script>

<style lang="scss" scoped>
.operation-log {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h2 {
      margin: 0;
      font-size: 20px;
      color: #303133;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .stat-cards {
    margin-bottom: 16px;

    .stat-card {
      :deep(.el-card__body) {
        padding: 16px;
      }

      .stat-content {
        display: flex;
        align-items: center;
        gap: 16px;

        .stat-icon {
          width: 56px;
          height: 56px;
          border-radius: 12px;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #fff;

          &.blue {
            background: linear-gradient(135deg, #667eea, #764ba2);
          }

          &.green {
            background: linear-gradient(135deg, #11998e, #38ef7d);
          }

          &.red {
            background: linear-gradient(135deg, #eb3349, #f45c43);
          }

          &.purple {
            background: linear-gradient(135deg, #8b5cf6, #a855f7);
          }
        }

        .stat-info {
          .stat-value {
            font-size: 28px;
            font-weight: 700;
            color: #303133;
            line-height: 1.2;
          }

          .stat-label {
            font-size: 13px;
            color: #909399;
            margin-top: 4px;
          }
        }
      }
    }
  }

  .filter-card {
    margin-bottom: 16px;
  }

  .table-card {
    .pagination {
      margin-top: 16px;
      display: flex;
      justify-content: flex-end;
    }
  }

  .json-preview {
    margin: 0;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;
    font-size: 12px;
    max-height: 200px;
    overflow-y: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
