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
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-item', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span v-if="tab.count > 0" class="tab-count">{{ tab.count }}</span>
      </div>
    </div>

    <div class="alert-list" ref="alertListRef">
      <div
        v-for="alert in filteredAlerts"
        :key="alert.id"
        :class="['alert-item', alert.level, { handled: alert.handled }]"
      >
        <div class="alert-icon">
          <el-icon v-if="alert.level === 'critical'"><Close /></el-icon>
          <el-icon v-else-if="alert.level === 'error'"><Warning /></el-icon>
          <el-icon v-else><InfoFilled /></el-icon>
        </div>

        <div class="alert-content">
          <div class="alert-header">
            <span class="alert-title">{{ alert.title }}</span>
            <span :class="['alert-type', alert.type]">
              {{ getTypeLabel(alert.type) }}
            </span>
          </div>
          <div class="alert-message">{{ alert.message }}</div>
          <div class="alert-meta">
            <span v-if="alert.agvNos.length > 0" class="meta-item">
              <el-icon><Van /></el-icon>
              {{ alert.agvNos.join(', ') }}
            </span>
            <span class="meta-item">
              <el-icon><Position /></el-icon>
              {{ alert.location }}
            </span>
            <span class="meta-item">
              <el-icon><Clock /></el-icon>
              {{ alert.timestamp }}
            </span>
          </div>
        </div>

        <div class="alert-actions">
          <el-tag
            v-if="alert.handled"
            type="success"
            size="small"
            effect="light"
          >
            已处理
          </el-tag>
          <el-button
            v-else
            type="primary"
            size="small"
            :icon="Check"
            @click="handleAlert(alert)"
          >
            处理
          </el-button>
        </div>
      </div>

      <div v-if="filteredAlerts.length === 0" class="empty-alerts">
        <el-icon><CircleCheck /></el-icon>
        <span>暂无{{ activeTab === 'unhandled' ? '未处理' : '' }}告警</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import {
  Warning, Close, InfoFilled,
  Van, Position, Clock, CircleCheck, Check
} from '@element-plus/icons-vue'

const props = defineProps({
  alerts: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['handleAlert'])

const activeTab = ref('unhandled')
const alertListRef = ref(null)

const criticalCount = computed(() => props.alerts.filter(a => a.level === 'critical' && !a.handled).length)
const errorCount = computed(() => props.alerts.filter(a => a.level === 'error' && !a.handled).length)
const warningCount = computed(() => props.alerts.filter(a => a.level === 'warning' && !a.handled).length)

const tabs = computed(() => [
  { key: 'unhandled', label: '未处理', count: props.alerts.filter(a => !a.handled).length },
  { key: 'all', label: '全部', count: props.alerts.length }
])

const filteredAlerts = computed(() => {
  let list = [...props.alerts]
  if (activeTab.value === 'unhandled') {
    list = list.filter(a => !a.handled)
  }
  return list.sort((a, b) => {
    const levelOrder = { critical: 0, error: 1, warning: 2 }
    if (a.handled !== b.handled) return a.handled ? 1 : -1
    return levelOrder[a.level] - levelOrder[b.level]
  })
})

const getTypeLabel = (type) => ({
  deadlock: '死锁',
  lowBattery: '低电量',
  pathBlocked: '路径阻塞',
  conflict: '路径冲突',
  obstacle: '障碍物',
  system: '系统异常'
}[type] || type)

const handleAlert = (alert) => {
  emit('handleAlert', alert)
}

watch(
  () => props.alerts,
  () => {
    nextTick(() => {
      if (alertListRef.value) {
        alertListRef.value.scrollTop = 0
      }
    })
  },
  { deep: true }
)
</script>

<style lang="scss" scoped>
.alert-panel {
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

      :deep(.el-icon) {
        color: #f59e0b;
      }
    }

    .alert-stats {
      display: flex;
      gap: 10px;

      .stat-badge {
        display: flex;
        align-items: center;
        gap: 4px;
        padding: 4px 10px;
        border-radius: 20px;
        font-size: 11px;
        font-weight: 600;

        &.critical {
          background: rgba(239, 68, 68, 0.2);
          color: #f87171;
        }

        &.error {
          background: rgba(249, 115, 22, 0.2);
          color: #fb923c;
        }

        &.warning {
          background: rgba(245, 158, 11, 0.2);
          color: #fbbf24;
        }
      }
    }
  }

  .alert-tabs {
    display: flex;
    padding: 10px 16px 0;
    gap: 8px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    .tab-item {
      position: relative;
      padding: 8px 16px;
      font-size: 13px;
      color: #9ca3af;
      cursor: pointer;
      transition: all 0.2s ease;
      border-radius: 8px 8px 0 0;

      &:hover {
        color: #e0e7ff;
        background: rgba(255, 255, 255, 0.05);
      }

      &.active {
        color: #60a5fa;
        background: rgba(59, 130, 246, 0.1);

        &::after {
          content: '';
          position: absolute;
          bottom: -1px;
          left: 0;
          right: 0;
          height: 2px;
          background: #3b82f6;
          border-radius: 2px 2px 0 0;
        }
      }

      .tab-count {
        display: inline-block;
        margin-left: 6px;
        padding: 1px 6px;
        background: rgba(255, 255, 255, 0.1);
        border-radius: 10px;
        font-size: 10px;
        font-weight: 600;
      }
    }
  }

  .alert-list {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
    min-height: 0;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.2);
      border-radius: 3px;
    }

    .alert-item {
      display: flex;
      gap: 12px;
      padding: 14px;
      margin-bottom: 10px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      transition: all 0.3s ease;
      animation: slideIn 0.3s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.08);
        transform: translateX(4px);
      }

      &.critical {
        border-left: 4px solid #ef4444;
        background: linear-gradient(90deg, rgba(239, 68, 68, 0.1) 0%, rgba(255, 255, 255, 0.05) 100%);

        .alert-icon {
          color: #ef4444;
          background: rgba(239, 68, 68, 0.2);
        }
      }

      &.error {
        border-left: 4px solid #f97316;
        background: linear-gradient(90deg, rgba(249, 115, 22, 0.1) 0%, rgba(255, 255, 255, 0.05) 100%);

        .alert-icon {
          color: #f97316;
          background: rgba(249, 115, 22, 0.2);
        }
      }

      &.warning {
        border-left: 4px solid #f59e0b;
        background: linear-gradient(90deg, rgba(245, 158, 11, 0.1) 0%, rgba(255, 255, 255, 0.05) 100%);

        .alert-icon {
          color: #f59e0b;
          background: rgba(245, 158, 11, 0.2);
        }
      }

      &.handled {
        opacity: 0.6;

        &:hover {
          opacity: 0.8;
        }
      }

      .alert-icon {
        flex-shrink: 0;
        width: 36px;
        height: 36px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;

        :deep(.el-icon) {
          font-size: 18px;
        }
      }

      .alert-content {
        flex: 1;
        min-width: 0;

        .alert-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 6px;

          .alert-title {
            font-size: 14px;
            font-weight: 600;
            color: #e0e7ff;
          }

          .alert-type {
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 10px;
            font-weight: 600;

            &.deadlock {
              background: rgba(239, 68, 68, 0.2);
              color: #f87171;
            }

            &.lowBattery {
              background: rgba(245, 158, 11, 0.2);
              color: #fbbf24;
            }

            &.pathBlocked {
              background: rgba(249, 115, 22, 0.2);
              color: #fb923c;
            }

            &.conflict {
              background: rgba(139, 92, 246, 0.2);
              color: #a78bfa;
            }

            &.obstacle {
              background: rgba(236, 72, 153, 0.2);
              color: #f472b6;
            }

            &.system {
              background: rgba(107, 114, 128, 0.2);
              color: #9ca3af;
            }
          }
        }

        .alert-message {
          font-size: 12px;
          color: #d1d5db;
          margin-bottom: 8px;
          line-height: 1.5;
        }

        .alert-meta {
          display: flex;
          flex-wrap: wrap;
          gap: 12px;

          .meta-item {
            display: flex;
            align-items: center;
            gap: 4px;
            font-size: 11px;
            color: #9ca3af;

            :deep(.el-icon) {
              font-size: 11px;
            }
          }
        }
      }

      .alert-actions {
        flex-shrink: 0;
        display: flex;
        align-items: center;
      }
    }

    .empty-alerts {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      color: #6b7280;
      font-size: 13px;
      gap: 12px;

      :deep(.el-icon) {
        font-size: 48px;
        opacity: 0.4;
        color: #10b981;
      }
    }
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
