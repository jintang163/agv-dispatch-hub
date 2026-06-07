<template>
  <div class="agv-status-cards">
    <div class="panel-header">
      <h3>
        <el-icon><Van /></el-icon>
        AGV 状态监控
      </h3>
      <div class="agv-summary">
        <span class="summary-item">
          <span class="summary-dot" style="background: #10b981;"></span>
          空闲 {{ statusCounts.idle }}
        </span>
        <span class="summary-item">
          <span class="summary-dot" style="background: #3b82f6;"></span>
          工作中 {{ statusCounts.working }}
        </span>
        <span class="summary-item">
          <span class="summary-dot" style="background: #f59e0b;"></span>
          充电中 {{ statusCounts.charging }}
        </span>
        <span class="summary-item">
          <span class="summary-dot" style="background: #ef4444;"></span>
          故障 {{ statusCounts.fault }}
        </span>
      </div>
    </div>

    <div class="cards-container">
      <div
        v-for="agv in agvList"
        :key="agv.agvNo"
        class="agv-card"
        :class="`status-${agv.status}`"
      >
        <div class="card-header">
          <div class="agv-info">
            <div class="agv-icon" :style="{ background: getStatusColor(agv.status) + '20' }">
              <el-icon :style="{ color: getStatusColor(agv.status) }"><Van /></el-icon>
            </div>
            <div class="agv-basic">
              <div class="agv-no">{{ agv.agvNo }}</div>
              <div class="agv-name">{{ agv.name }}</div>
            </div>
          </div>
          <span class="status-badge" :style="{ background: getStatusColor(agv.status) + '20', color: getStatusColor(agv.status) }">
            <span class="status-dot" :style="{ background: getStatusColor(agv.status) }"></span>
            {{ getStatusLabel(agv.status) }}
          </span>
        </div>

        <div class="card-body">
          <div class="info-row">
            <div class="info-item">
              <span class="info-label">
                <el-icon><Position /></el-icon>
                当前位置
              </span>
              <span class="info-value">{{ agv.currentPosition }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">
                <el-icon><SetUp /></el-icon>
                运行速度
              </span>
              <span class="info-value">{{ agv.speed.toFixed(1) }} m/s</span>
            </div>
          </div>

          <div class="info-row">
            <div class="info-item">
              <span class="info-label">
                <el-icon><Goods /></el-icon>
                载重
              </span>
              <span class="info-value">{{ agv.loadWeight }} kg</span>
            </div>
            <div class="info-item">
              <span class="info-label">
                <el-icon><Tickets /></el-icon>
                当前任务
              </span>
              <span class="info-value task-value" :title="agv.currentTask">
                {{ agv.currentTask || '无' }}
              </span>
            </div>
          </div>

          <div class="battery-section">
            <div class="battery-header">
              <span class="battery-label">
                <el-icon :class="getBatteryIcon(agv.batteryLevel)">
                  <component :is="getBatteryIcon(agv.batteryLevel)" />
                </el-icon>
                电量
              </span>
              <span class="battery-percent" :style="{ color: getBatteryColor(agv.batteryLevel) }">
                {{ agv.batteryLevel }}%
              </span>
            </div>
            <div class="battery-bar">
              <div
                class="battery-fill"
                :style="{
                  width: agv.batteryLevel + '%',
                  background: getBatteryGradient(agv.batteryLevel)
                }"
              ></div>
            </div>
            <div v-if="agv.batteryLevel < 30" class="battery-warning">
              <el-icon><Warning /></el-icon>
              电量不足，请及时充电
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  Van, Position, SetUp, Goods, Tickets, Warning,
  MagicStick, Coin, Female, Male
} from '@element-plus/icons-vue'

const props = defineProps({
  agvList: {
    type: Array,
    default: () => []
  }
})

const statusCounts = computed(() => {
  const counts = { idle: 0, working: 0, charging: 0, fault: 0, offline: 0, paused: 0 }
  props.agvList.forEach(agv => {
    switch (agv.status) {
      case 0: counts.idle++; break
      case 1: counts.working++; break
      case 2: counts.charging++; break
      case 3: counts.fault++; break
      case 4: counts.offline++; break
      case 5: counts.paused++; break
    }
  })
  return counts
})

const getStatusLabel = (status) => ({ 0: '空闲', 1: '工作中', 2: '充电中', 3: '故障', 4: '离线', 5: '暂停' }[status] || '未知')
const getStatusColor = (status) => ({ 0: '#10b981', 1: '#3b82f6', 2: '#f59e0b', 3: '#ef4444', 4: '#6b7280', 5: '#8b5cf6' }[status] || '#6b7280')

const getBatteryColor = (level) => level > 60 ? '#10b981' : level > 30 ? '#f59e0b' : '#ef4444'
const getBatteryGradient = (level) => {
  const color = getBatteryColor(level)
  return `linear-gradient(90deg, ${color}90 0%, ${color} 100%)`
}

const getBatteryIcon = (level) => {
  if (level > 80) return MagicStick
  if (level > 50) return Coin
  if (level > 20) return Male
  return Female
}
</script>

<style lang="scss" scoped>
.agv-status-cards {
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

    .agv-summary {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #9ca3af;

      .summary-item {
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .summary-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }
    }
  }

  .cards-container {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
    align-content: start;
    min-height: 0;

    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.2);
      border-radius: 3px;
    }

    .agv-card {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 16px;
      transition: all 0.3s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.08);
        transform: translateY(-3px);
        box-shadow: 0 8px 20px rgba(0, 0, 0, 0.3);
      }

      &.status-1 {
        border-left: 4px solid #3b82f6;
      }

      &.status-0 {
        border-left: 4px solid #10b981;
      }

      &.status-2 {
        border-left: 4px solid #f59e0b;
      }

      &.status-3 {
        border-left: 4px solid #ef4444;
      }

      &.status-4 {
        border-left: 4px solid #6b7280;
      }

      &.status-5 {
        border-left: 4px solid #8b5cf6;
      }

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 16px;
        padding-bottom: 12px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.1);

        .agv-info {
          display: flex;
          align-items: center;
          gap: 12px;

          .agv-icon {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;

            :deep(.el-icon) {
              font-size: 24px;
            }
          }

          .agv-basic {
            .agv-no {
              font-size: 16px;
              font-weight: 700;
              color: #e0e7ff;
            }

            .agv-name {
              font-size: 12px;
              color: #9ca3af;
            }
          }
        }

        .status-badge {
          display: flex;
          align-items: center;
          gap: 6px;
          padding: 4px 10px;
          border-radius: 20px;
          font-size: 11px;
          font-weight: 600;

          .status-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            animation: pulse 2s infinite;
          }
        }
      }

      .card-body {
        .info-row {
          display: flex;
          gap: 16px;
          margin-bottom: 12px;

          .info-item {
            flex: 1;
            min-width: 0;

            .info-label {
              display: flex;
              align-items: center;
              gap: 4px;
              font-size: 11px;
              color: #9ca3af;
              margin-bottom: 4px;

              :deep(.el-icon) {
                font-size: 12px;
              }
            }

            .info-value {
              font-size: 14px;
              font-weight: 600;
              color: #e0e7ff;

              &.task-value {
                display: block;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
                color: #60a5fa;
              }
            }
          }
        }

        .battery-section {
          margin-top: 12px;
          padding-top: 12px;
          border-top: 1px solid rgba(255, 255, 255, 0.1);

          .battery-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 8px;

            .battery-label {
              display: flex;
              align-items: center;
              gap: 6px;
              font-size: 12px;
              color: #9ca3af;

              :deep(.el-icon) {
                font-size: 14px;
              }
            }

            .battery-percent {
              font-size: 14px;
              font-weight: 700;
            }
          }

          .battery-bar {
            height: 8px;
            background: rgba(0, 0, 0, 0.3);
            border-radius: 4px;
            overflow: hidden;

            .battery-fill {
              height: 100%;
              border-radius: 4px;
              transition: width 0.5s ease;
            }
          }

          .battery-warning {
            display: flex;
            align-items: center;
            gap: 6px;
            margin-top: 8px;
            font-size: 11px;
            color: #f87171;

            :deep(.el-icon) {
              font-size: 12px;
            }
          }
        }
      }
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
