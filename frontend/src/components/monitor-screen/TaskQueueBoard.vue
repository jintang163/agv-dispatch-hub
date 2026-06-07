<template>
  <div class="task-queue-board">
    <div class="panel-header">
      <h3>
        <el-icon><List /></el-icon>
        任务队列看板
      </h3>
      <div class="queue-stats">
        <span class="stat-item">
          <span class="stat-count pending">{{ pendingTasks.length }}</span>
          <span class="stat-label">待执行</span>
        </span>
        <span class="stat-item">
          <span class="stat-count executing">{{ executingTasks.length }}</span>
          <span class="stat-label">执行中</span>
        </span>
      </div>
    </div>

    <div class="queue-content">
      <div class="queue-column">
        <div class="column-header pending">
          <el-icon><Clock /></el-icon>
          待执行任务
        </div>
        <div ref="pendingListRef" class="task-list">
          <div
            v-for="(task, index) in pendingTasks"
            :key="task.id"
            class="task-card pending"
            :data-id="task.id"
          >
            <div class="task-header">
              <span class="task-no">{{ task.taskNo }}</span>
              <span :class="['priority-tag', getPriorityClass(task.priority)]">
                {{ getPriorityLabel(task.priority) }}
              </span>
            </div>
            <div class="task-body">
              <div class="task-type">{{ getTaskTypeLabel(task.taskType) }}</div>
              <div class="task-route">
                <el-icon><Right /></el-icon>
                <span>{{ task.startPoint }}</span>
                <el-icon><ArrowRight /></el-icon>
                <span>{{ task.endPoint }}</span>
              </div>
            </div>
            <div class="task-footer">
              <span class="task-time">
                <el-icon><Timer /></el-icon>
                {{ formatTime(task.estimatedTime) }}
              </span>
              <span class="drag-hint">
                <el-icon><Sort /></el-icon>
                拖拽排序
              </span>
            </div>
          </div>
          <div v-if="pendingTasks.length === 0" class="empty-state">
            <el-icon><CircleCheck /></el-icon>
            <span>暂无待执行任务</span>
          </div>
        </div>
      </div>

      <div class="queue-column">
        <div class="column-header executing">
          <el-icon><VideoPlay /></el-icon>
          执行中任务
        </div>
        <div class="task-list">
          <div
            v-for="task in executingTasks"
            :key="task.id"
            class="task-card executing"
          >
            <div class="task-header">
              <span class="task-no">{{ task.taskNo }}</span>
              <span class="agv-tag">
                <el-icon><Van /></el-icon>
                {{ task.assignedAgv }}
              </span>
            </div>
            <div class="task-body">
              <div class="task-type">{{ getTaskTypeLabel(task.taskType) }}</div>
              <div class="task-route">
                <span>{{ task.startPoint }}</span>
                <el-icon><ArrowRight /></el-icon>
                <span>{{ task.endPoint }}</span>
              </div>
              <div class="task-progress">
                <div class="progress-header">
                  <span>执行进度</span>
                  <span>{{ task.progress }}%</span>
                </div>
                <el-progress
                  :percentage="task.progress"
                  :stroke-width="6"
                  :color="getProgressColor(task.progress)"
                />
              </div>
              <div class="task-path">
                <el-icon><Location /></el-icon>
                <span class="path-text">
                  {{ formatPath(task.currentPath, task.currentNodeIndex) }}
                </span>
              </div>
            </div>
            <div class="task-footer">
              <span class="task-time">
                <el-icon><Timer /></el-icon>
                已用时 {{ formatTime(task.elapsedTime) }}
              </span>
              <span class="task-time">
                <el-icon><Clock /></el-icon>
                预计 {{ formatTime(task.estimatedTime - task.elapsedTime) }}
              </span>
            </div>
          </div>
          <div v-if="executingTasks.length === 0" class="empty-state">
            <el-icon><CoffeeCup /></el-icon>
            <span>暂无执行中任务</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import Sortable from 'sortablejs'
import {
  List, Clock, VideoPlay, Right, ArrowRight, Timer, Sort,
  CircleCheck, Van, Location, CoffeeCup
} from '@element-plus/icons-vue'

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

const emit = defineEmits(['updatePriority'])

const pendingListRef = ref(null)
let sortableInstance = null

const getTaskTypeLabel = (type) => ({ 1: '搬运任务', 2: '拣选任务', 3: '充电任务', 4: '待命任务' }[type] || type)
const getPriorityLabel = (priority) => ({ 3: '高优先级', 2: '中优先级', 1: '低优先级' }[priority] || priority)
const getPriorityClass = (priority) => ({ 3: 'high', 2: 'medium', 1: 'low' }[priority] || '')

const formatTime = (seconds) => {
  if (!seconds || seconds < 0) return '0分0秒'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}分${secs}秒`
}

const getProgressColor = (progress) => {
  if (progress >= 80) return '#10b981'
  if (progress >= 50) return '#3b82f6'
  if (progress >= 20) return '#f59e0b'
  return '#ef4444'
}

const formatPath = (path, currentIndex) => {
  if (!path || path.length === 0) return ''
  return path.map((node, idx) => (
    idx <= currentIndex ? `<span class="passed">${node}</span>` :
    idx === currentIndex + 1 ? `<span class="current">${node}</span>` :
    `<span class="future">${node}</span>`
  )).join(' → ')
}

const initSortable = () => {
  if (!pendingListRef.value) return

  sortableInstance = Sortable.create(pendingListRef.value, {
    animation: 200,
    handle: '.task-card',
    ghostClass: 'sortable-ghost',
    chosenClass: 'sortable-chosen',
    dragClass: 'sortable-drag',
    onEnd: (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex !== newIndex) {
        const task = props.pendingTasks[oldIndex]
        emit('updatePriority', {
          taskId: task.id,
          oldIndex,
          newIndex
        })
      }
    }
  })
}

onMounted(() => {
  setTimeout(() => initSortable(), 100)
})

onUnmounted(() => {
  sortableInstance?.destroy()
})
</script>

<style lang="scss" scoped>
.task-queue-board {
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

    .queue-stats {
      display: flex;
      gap: 20px;

      .stat-item {
        display: flex;
        align-items: center;
        gap: 6px;

        .stat-count {
          font-size: 20px;
          font-weight: 700;

          &.pending { color: #f59e0b; }
          &.executing { color: #10b981; }
        }

        .stat-label {
          font-size: 12px;
          color: #9ca3af;
        }
      }
    }
  }

  .queue-content {
    flex: 1;
    display: flex;
    gap: 16px;
    padding: 16px;
    min-height: 0;

    .queue-column {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;

      .column-header {
        padding: 10px 14px;
        border-radius: 8px 8px 0 0;
        font-size: 14px;
        font-weight: 600;
        display: flex;
        align-items: center;
        gap: 8px;

        &.pending {
          background: linear-gradient(90deg, rgba(245, 158, 11, 0.2) 0%, transparent 100%);
          color: #fbbf24;
          border-left: 3px solid #f59e0b;
        }

        &.executing {
          background: linear-gradient(90deg, rgba(16, 185, 129, 0.2) 0%, transparent 100%);
          color: #34d399;
          border-left: 3px solid #10b981;
        }
      }

      .task-list {
        flex: 1;
        overflow-y: auto;
        padding: 12px;
        background: rgba(0, 0, 0, 0.2);
        border-radius: 0 0 8px 8px;
        min-height: 0;

        &::-webkit-scrollbar {
          width: 4px;
        }

        &::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.2);
          border-radius: 2px;
        }

        .task-card {
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: 8px;
          padding: 12px;
          margin-bottom: 12px;
          transition: all 0.3s ease;
          cursor: grab;

          &:hover {
            background: rgba(255, 255, 255, 0.08);
            border-color: rgba(59, 130, 246, 0.5);
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
          }

          &:active {
            cursor: grabbing;
          }

          &.pending {
            border-left: 3px solid #f59e0b;
          }

          &.executing {
            border-left: 3px solid #10b981;
            cursor: default;
          }

          .task-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;

            .task-no {
              font-size: 13px;
              font-weight: 600;
              color: #e0e7ff;
            }

            .priority-tag {
              padding: 2px 8px;
              border-radius: 4px;
              font-size: 11px;
              font-weight: 500;

              &.high {
                background: rgba(239, 68, 68, 0.2);
                color: #f87171;
              }

              &.medium {
                background: rgba(245, 158, 11, 0.2);
                color: #fbbf24;
              }

              &.low {
                background: rgba(16, 185, 129, 0.2);
                color: #34d399;
              }
            }

            .agv-tag {
              display: flex;
              align-items: center;
              gap: 4px;
              padding: 2px 8px;
              background: rgba(59, 130, 246, 0.2);
              color: #60a5fa;
              border-radius: 4px;
              font-size: 11px;
              font-weight: 500;
            }
          }

          .task-body {
            .task-type {
              font-size: 12px;
              color: #9ca3af;
              margin-bottom: 6px;
            }

            .task-route {
              display: flex;
              align-items: center;
              gap: 6px;
              font-size: 13px;
              color: #d1d5db;
              margin-bottom: 10px;

              :deep(.el-icon) {
                color: #6b7280;
              }
            }

            .task-progress {
              margin-bottom: 10px;

              .progress-header {
                display: flex;
                justify-content: space-between;
                font-size: 11px;
                color: #9ca3af;
                margin-bottom: 4px;
              }
            }

            .task-path {
              display: flex;
              align-items: flex-start;
              gap: 6px;
              font-size: 11px;
              color: #9ca3af;
              margin-bottom: 8px;

              :deep(.el-icon) {
                flex-shrink: 0;
                margin-top: 1px;
                color: #6b7280;
              }

              .path-text {
                :deep(.passed) {
                  color: #10b981;
                }

                :deep(.current) {
                  color: #3b82f6;
                  font-weight: 600;
                }

                :deep(.future) {
                  color: #6b7280;
                }
              }
            }
          }

          .task-footer {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-top: 8px;
            border-top: 1px solid rgba(255, 255, 255, 0.1);

            .task-time {
              display: flex;
              align-items: center;
              gap: 4px;
              font-size: 11px;
              color: #9ca3af;

              :deep(.el-icon) {
                color: #6b7280;
              }
            }

            .drag-hint {
              display: flex;
              align-items: center;
              gap: 4px;
              font-size: 10px;
              color: #6b7280;
              opacity: 0.8;
            }
          }
        }

        .empty-state {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 40px 20px;
          color: #6b7280;
          font-size: 13px;
          gap: 10px;

          :deep(.el-icon) {
            font-size: 32px;
            opacity: 0.5;
          }
        }
      }
    }
  }
}

.sortable-ghost {
  opacity: 0.4;
  background: rgba(59, 130, 246, 0.2) !important;
}

.sortable-chosen {
  background: rgba(59, 130, 246, 0.15) !important;
}

.sortable-drag {
  opacity: 0.9;
  background: rgba(30, 41, 59, 0.95) !important;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.4) !important;
}
</style>
