<template>
  <div class="task-queue-board">
    <div class="panel-header">
      <h3>
        <el-icon><Sort /></el-icon>
        任务队列看板
      </h3>
      <div class="queue-stats">
        <span class="stat pending">
          <el-icon><Clock /></el-icon>
          {{ pendingCount }} 待执行
        </span>
        <span class="stat executing">
          <el-icon><Van /></el-icon>
          {{ executingCount }} 执行中
        </span>
      </div>
    </div>

    <div class="queue-container">
      <div class="queue-column pending">
        <div class="column-header">
          <span class="column-title">待执行任务</span>
          <el-tag size="small" type="warning" round>{{ pendingTasks.length }}</el-tag>
        </div>
        <div class="task-list" ref="pendingListRef">
          <div
            v-for="(task, index) in pendingTasks"
            :key="task.id"
            class="task-card pending"
            draggable="true"
            @dragstart="handleDragStart($event, index, 'pending')"
            @dragover="handleDragOver"
            @drop="handleDrop($event, index, 'pending')"
            @dragend="handleDragEnd"
          >
            <div class="task-header">
              <span class="task-no">{{ task.taskNo }}</span>
              <span :class="['priority-tag', getTaskPriorityClass(task.priority)]">
                {{ getTaskPriorityLabel(task.priority) }}
              </span>
              <span class="task-type">{{ getTaskTypeLabel(task.taskType) }}</span>
            </div>
            <div class="task-body">
              <div class="task-route">
                <el-icon><Location /></el-icon>
                <span class="route-text">
                  <span class="point">{{ task.startPoint }}</span>
                  <el-icon class="arrow"><Right /></el-icon>
                  <span class="point">{{ task.endPoint }}</span>
                </span>
              </div>
              <div class="task-info">
                <span class="info-item">
                  <el-icon><Clock /></el-icon>
                  等待: {{ getWaitTime(task.createTime) }}
                </span>
                <span class="info-item" v-if="task.deadline">
                  <el-icon><Timer /></el-icon>
                  截止: {{ formatDateTime(task.deadline) }}
                </span>
              </div>
            </div>
            <div class="task-actions">
              <el-button
                type="primary"
                size="small"
                @click.stop="handleAutoAssign(task)"
              >
                自动分配
              </el-button>
              <el-button
                type="success"
                size="small"
                @click.stop="handleTopPriority(task)"
              >
                置顶
              </el-button>
            </div>
            <div class="drag-handle">
              <el-icon><Sort /></el-icon>
            </div>
          </div>
          <el-empty
            v-if="pendingTasks.length === 0"
            description="暂无待执行任务"
            :image-size="60"
          />
        </div>
      </div>

      <div class="queue-column executing">
        <div class="column-header">
          <span class="column-title">执行中任务</span>
          <el-tag size="small" type="success" round>{{ executingTasks.length }}</el-tag>
        </div>
        <div class="task-list">
          <div
            v-for="task in executingTasks"
            :key="task.id"
            class="task-card executing"
          >
            <div class="task-header">
              <span class="task-no">{{ task.taskNo }}</span>
              <span class="task-type">{{ getTaskTypeLabel(task.taskType) }}</span>
              <el-tag size="small" type="success" effect="light">
                <el-icon><Van /></el-icon>
                {{ task.assignedAgv }}
              </el-tag>
            </div>
            <div class="task-body">
              <div class="task-route">
                <el-icon><Location /></el-icon>
                <span class="route-text">
                  <span class="point">{{ task.startPoint }}</span>
                  <el-icon class="arrow"><Right /></el-icon>
                  <span class="point">{{ task.endPoint }}</span>
                </span>
              </div>
              <div class="task-path" v-if="task.currentPath && task.currentPath.length > 0">
                <span class="path-label">路径:</span>
                <span class="path-nodes">
                  {{ task.currentPath.join(' → ') }}
                </span>
              </div>
              <div class="task-progress">
                <div class="progress-header">
                  <span class="progress-label">执行进度</span>
                  <span class="progress-value">{{ Math.round(task.progress || 0) }}%</span>
                </div>
                <el-progress
                  :percentage="Math.round(task.progress || 0)"
                  :color="getProgressColor(task.progress)"
                  :stroke-width="6"
                />
              </div>
              <div class="task-info">
                <span class="info-item">
                  <el-icon><Clock /></el-icon>
                  已用: {{ getElapsedTime(task.startTime) }}
                </span>
                <span class="info-item">
                  <el-icon><Timer /></el-icon>
                  预计: {{ getEstimatedTime(task) }}
                </span>
              </div>
            </div>
            <div class="task-actions">
              <el-button
                type="warning"
                size="small"
                @click.stop="handlePauseTask(task)"
              >
                暂停
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click.stop="handleCancelTask(task)"
              >
                取消
              </el-button>
            </div>
          </div>
          <el-empty
            v-if="executingTasks.length === 0"
            description="暂无执行中任务"
            :image-size="60"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="topPriorityDialogVisible"
      title="调整任务优先级"
      width="400px"
    >
      <el-form :model="priorityForm" label-width="100px">
        <el-form-item label="任务编号">
          <span>{{ currentTask?.taskNo }}</span>
        </el-form-item>
        <el-form-item label="新优先级" prop="priority">
          <el-radio-group v-model="priorityForm.priority">
            <el-radio label="HIGH">高（插队到最前）</el-radio>
            <el-radio label="MEDIUM">中</el-radio>
            <el-radio label="LOW">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="操作人" prop="operator">
          <el-input v-model="priorityForm.operator" placeholder="请输入操作人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="topPriorityDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmUpdatePriority">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Sort, Clock, Van, Location, Right, Timer
} from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { taskApi, dispatchApi } from '@/api'
import {
  getTaskPriorityLabel,
  getTaskPriorityClass,
  getTaskTypeLabel,
  formatDateTime,
  getWaitTime,
  formatDuration
} from '@/utils/helpers'

const props = defineProps({
  pendingTasks: {
    type: Array,
    default: () => []
  },
  executingTasks: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['priorityUpdated', 'taskUpdated'])

const pendingListRef = ref(null)
const topPriorityDialogVisible = ref(false)
const currentTask = ref(null)
let sortableInstance = null

let dragIndex = -1
let dragSource = ''

const priorityForm = ref({
  priority: 'HIGH',
  operator: 'admin'
})

const pendingCount = computed(() => props.pendingTasks.length)
const executingCount = computed(() => props.executingTasks.length)

const getProgressColor = (progress) => {
  if (progress >= 80) return '#10b981'
  if (progress >= 50) return '#3b82f6'
  if (progress >= 20) return '#f59e0b'
  return '#ef4444'
}

const getElapsedTime = (startTime) => {
  if (!startTime) return '-'
  return formatDuration(new Date() - new Date(startTime))
}

const getEstimatedTime = (task) => {
  if (!task.startTime || !task.progress) return '-'
  const elapsed = new Date() - new Date(task.startTime)
  const estimated = elapsed / (task.progress / 100)
  const remaining = estimated - elapsed
  return formatDuration(remaining)
}

const handleDragStart = (event, index, source) => {
  dragIndex = index
  dragSource = source
  event.dataTransfer.effectAllowed = 'move'
}

const handleDragOver = (event) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

const handleDrop = async (event, targetIndex, targetSource) => {
  event.preventDefault()

  if (dragSource !== targetSource || dragIndex === -1 || dragIndex === targetIndex) {
    return
  }

  if (dragSource === 'pending') {
    const sourceTask = props.pendingTasks[dragIndex]
    const targetTask = props.pendingTasks[targetIndex]

    if (dragIndex < targetIndex) {
      ElMessage.warning('只能将任务向上移动（提高优先级）')
      handleDragEnd()
      return
    }

    try {
      await taskApi.updatePriority({
        taskId: sourceTask.id,
        priority: targetTask.priority,
        operator: 'admin'
      })
      ElMessage.success('任务优先级已调整')
      emit('priorityUpdated')
    } catch (e) {
      ElMessage.error(e.message || '调整失败')
    }
  }

  handleDragEnd()
}

const handleDragEnd = () => {
  dragIndex = -1
  dragSource = ''
}

const handleAutoAssign = async (task) => {
  try {
    await ElMessageBox.confirm(
      `确定要自动分配任务 ${task.taskNo} 吗？`,
      '自动分配确认',
      { type: 'info' }
    )
    await taskApi.autoAssign(task.id)
    ElMessage.success('自动分配成功')
    emit('taskUpdated')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '分配失败')
    }
  }
}

const handleTopPriority = (task) => {
  currentTask.value = task
  priorityForm.value = {
    priority: 'HIGH',
    operator: 'admin'
  }
  topPriorityDialogVisible.value = true
}

const confirmUpdatePriority = async () => {
  if (!priorityForm.value.operator) {
    ElMessage.warning('请输入操作人')
    return
  }
  try {
    await taskApi.updatePriority({
      taskId: currentTask.value.id,
      priority: priorityForm.value.priority,
      operator: priorityForm.value.operator
    })
    ElMessage.success('任务优先级已更新')
    topPriorityDialogVisible.value = false
    emit('priorityUpdated')
  } catch (e) {
    ElMessage.error(e.message || '更新失败')
  }
}

const handlePauseTask = async (task) => {
  try {
    await ElMessageBox.confirm(
      `确定要暂停任务 ${task.taskNo} 吗？`,
      '暂停确认',
      { type: 'warning' }
    )
    await dispatchApi.pauseTask(task.id, '手动暂停')
    ElMessage.success('任务已暂停')
    emit('taskUpdated')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '暂停失败')
    }
  }
}

const handleCancelTask = async (task) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消任务 ${task.taskNo} 吗？此操作不可撤销！`,
      '取消确认',
      { type: 'error', confirmButtonText: '确认取消' }
    )
    await dispatchApi.cancelTask(task.id, '手动取消')
    ElMessage.success('任务已取消')
    emit('taskUpdated')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '取消失败')
    }
  }
}

const initSortable = () => {
  nextTick(() => {
    if (pendingListRef.value && !sortableInstance) {
      sortableInstance = Sortable.create(pendingListRef.value, {
        animation: 200,
        handle: '.task-card',
        ghostClass: 'sortable-ghost',
        chosenClass: 'sortable-chosen',
        dragClass: 'sortable-drag',
        onEnd: (evt) => {
          if (evt.oldIndex === evt.newIndex) return

          if (evt.oldIndex < evt.newIndex) {
            ElMessage.warning('只能将任务向上移动（提高优先级）')
            loadData()
            return
          }

          const sourceTask = props.pendingTasks[evt.oldIndex]
          const targetTask = props.pendingTasks[evt.newIndex]

          taskApi.updatePriority({
            taskId: sourceTask.id,
            priority: targetTask.priority,
            operator: 'admin'
          }).then(() => {
            ElMessage.success('任务优先级已调整')
            emit('priorityUpdated')
          }).catch((e) => {
            ElMessage.error(e.message || '调整失败')
            loadData()
          })
        }
      })
    }
  })
}

const loadData = () => {
  emit('taskUpdated')
}

onMounted(() => {
  initSortable()
})
</script>

<style lang="scss" scoped>
.task-queue-board {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: rgba(15, 23, 42, 0.8);
  border-radius: 8px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  overflow: hidden;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.9) 0%, rgba(15, 23, 42, 0.9) 100%);
    border-bottom: 1px solid rgba(59, 130, 246, 0.2);

    h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 16px;
      color: #f1f5f9;

      .el-icon {
        color: '#fbbf24';
        font-size: 20px;
      }
    }
  }

  .queue-stats {
    display: flex;
    gap: 16px;

    .stat {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      padding: 4px 12px;
      border-radius: 12px;
      font-weight: 500;

      &.pending {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
      }

      &.executing {
        background: rgba(16, 185, 129, 0.2);
        color: #10b981;
      }

      .el-icon {
        font-size: 14px;
      }
    }
  }

  .queue-container {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    flex: 1;
    min-height: 0;
    padding: 12px;
  }

  .queue-column {
    display: flex;
    flex-direction: column;
    min-height: 0;
    background: rgba(30, 41, 59, 0.6);
    border-radius: 6px;
    border: 1px solid rgba(59, 130, 246, 0.15);

    &.pending {
      border-color: rgba(245, 158, 11, 0.3);
    }

    &.executing {
      border-color: rgba(16, 185, 129, 0.3);
    }
  }

  .column-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 12px;
    background: rgba(15, 23, 42, 0.6);
    border-bottom: 1px solid rgba(59, 130, 246, 0.1);
    border-radius: 6px 6px 0 0;

    .column-title {
      font-weight: 500;
      color: #f1f5f9;
      font-size: 14px;
    }
  }

  .task-list {
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

  .task-card {
    position: relative;
    padding: 12px;
    margin-bottom: 8px;
    background: rgba(30, 41, 59, 0.9);
    border-radius: 6px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    cursor: move;
    transition: all 0.3s ease;

    &:hover {
      border-color: rgba(59, 130, 246, 0.5);
      transform: translateX(2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    }

    &.pending {
      border-left: 3px solid #f59e0b;
    }

    &.executing {
      border-left: 3px solid #10b981;
    }

    &.sortable-ghost {
      opacity: 0.4;
      background: rgba(59, 130, 246, 0.2);
    }

    &.sortable-chosen {
      background: rgba(59, 130, 246, 0.3);
    }
  }

  .task-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .task-no {
      font-weight: 600;
      color: #f1f5f9;
      font-size: 13px;
    }

    .priority-tag {
      font-size: 10px;
      padding: 2px 6px;
      border-radius: 3px;
      font-weight: 600;

      &.high {
        background: rgba(220, 38, 38, 0.3);
        color: #dc2626;
      }

      &.medium {
        background: rgba(217, 119, 6, 0.3);
        color: #d97706;
      }

      &.low {
        background: rgba(5, 150, 105, 0.3);
        color: #059669;
      }
    }

    .task-type {
      font-size: 11px;
      padding: 2px 6px;
      background: rgba(100, 116, 139, 0.3);
      color: #94a3b8;
      border-radius: 3px;
    }
  }

  .task-body {
    .task-route {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 6px;
      font-size: 12px;
      color: #94a3b8;

      .route-text {
        display: flex;
        align-items: center;
        gap: 4px;

        .point {
          color: #f1f5f9;
          font-weight: 500;
        }

        .arrow {
          color: #3b82f6;
          font-size: 12px;
        }
      }
    }

    .task-path {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
      font-size: 11px;
      color: #64748b;

      .path-label {
        flex-shrink: 0;
      }

      .path-nodes {
        font-family: 'Courier New', monospace;
        color: #94a3b8;
      }
    }

    .task-progress {
      margin-bottom: 8px;

      .progress-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 4px;
        font-size: 12px;

        .progress-label {
          color: #94a3b8;
        }

        .progress-value {
          color: #f1f5f9;
          font-weight: 500;
        }
      }
    }

    .task-info {
      display: flex;
      gap: 16px;
      font-size: 11px;
      color: #64748b;

      .info-item {
        display: flex;
        align-items: center;
        gap: 4px;

        .el-icon {
          font-size: 12px;
        }
      }
    }
  }

  .task-actions {
    display: flex;
    gap: 8px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid rgba(59, 130, 246, 0.1);
  }

  .drag-handle {
    position: absolute;
    right: 8px;
    top: 50%;
    transform: translateY(-50%);
    color: rgba(100, 116, 139, 0.5);
    cursor: grab;

    &:active {
      cursor: grabbing;
    }
  }
}
</style>
