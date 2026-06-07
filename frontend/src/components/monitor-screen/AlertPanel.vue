<template>
  <div class="alert-panel">
    <div class="panel-header">
      <h3>
        <el-icon><Warning /></el-icon>
        实时告警中心
      </h3>
      <div class="alert-stats">
        <span class="stat-badge critical">
          <el-icon><Close /></el-icon>
          {{ criticalCount }} 严重
        </span>
        <span class="stat-badge error">
          <el-icon><Warning /></el-icon>
          {{ errorCount }} 错误
        </span>
        <span class="stat-badge warning">
          <el-icon><InfoFilled /></el-icon>
          {{ warningCount }} 警告
        </span>
      </div>
    </div>

    <div class="alert-tabs">
      <div
        :class="['tab-item', { active: activeTab === 'unhandled' }]"
        @click="activeTab = 'unhandled'"
      >
        未处理
        <span class="tab-badge" v-if="unhandledCount > 0">{{ unhandledCount }}</span>
      </div>
      <div
        :class="['tab-item', { active: activeTab === 'all' }]"
        @click="activeTab = 'all'"
      >
        全部
      </div>
      <el-button
        v-if="unhandledCount > 0"
        size="small"
        type="danger"
        @click="handleResolveAll"
      >
        全部处理
      </el-button>
    </div>

    <div class="alert-list" ref="alertListRef">
      <div
        v-for="alert in filteredAlerts"
        :key="alert.id"
        :class="['alert-item', alert.level, { handled: alert.handled, 'unhandled': !alert.handled }]"
      >
        <div class="alert-icon">
          <el-icon v-if="alert.level === 'critical'"><Close /></el-icon>
          <el-icon v-else-if="alert.level === 'error'"><Warning /></el-icon>
          <el-icon v-else><InfoFilled /></el-icon>
        </div>

        <div class="alert-content">
          <div class="alert-header">
            <span class="alert-title">{{ alert.title }}</span>
            <span :class="['alert-type', getTypeClass(alert.type)]">
              {{ getTypeLabel(alert.type) }}
            </span>
            <span v-if="!alert.handled" class="unhandled-tag">未处理</span>
            <span v-else class="handled-tag">已处理</span>
          </div>
          <p class="alert-message">{{ alert.message }}</p>
          <div class="alert-meta">
            <span class="meta-item">
              <el-icon><Van /></el-icon>
              {{ alert.agvIds?.join(', ') || '系统' }}
            </span>
            <span class="meta-item" v-if="alert.location">
              <el-icon><Position /></el-icon>
              {{ alert.location }}
            </span>
            <span class="meta-item">
              <el-icon><Clock /></el-icon>
              {{ formatDateTime(alert.createTime) }}
            </span>
            <span class="meta-item" v-if="alert.handler">
              <el-icon><CircleCheck /></el-icon>
              {{ alert.handler }}
            </span>
          </div>
        </div>

        <div class="alert-actions">
          <el-button
            v-if="!alert.handled"
            type="primary"
            size="small"
            @click="openHandleDialog(alert)"
          >
            处理
          </el-button>
          <el-button
            v-else
            type="success"
            size="small"
            @click="showHandleResult(alert)"
          >
            查看
          </el-button>
        </div>
      </div>

      <el-empty
        v-if="filteredAlerts.length === 0"
        :description="activeTab === 'unhandled' ? '暂无未处理告警' : '暂无告警记录'"
      />
    </div>

    <el-dialog
      v-model="handleDialogVisible"
      title="处理告警"
      width="500px"
    >
      <el-form :model="handleForm" label-width="80px">
        <el-form-item label="告警标题">
          <span>{{ currentAlert?.title }}</span>
        </el-form-item>
        <el-form-item label="告警信息">
          <span>{{ currentAlert?.message }}</span>
        </el-form-item>
        <el-form-item label="处理结果" prop="handleResult">
          <el-radio-group v-model="handleForm.handleResult">
            <el-radio label="resolved">已解决</el-radio>
            <el-radio label="ignored">忽略</el-radio>
            <el-radio label="escalated">已升级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明" prop="remark">
          <el-input
            v-model="handleForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入处理说明"
          />
        </el-form-item>
        <el-form-item label="处理人" prop="handler">
          <el-input v-model="handleForm.handler" placeholder="请输入处理人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resultDialogVisible"
      title="告警处理结果"
      width="500px"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item label="告警标题">{{ currentAlert?.title }}</el-descriptions-item>
        <el-descriptions-item label="告警信息">{{ currentAlert?.message }}</el-descriptions-item>
        <el-descriptions-item label="处理结果">
          <span :class="['result-tag', currentAlert?.handleResult]">
            {{ getHandleResultLabel(currentAlert?.handleResult) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentAlert?.handler }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ formatDateTime(currentAlert?.handleTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Warning, Close, InfoFilled,
  Van, Position, Clock, CircleCheck
} from '@element-plus/icons-vue'
import { dispatchApi } from '@/api'
import {
  formatDateTime,
  getAlarmTypeLabel,
  getAlarmLevelColor,
  normalizeAlarmData
} from '@/utils/helpers'

const props = defineProps({
  alarms: {
    type: Array,
    default: () => []
  },
  autoRefresh: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['alarmHandled', 'update:alarms'])

const activeTab = ref('unhandled')
const handleDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const currentAlert = ref(null)
const alertListRef = ref(null)

const handleForm = ref({
  handleResult: 'resolved',
  remark: '',
  handler: ''
})

const filteredAlerts = computed(() => {
  let list = props.alarms || []
  if (activeTab.value === 'unhandled') {
    list = list.filter(a => !a.handled)
  }
  return list.slice(0, 10)
})

const criticalCount = computed(() => props.alarms.filter(a => a.level === 'critical' && !a.handled).length)
const errorCount = computed(() => props.alarms.filter(a => a.level === 'error' && !a.handled).length)
const warningCount = computed(() => props.alarms.filter(a => a.level === 'warning' && !a.handled).length)
const unhandledCount = computed(() => props.alarms.filter(a => !a.handled).length)

const getTypeLabel = (type) => getAlarmTypeLabel(type)

const getTypeClass = (type) => type?.toLowerCase() || ''

const getHandleResultLabel = (result) => ({
  resolved: '已解决',
  ignored: '忽略',
  escalated: '已升级'
}[result] || result)

const openHandleDialog = (alert) => {
  currentAlert.value = alert
  handleForm.value = {
    handleResult: 'resolved',
    remark: '',
    handler: 'admin'
  }
  handleDialogVisible.value = true
}

const showHandleResult = (alert) => {
  currentAlert.value = alert
  resultDialogVisible.value = true
}

const confirmHandle = async () => {
  if (!handleForm.value.handler) {
    ElMessage.warning('请输入处理人姓名')
    return
  }
  try {
    await dispatchApi.handleAlarm(
      currentAlert.value.id,
      `${handleForm.value.handleResult}: ${handleForm.value.remark}`,
      handleForm.value.handler
    )
    if (currentAlert.value) {
      currentAlert.value.handled = true
      currentAlert.value.handleTime = new Date().toISOString()
      currentAlert.value.handleResult = handleForm.value.handleResult
      currentAlert.value.handler = handleForm.value.handler
    }
    ElMessage.success('告警处理成功')
    handleDialogVisible.value = false
    emit('alarmHandled', currentAlert.value)
  } catch (e) {
    ElMessage.error(e.message || '告警处理失败')
  }
}

const handleResolveAll = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要处理所有 ${unhandledCount.value} 条未处理告警吗？`,
      '批量处理确认',
      { type: 'warning' }
    )
    await dispatchApi.resolveAllConflicts()
    props.alarms.forEach(a => {
      if (!a.handled) {
        a.handled = true
        a.handleTime = new Date().toISOString()
        a.handleResult = 'batch_resolved'
        a.handler = 'system'
      }
    })
    ElMessage.success('批量处理成功')
    emit('alarmHandled', { batch: true })
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量处理失败')
    }
  }
}

watch(() => props.alarms, () => {
  if (alertListRef.value) {
    alertListRef.value.scrollTop = 0
  }
}, { deep: true })
</script>

<style lang="scss" scoped>
.alert-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: rgba(15, 23, 42, 0.8);
  border-radius: 8px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  overflow: hidden;

  .panel-header {
    padding: 16px;
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.9) 0%, rgba(15, 23, 42, 0.9) 100%);
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);

    h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 12px 0;
      font-size: 16px;
      color: #f1f5f9;

      .el-icon {
        color: #f59e0b;
        font-size: 20px;
      }
    }
  }

  .alert-stats {
    display: flex;
    gap: 12px;

    .stat-badge {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;

      &.critical {
        background: rgba(239, 68, 68, 0.2);
        color: #ef4444;
      }

      &.error {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
      }

      &.warning {
        background: rgba(59, 130, 246, 0.2);
        color: #3b82f6;
      }

      .el-icon {
        font-size: 14px;
      }
    }
  }

  .alert-tabs {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    gap: 16px;
    background: rgba(15, 23, 42, 0.6);
    border-bottom: 1px solid rgba(59, 130, 246, 0.1);

    .tab-item {
      position: relative;
      padding: 6px 12px;
      cursor: pointer;
      color: #94a3b8;
      font-size: 14px;
      transition: all 0.3s ease;
      border-radius: 4px;

      &:hover {
        color: #3b82f6;
        background: rgba(59, 130, 246, 0.1);
      }

      &.active {
        color: #3b82f6;
        background: rgba(59, 130, 246, 0.15);

        .tab-badge {
          background: #3b82f6;
          color: #fff;
        }
      }
    }

    .tab-badge {
      display: inline-block;
      min-width: 18px;
      height: 18px;
      line-height: 18px;
      text-align: center;
      padding: 0 5px;
      margin-left: 4px;
      border-radius: 9px;
      background: #ef4444;
      color: #fff;
      font-size: 11px;
      font-weight: 600;
    }
  }

  .alert-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-track {
      background: rgba(15, 23, 42, 0.3);
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(59, 130, 246, 0.3);
      border-radius: 3px;
    }
  }

  .alert-item {
    display: flex;
    padding: 12px;
    margin-bottom: 8px;
    background: rgba(30, 41, 59, 0.8);
    border-radius: 6px;
    border-left: 3px solid transparent;
    transition: all 0.3s ease;

    &:hover {
      background: rgba(30, 41, 59, 0.95);
      transform: translateX(2px);
    }

    &.critical {
      border-left-color: #ef4444;

      &.unhandled {
        animation: criticalBlink 2s infinite;
      }
    }

    &.error {
      border-left-color: #f59e0b;
    }

    &.warning {
      border-left-color: #3b82f6;
    }

    &.handled {
      opacity: 0.6;
    }
  }

  @keyframes criticalBlink {
    0%, 100% { background: rgba(239, 68, 68, 0.1); }
    50% { background: rgba(239, 68, 68, 0.3); }
  }

  .alert-icon {
    flex-shrink: 0;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    margin-right: 12px;

    .critical & {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    .error & {
      background: rgba(245, 158, 11, 0.2);
      color: #f59e0b;
    }

    .warning & {
      background: rgba(59, 130, 246, 0.2);
      color: #3b82f6;
    }

    .el-icon {
      font-size: 18px;
    }
  }

  .alert-content {
    flex: 1;
    min-width: 0;
  }

  .alert-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;

    .alert-title {
      font-weight: 500;
      color: #f1f5f9;
      font-size: 14px;
    }

    .alert-type {
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 4px;
      background: rgba(100, 116, 139, 0.3);
      color: #94a3b8;

      &.deadlock {
        background: rgba(239, 68, 68, 0.2);
        color: #ef4444;
      }

      &.lowbattery {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
      }

      &.pathblocked {
        background: rgba(239, 68, 68, 0.2);
        color: #ef4444;
      }

      &.conflict {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
      }
    }

    .unhandled-tag {
      font-size: 10px;
      padding: 1px 6px;
      border-radius: 3px;
      background: #ef4444;
      color: #fff;
      animation: pulse 1.5s infinite;
    }

    .handled-tag {
      font-size: 10px;
      padding: 1px 6px;
      border-radius: 3px;
      background: #10b981;
      color: #fff;
    }
  }

  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.6; }
  }

  .alert-message {
    margin: 4px 0;
    color: #94a3b8;
    font-size: 13px;
    line-height: 1.5;
  }

  .alert-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 6px;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #64748b;

      .el-icon {
        font-size: 12px;
      }
    }
  }

  .alert-actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    margin-left: 12px;
  }

  .result-tag {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 500;

    &.resolved {
      background: rgba(16, 185, 129, 0.2);
      color: #10b981;
    }

    &.ignored {
      background: rgba(100, 116, 139, 0.2);
      color: #64748b;
    }

    &.escalated {
      background: rgba(245, 158, 11, 0.2);
      color: #f59e0b;
    }

    &.batch_resolved {
      background: rgba(59, 130, 246, 0.2);
      color: #3b82f6;
    }
  }
}
</style>
