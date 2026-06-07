<template>
  <div class="agv-status-cards">
    <div class="panel-header">
      <h3>
        <el-icon><MagicStick /></el-icon>
        AGV 状态监控
      </h3>
      <div class="agv-stats">
        <span class="stat-item" v-for="stat in statusStats" :key="stat.status">
          <span class="stat-dot" :style="{ background: stat.color }"></span>
          <span class="stat-label">{{ stat.label }}</span>
          <span class="stat-count">{{ stat.count }}</span>
        </span>
      </div>
    </div>

    <div class="cards-container" ref="cardsContainerRef">
      <div
        v-for="agv in agvList"
        :key="agv.id"
        :class="['agv-card', getStatusClass(agv.status), { 'low-battery': isLowBattery(agv.battery) }]"
      >
        <div class="card-header">
          <div class="agv-info">
            <div class="agv-icon">
              <el-icon><Van /></el-icon>
            </div>
            <div class="agv-basic">
              <span class="agv-no">{{ agv.agvNo }}</span>
              <span class="agv-name">{{ agv.name || '搬运车' }}</span>
            </div>
          </div>
          <span :class="['status-tag', getStatusClass(agv.status)]">
            {{ getStatusLabel(agv.status) }}
          </span>
        </div>

        <div class="card-body">
          <div class="info-row">
            <div class="info-item">
              <span class="info-label">
                <el-icon><Location /></el-icon>
                当前位置
              </span>
              <span class="info-value">{{ agv.currentPosition || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">
                <el-icon><Coin /></el-icon>
                运行速度
              </span>
              <span class="info-value">{{ agv.speed !== null ? agv.speed + ' m/s' : '-' }}</span>
            </div>
          </div>

          <div class="info-row">
            <div class="info-item">
              <span class="info-label">
                <el-icon><Male /></el-icon>
                载重
              </span>
              <span class="info-value">{{ agv.load !== null ? agv.load + ' kg' : '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">
                <el-icon><Female /></el-icon>
                当前任务
              </span>
              <span class="info-value task-id">{{ agv.currentTaskId || '-' }}</span>
            </div>
          </div>

          <div class="battery-section">
            <div class="battery-header">
              <span class="battery-label">电量</span>
              <span :class="['battery-value', { 'low': isLowBattery(agv.battery) }]">
                {{ agv.battery ?? 0 }}%
              </span>
            </div>
            <el-progress
              :percentage="agv.battery ?? 0"
              :color="getBatteryColor(agv.battery)"
              :stroke-width="8"
              :show-text="false"
            />
          </div>

          <div class="card-actions">
            <el-button
              v-if="agv.status === 'WORKING' || agv.status === 1"
              type="warning"
              size="small"
              @click.stop="handlePause(agv)"
            >
              暂停
            </el-button>
            <el-button
              v-if="agv.status === 'PAUSED' || agv.status === 5"
              type="success"
              size="small"
              @click.stop="handleResume(agv)"
            >
              恢复
            </el-button>
            <el-button
              v-if="agv.status === 'IDLE' || agv.status === 0"
              type="primary"
              size="small"
              @click.stop="handleCharge(agv)"
            >
              呼叫充电
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click.stop="handleStop(agv)"
            >
              急停
            </el-button>
          </div>
        </div>

        <div v-if="isLowBattery(agv.battery)" class="low-battery-warning">
          <el-icon><Warning /></el-icon>
          电量不足，请及时充电
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MagicStick, Van, Location, Coin, Male, Female, Warning
} from '@element-plus/icons-vue'
import { agvApi } from '@/api'
import {
  getAgvStatusLabel,
  getAgvStatusColor,
  getAgvStatusClass,
  getBatteryColor
} from '@/utils/helpers'

const props = defineProps({
  agvList: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['agvUpdated'])

const cardsContainerRef = ref(null)

const statusStats = computed(() => {
  const counts = {
    IDLE: { count: 0, label: '空闲', color: '#10b981' },
    WORKING: { count: 0, label: '工作中', color: '#3b82f6' },
    CHARGING: { count: 0, label: '充电中', color: '#f59e0b' },
    FAULT: { count: 0, label: '故障', color: '#ef4444' },
    OFFLINE: { count: 0, label: '离线', color: '#6b7280' },
    PAUSED: { count: 0, label: '暂停', color: '#8b5cf6' }
  }

  props.agvList.forEach(agv => {
    const status = typeof agv.status === 'number'
      ? Object.keys(counts)[agv.status] || 'OFFLINE'
      : agv.status || 'OFFLINE'
    if (counts[status]) {
      counts[status].count++
    }
  })

  return Object.entries(counts)
    .filter(([_, stat]) => stat.count > 0 || ['IDLE', 'WORKING', 'CHARGING', 'FAULT'].includes(_))
    .map(([status, stat]) => ({ status, ...stat }))
})

const getStatusLabel = (status) => getAgvStatusLabel(status)
const getStatusColor = (status) => getAgvStatusColor(status)
const getStatusClass = (status) => getAgvStatusClass(status)

const isLowBattery = (battery) => {
  return battery !== null && battery !== undefined && battery < 30
}

const handlePause = async (agv) => {
  try {
    await ElMessageBox.confirm(
      `确定要暂停AGV ${agv.agvNo} 吗？`,
      '确认暂停',
      { type: 'warning' }
    )
    await agvApi.pause(agv.id)
    ElMessage.success('AGV已暂停')
    emit('agvUpdated')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '暂停失败')
    }
  }
}

const handleResume = async (agv) => {
  try {
    await agvApi.resume(agv.id)
    ElMessage.success('AGV已恢复运行')
    emit('agvUpdated')
  } catch (e) {
    ElMessage.error(e.message || '恢复失败')
  }
}

const handleCharge = async (agv) => {
  try {
    const { value: station } = await ElMessageBox.prompt(
      `请选择充电站为AGV ${agv.agvNo} 充电`,
      '呼叫充电',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPattern: /^[A-Z]\d{2}$/,
        inputErrorMessage: '请输入正确的站点编号，如 D01',
        inputValue: 'D01'
      }
    )
    await agvApi.charge(agv.id, station)
    ElMessage.success('充电呼叫已发送')
    emit('agvUpdated')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '呼叫充电失败')
    }
  }
}

const handleStop = async (agv) => {
  try {
    await ElMessageBox.confirm(
      `确定要紧急停车AGV ${agv.agvNo} 吗？此操作会立即停止AGV！`,
      '确认紧急停车',
      {
        type: 'error',
        confirmButtonText: '确认急停',
        cancelButtonText: '取消'
      }
    )
    await agvApi.stop(agv.id)
    ElMessage.success('AGV已紧急停车')
    emit('agvUpdated')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

watch(() => props.agvList, () => {}, { deep: true })
</script>

<style lang="scss" scoped>
.agv-status-cards {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: rgba(15, 23, 42, 0.8);
  border-radius: 8px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  overflow: hidden;

  .panel-header {
    padding: 12px 16px;
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
        color: '#3b82f6';
        font-size: 20px;
      }
    }
  }

  .agv-stats {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;

    .stat-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      padding: 4px 10px;
      background: rgba(30, 41, 59, 0.6);
      border-radius: 12px;

      .stat-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
      }

      .stat-label {
        color: #94a3b8;
      }

      .stat-count {
        color: #f1f5f9;
        font-weight: 600;
      }
    }
  }

  .cards-container {
    flex: 1;
    overflow-y: auto;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 10px;

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

  .agv-card {
    position: relative;
    padding: 12px;
    background: rgba(30, 41, 59, 0.9);
    border-radius: 8px;
    border: 1px solid rgba(59, 130, 246, 0.2);
    border-left: 4px solid #6b7280;
    transition: all 0.3s ease;

    &:hover {
      border-color: rgba(59, 130, 246, 0.5);
      transform: translateX(2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    }

    &.idle {
      border-left-color: #10b981;
    }

    &.working {
      border-left-color: #3b82f6;
    }

    &.charging {
      border-left-color: #f59e0b;
    }

    &.fault {
      border-left-color: #ef4444;
    }

    &.offline {
      border-left-color: #6b7280;
      opacity: 0.7;
    }

    &.paused {
      border-left-color: #8b5cf6;
    }

    &.low-battery {
      animation: lowBatteryPulse 2s infinite;
    }
  }

  @keyframes lowBatteryPulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
    50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 12px;

    .agv-info {
      display: flex;
      align-items: center;
      gap: 10px;

      .agv-icon {
        width: 40px;
        height: 40px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(59, 130, 246, 0.2);
        border-radius: 8px;
        color: #3b82f6;
        font-size: 20px;
      }

      .agv-basic {
        display: flex;
        flex-direction: column;

        .agv-no {
          font-weight: 600;
          color: #f1f5f9;
          font-size: 14px;
        }

        .agv-name {
          font-size: 12px;
          color: #64748b;
        }
      }
    }
  }

  .status-tag {
    padding: 3px 10px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 500;

    &.idle {
      background: rgba(16, 185, 129, 0.2);
      color: #10b981;
    }

    &.working {
      background: rgba(59, 130, 246, 0.2);
      color: #3b82f6;
    }

    &.charging {
      background: rgba(245, 158, 11, 0.2);
      color: #f59e0b;
    }

    &.fault {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    &.offline {
      background: rgba(107, 114, 128, 0.2);
      color: #6b7280;
    }

    &.paused {
      background: rgba(139, 92, 246, 0.2);
      color: #8b5cf6;
    }
  }

  .info-row {
    display: flex;
    gap: 16px;
    margin-bottom: 10px;

    .info-item {
      flex: 1;
      min-width: 0;

      .info-label {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 11px;
        color: #64748b;
        margin-bottom: 2px;

        .el-icon {
          font-size: 12px;
        }
      }

      .info-value {
        font-size: 13px;
        color: #f1f5f9;
        font-weight: 500;

        &.task-id {
          font-family: 'Courier New', monospace;
          font-size: 12px;
          color: #94a3b8;
        }
      }
    }
  }

  .battery-section {
    margin: 12px 0;
    padding: 10px;
    background: rgba(15, 23, 42, 0.6);
    border-radius: 6px;

    .battery-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;

      .battery-label {
        font-size: 12px;
        color: #64748b;
      }

      .battery-value {
        font-size: 14px;
        font-weight: 600;
        color: #10b981;

        &.low {
          color: #ef4444;
          animation: blink 1s infinite;
        }
      }
    }
  }

  @keyframes blink {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
  }

  .card-actions {
    display: flex;
    gap: 8px;
    justify-content: center;
    padding-top: 10px;
    border-top: 1px solid rgba(59, 130, 246, 0.1);
  }

  .low-battery-warning {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    margin-top: 8px;
    padding: 6px;
    background: rgba(239, 68, 68, 0.1);
    border-radius: 4px;
    font-size: 11px;
    color: #ef4444;

    .el-icon {
      font-size: 14px;
    }
  }
}
</style>
