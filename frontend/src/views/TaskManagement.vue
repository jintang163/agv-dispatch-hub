<template>
  <div class="page-container">
    <div class="page-header">
      <h2>任务管理</h2>
      <el-button type="primary" @click="showCreateDialog" v-if="userStore.hasPermission('task:create')">
        <el-icon><Plus /></el-icon>
        新建任务
      </el-button>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="任务编号">
          <el-input v-model="queryForm.taskNo" placeholder="请输入任务编号" clearable />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="queryForm.taskType" placeholder="请选择" clearable>
            <el-option label="搬运" value="TRANSPORT" />
            <el-option label="拣选" value="PICKING" />
            <el-option label="充电" value="CHARGING" />
            <el-option label="待命" value="STANDBY" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="queryForm.status" placeholder="请选择" clearable>
            <el-option label="待分配" value="PENDING" />
            <el-option label="已分配" value="ASSIGNED" />
            <el-option label="执行中" value="EXECUTING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="异常" value="ABNORMAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="queryForm.priority" placeholder="请选择" clearable>
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="AGV编号">
          <el-input v-model="queryForm.agvId" placeholder="请输入AGV编号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table
      :data="taskList"
      border
      stripe
      style="margin-top: 20px"
      v-loading="loading"
    >
      <el-table-column prop="taskNo" label="任务编号" width="180" />
      <el-table-column prop="taskType" label="任务类型" width="100">
        <template #default="{ row }">
          <span>{{ getTaskTypeName(row.taskType) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="100">
        <template #default="{ row }">
          <span :class="['priority-tag', row.priority.toLowerCase()]">
            {{ getPriorityName(row.priority) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <span :class="['status-tag', row.status.toLowerCase()]">
            {{ getStatusName(row.status) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="startPoint" label="起点" width="100" />
      <el-table-column prop="endPoint" label="终点" width="100" />
      <el-table-column prop="agvId" label="AGV编号" width="120" />
      <el-table-column prop="currentStep" label="进度" width="100">
        <template #default="{ row }">
          <span v-if="row.totalSteps > 0">
            {{ row.currentStep || 0 }}/{{ row.totalSteps }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="期望完成时间" width="180">
        <template #default="{ row }">
          <span>{{ row.deadline ? formatDateTime(row.deadline) : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180">
        <template #default="{ row }">
          <span>{{ formatDateTime(row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="showDetail(row)">详情</el-button>
          <el-button type="primary" link @click="showLogs(row)">日志</el-button>
          <el-button
            v-if="row.status === 'PENDING' && userStore.hasPermission('task:dispatch')"
            type="success"
            link
            @click="handleAssign(row)"
          >
            分配
          </el-button>
          <el-button
            v-if="(row.status === 'PENDING' || row.status === 'ASSIGNED') && userStore.hasPermission('task:update')"
            type="primary"
            link
            @click="handleUpdatePriority(row)"
          >
            调整优先级
          </el-button>
          <el-button
            v-if="row.status === 'ABNORMAL'"
            type="warning"
            link
            @click="handleReassign(row)"
          >
            重分配
          </el-button>
          <el-button
            v-if="row.status !== 'COMPLETED' && row.status !== 'CANCELLED' && userStore.hasPermission('task:cancel')"
            type="danger"
            link
            @click="handleCancel(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.size"
      :page-sizes="[10, 20, 50, 100]"
      :total="pagination.total"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 20px; justify-content: flex-end"
      @size-change="handleQuery"
      @current-change="handleQuery"
    />

    <el-dialog
      v-model="createDialogVisible"
      title="新建任务"
      width="600px"
      @close="resetCreateForm"
    >
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="120px">
        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="createForm.taskType" placeholder="请选择任务类型" style="width: 100%">
            <el-option label="搬运" value="TRANSPORT" />
            <el-option label="拣选" value="PICKING" />
            <el-option label="充电" value="CHARGING" />
            <el-option label="待命" value="STANDBY" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="createForm.priority" placeholder="请选择优先级" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="起点" prop="startPoint">
          <el-input v-model="createForm.startPoint" placeholder="请输入起点节点编号" />
        </el-form-item>
        <el-form-item label="终点" prop="endPoint">
          <el-input v-model="createForm.endPoint" placeholder="请输入终点节点编号" />
        </el-form-item>
        <el-form-item label="期望完成时间" prop="deadline">
          <el-date-picker
            v-model="createForm.deadline"
            type="datetime"
            placeholder="选择期望完成时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailDialogVisible"
      title="任务详情"
      width="700px"
    >
      <el-descriptions :column="2" border v-if="currentTask">
        <el-descriptions-item label="任务编号">{{ currentTask.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">
          {{ getTaskTypeName(currentTask.taskType) }}
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <span :class="['priority-tag', currentTask.priority.toLowerCase()]">
            {{ getPriorityName(currentTask.priority) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <span :class="['status-tag', currentTask.status.toLowerCase()]">
            {{ getStatusName(currentTask.status) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="起点">{{ currentTask.startPoint }}</el-descriptions-item>
        <el-descriptions-item label="终点">{{ currentTask.endPoint }}</el-descriptions-item>
        <el-descriptions-item label="AGV编号">{{ currentTask.agvId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前位置">{{ currentTask.currentPosition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="进度">
          {{ currentTask.totalSteps > 0 ? `${currentTask.currentStep || 0}/${currentTask.totalSteps}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="期望完成时间">
          {{ currentTask.deadline ? formatDateTime(currentTask.deadline) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(currentTask.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ currentTask.startTime ? formatDateTime(currentTask.startTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">
          {{ currentTask.completeTime ? formatDateTime(currentTask.completeTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentTask.operator || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          {{ currentTask.remark || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="路径" :span="2">
          {{ currentTask.path ? currentTask.path.join(' → ') : '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog
      v-model="logsDialogVisible"
      title="任务日志"
      width="700px"
    >
      <el-table :data="taskLogs" border stripe>
        <el-table-column prop="operation" label="操作" width="120" />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="oldStatus" label="原状态" width="100">
          <template #default="{ row }">
            <span v-if="row.oldStatus" :class="['status-tag', row.oldStatus.toLowerCase()]">
              {{ getStatusName(row.oldStatus) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="newStatus" label="新状态" width="100">
          <template #default="{ row }">
            <span :class="['status-tag', row.newStatus.toLowerCase()]">
              {{ getStatusName(row.newStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="createTime" label="操作时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="assignDialogVisible"
      title="任务分配"
      width="500px"
    >
      <el-form :model="assignForm" label-width="100px">
        <el-form-item label="任务编号">
          <span>{{ currentTask?.taskNo }}</span>
        </el-form-item>
        <el-form-item label="选择AGV" prop="agvId">
          <el-select v-model="assignForm.agvId" placeholder="请选择AGV" style="width: 100%">
            <el-option
              v-for="agv in availableAgvs"
              :key="agv.id"
              :label="`${agv.agvNo} (${getAgvStatusName(agv.status)})`"
              :value="agv.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmAssign">确定分配</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="priorityDialogVisible"
      title="调整优先级"
      width="400px"
    >
      <el-form :model="priorityForm" label-width="100px">
        <el-form-item label="任务编号">
          <span>{{ currentTask?.taskNo }}</span>
        </el-form-item>
        <el-form-item label="新优先级" prop="priority">
          <el-select v-model="priorityForm.priority" placeholder="请选择优先级" style="width: 100%">
            <el-option label="高（插队）" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="priorityForm.operator" placeholder="请输入操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priorityDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmPriority">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reassignDialogVisible"
      title="任务重分配"
      width="500px"
    >
      <el-form :model="reassignForm" label-width="100px">
        <el-form-item label="任务编号">
          <span>{{ currentTask?.taskNo }}</span>
        </el-form-item>
        <el-form-item label="重分配原因" prop="reason">
          <el-input
            v-model="reassignForm.reason"
            type="textarea"
            :rows="2"
            placeholder="请输入重分配原因"
          />
        </el-form-item>
        <el-form-item label="目标AGV" prop="targetAgvId">
          <el-select v-model="reassignForm.targetAgvId" placeholder="请选择目标AGV" style="width: 100%">
            <el-option
              v-for="agv in availableAgvs"
              :key="agv.id"
              :label="`${agv.agvNo} (${getAgvStatusName(agv.status)})`"
              :value="agv.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="reassignForm.operator" placeholder="请输入操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reassignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmReassign">确定重分配</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="cancelDialogVisible"
      title="取消任务"
      width="400px"
    >
      <el-form :model="cancelForm" label-width="100px">
        <el-form-item label="任务编号">
          <span>{{ currentTask?.taskNo }}</span>
        </el-form-item>
        <el-form-item label="取消原因" prop="reason">
          <el-input
            v-model="cancelForm.reason"
            type="textarea"
            :rows="2"
            placeholder="请输入取消原因"
          />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="cancelForm.operator" placeholder="请输入操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="handleConfirmCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { taskApi, agvApi } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const taskList = ref([])
const taskLogs = ref([])
const availableAgvs = ref([])
const currentTask = ref(null)

const createDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const logsDialogVisible = ref(false)
const assignDialogVisible = ref(false)
const priorityDialogVisible = ref(false)
const reassignDialogVisible = ref(false)
const cancelDialogVisible = ref(false)

const createFormRef = ref(null)

const queryForm = reactive({
  taskNo: '',
  taskType: '',
  status: '',
  priority: '',
  agvId: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const createForm = reactive({
  taskType: '',
  priority: 'MEDIUM',
  startPoint: '',
  endPoint: '',
  deadline: '',
  remark: ''
})

const createRules = {
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  startPoint: [{ required: true, message: '请输入起点', trigger: 'blur' }],
  endPoint: [{ required: true, message: '请输入终点', trigger: 'blur' }]
}

const assignForm = reactive({
  agvId: ''
})

const priorityForm = reactive({
  taskId: '',
  priority: '',
  operator: ''
})

const reassignForm = reactive({
  taskId: '',
  reason: '',
  targetAgvId: '',
  operator: ''
})

const cancelForm = reactive({
  taskId: '',
  reason: '',
  operator: ''
})

const getTaskTypeName = (type) => {
  const map = { TRANSPORT: '搬运', PICKING: '拣选', CHARGING: '充电', STANDBY: '待命' }
  return map[type] || type
}

const getPriorityName = (priority) => {
  const map = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[priority] || priority
}

const getStatusName = (status) => {
  const map = {
    PENDING: '待分配',
    ASSIGNED: '已分配',
    EXECUTING: '执行中',
    COMPLETED: '完成',
    CANCELLED: '取消',
    ABNORMAL: '异常'
  }
  return map[status] || status
}

const getAgvStatusName = (status) => {
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

const formatDateTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const handleQuery = async () => {
  loading.value = true
  try {
    const data = await taskApi.query({
      ...queryForm,
      page: pagination.page,
      size: pagination.size
    })
    taskList.value = data?.content || []
    pagination.total = data?.totalElements || 0
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  Object.assign(queryForm, {
    taskNo: '',
    taskType: '',
    status: '',
    priority: '',
    agvId: ''
  })
  pagination.page = 1
  handleQuery()
}

const showCreateDialog = () => {
  createDialogVisible.value = true
}

const resetCreateForm = () => {
  Object.assign(createForm, {
    taskType: '',
    priority: 'MEDIUM',
    startPoint: '',
    endPoint: '',
    deadline: '',
    remark: ''
  })
  createFormRef.value?.resetFields()
}

const handleCreate = async () => {
  await createFormRef.value?.validate()
  try {
    await taskApi.create(createForm)
    ElMessage.success('任务创建成功')
    createDialogVisible.value = false
    handleQuery()
  } catch (error) {
    ElMessage.error(error.message || '创建失败')
  }
}

const showDetail = (row) => {
  currentTask.value = row
  detailDialogVisible.value = true
}

const showLogs = async (row) => {
  try {
    const data = await taskApi.getLogs(row.id)
    taskLogs.value = data || []
    logsDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '获取日志失败')
  }
}

const handleAssign = async (row) => {
  currentTask.value = row
  try {
    availableAgvs.value = await agvApi.getAvailable()
    assignForm.agvId = ''
    assignDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '获取可用AGV失败')
  }
}

const handleConfirmAssign = async () => {
  if (!assignForm.agvId) {
    ElMessage.warning('请选择AGV')
    return
  }
  try {
    await taskApi.assign({
      taskId: currentTask.value.id,
      agvId: assignForm.agvId
    })
    ElMessage.success('任务分配成功')
    assignDialogVisible.value = false
    handleQuery()
  } catch (error) {
    ElMessage.error(error.message || '分配失败')
  }
}

const handleUpdatePriority = (row) => {
  currentTask.value = row
  priorityForm.taskId = row.id
  priorityForm.priority = row.priority
  priorityForm.operator = ''
  priorityDialogVisible.value = true
}

const handleConfirmPriority = async () => {
  if (!priorityForm.priority) {
    ElMessage.warning('请选择优先级')
    return
  }
  try {
    await taskApi.updatePriority(priorityForm)
    ElMessage.success('优先级调整成功')
    priorityDialogVisible.value = false
    handleQuery()
  } catch (error) {
    ElMessage.error(error.message || '调整失败')
  }
}

const handleReassign = async (row) => {
  currentTask.value = row
  reassignForm.taskId = row.id
  reassignForm.reason = ''
  reassignForm.targetAgvId = ''
  reassignForm.operator = ''
  try {
    availableAgvs.value = await agvApi.getAvailable()
    reassignDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '获取可用AGV失败')
  }
}

const handleConfirmReassign = async () => {
  if (!reassignForm.reason) {
    ElMessage.warning('请输入重分配原因')
    return
  }
  if (!reassignForm.targetAgvId) {
    ElMessage.warning('请选择目标AGV')
    return
  }
  try {
    await taskApi.reassign(reassignForm)
    ElMessage.success('任务重分配成功')
    reassignDialogVisible.value = false
    handleQuery()
  } catch (error) {
    ElMessage.error(error.message || '重分配失败')
  }
}

const handleCancel = (row) => {
  currentTask.value = row
  cancelForm.taskId = row.id
  cancelForm.reason = ''
  cancelForm.operator = ''
  cancelDialogVisible.value = true
}

const handleConfirmCancel = async () => {
  if (!cancelForm.reason) {
    ElMessage.warning('请输入取消原因')
    return
  }
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '确认取消', {
      type: 'warning'
    })
    await taskApi.cancel(cancelForm)
    ElMessage.success('任务取消成功')
    cancelDialogVisible.value = false
    handleQuery()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '取消失败')
    }
  }
}

onMounted(() => {
  handleQuery()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 20px;
}

:deep(.el-pagination) {
  display: flex;
}
</style>
