<template>
  <div class="map-visualization">
    <div class="panel-header">
      <h3>
        <el-icon><Position /></el-icon>
        AGV实时地图
      </h3>
      <div class="legend">
        <span class="legend-item">
          <span class="legend-dot" style="background: #93c5fd;"></span>
          工作站
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #86efac;"></span>
          存储区
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #fcd34d;"></span>
          充电站
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #fca5a5;"></span>
          装卸区
        </span>
        <span class="legend-item">
          <span class="legend-dot" style="background: #c4b5fd;"></span>
          路口
        </span>
      </div>
    </div>
    <div ref="mapChartRef" class="map-chart"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Position } from '@element-plus/icons-vue'

const props = defineProps({
  mapNodes: {
    type: Array,
    default: () => []
  },
  mapPaths: {
    type: Array,
    default: () => []
  },
  agvList: {
    type: Array,
    default: () => []
  },
  executingTasks: {
    type: Array,
    default: () => []
  }
})

const mapChartRef = ref(null)
let mapChartInstance = null

const getNodeTypeColor = (type) => {
  const colors = {
    workstation: '#93c5fd',
    storage: '#86efac',
    charging: '#fcd34d',
    loading: '#fca5a5',
    unloading: '#fca5a5',
    intersection: '#c4b5fd'
  }
  return colors[type] || '#93c5fd'
}

const getAgvStatusColor = (status) => {
  const colors = { 0: '#10b981', 1: '#3b82f6', 2: '#f59e0b', 3: '#ef4444', 4: '#6b7280', 5: '#8b5cf6' }
  return colors[status] || '#6b7280'
}

const getNodePosition = (code) => {
  const node = props.mapNodes.find(n => n.code === code)
  return node ? [node.x, node.y] : null
}

const initChart = () => {
  if (!mapChartRef.value) return

  mapChartInstance = echarts.init(mapChartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!mapChartInstance || props.mapNodes.length === 0) return

  const pathLines = []
  props.mapPaths.forEach(path => {
    const start = getNodePosition(path[0])
    const end = getNodePosition(path[1])
    if (start && end) {
      pathLines.push({
        coords: [start, end],
        lineStyle: { color: '#d1d5db', width: 2, type: 'solid' }
      })
    }
  })

  const taskPathLines = []
  props.executingTasks.forEach(task => {
    if (task.currentPath && task.currentPath.length > 1) {
      for (let i = task.currentNodeIndex || 0; i < task.currentPath.length - 1; i++) {
        const start = getNodePosition(task.currentPath[i])
        const end = getNodePosition(task.currentPath[i + 1])
        if (start && end) {
          taskPathLines.push({
            coords: [start, end],
            lineStyle: {
              color: getAgvStatusColor(1),
              width: 4,
              type: 'solid',
              shadowColor: getAgvStatusColor(1),
              shadowBlur: 10
            }
          })
        }
      }
    }
  })

  const targetPoints = []
  props.executingTasks.forEach(task => {
    const endPos = getNodePosition(task.endPoint)
    if (endPos) {
      targetPoints.push({
        name: `目标: ${task.taskNo}`,
        value: endPos,
        taskNo: task.taskNo,
        itemStyle: { color: '#ef4444' }
      })
    }
  })

  const agvPoints = props.agvList.map(agv => ({
    name: agv.agvNo,
    value: [agv.xCoord || 0, agv.yCoord || 0],
    status: agv.status,
    battery: agv.batteryLevel,
    currentTask: agv.currentTask,
    currentPosition: agv.currentPosition,
    symbolSize: 22,
    itemStyle: {
      color: getAgvStatusColor(agv.status),
      shadowColor: getAgvStatusColor(agv.status),
      shadowBlur: 15
    }
  }))

  const nodePoints = props.mapNodes.map(node => ({
    name: node.code,
    value: [node.x, node.y],
    type: node.type,
    symbolSize: 28,
    itemStyle: {
      color: getNodeTypeColor(node.type),
      borderColor: '#fff',
      borderWidth: 2
    }
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.seriesName === 'AGV') {
          const statusLabels = { 0: '空闲', 1: '工作中', 2: '充电中', 3: '故障', 4: '离线', 5: '暂停' }
          return `
            <div style="padding: 4px;">
              <div style="font-weight: bold; margin-bottom: 4px;">${params.data.name}</div>
              <div>状态: ${statusLabels[params.data.status] || '未知'}</div>
              <div>电量: ${params.data.battery}%</div>
              <div>位置: ${params.data.currentPosition}</div>
              <div>任务: ${params.data.currentTask || '无'}</div>
            </div>
          `
        } else if (params.seriesName === '节点') {
          const typeLabels = { workstation: '工作站', storage: '存储区', charging: '充电站', loading: '装货区', unloading: '卸货区', intersection: '路口' }
          return `
            <div style="padding: 4px;">
              <div style="font-weight: bold;">${params.data.name}</div>
              <div>类型: ${typeLabels[params.data.type] || '节点'}</div>
            </div>
          `
        } else if (params.seriesName === '目标点') {
          return `
            <div style="padding: 4px;">
              <div style="font-weight: bold; color: #ef4444;">${params.data.name}</div>
              <div>任务编号: ${params.data.taskNo}</div>
            </div>
          `
        }
        return params.name
      }
    },
    grid: { left: '5%', right: '5%', top: '5%', bottom: '5%' },
    xAxis: {
      type: 'value',
      min: -1,
      max: 16,
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#e5e7eb' } },
      axisLabel: { show: false },
      axisLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: -1,
      max: 16,
      inverse: true,
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#e5e7eb' } },
      axisLabel: { show: false },
      axisLine: { show: false }
    },
    series: [
      {
        name: '路径',
        type: 'lines',
        data: pathLines,
        silent: true,
        coordinateSystem: 'cartesian2d',
        lineStyle: { width: 2, color: '#d1d5db' }
      },
      {
        name: '任务路径',
        type: 'lines',
        data: taskPathLines,
        silent: true,
        coordinateSystem: 'cartesian2d',
        lineStyle: { width: 4 },
        effect: {
          show: true,
          period: 4,
          trailLength: 0.3,
          symbol: 'arrow',
          symbolSize: 8,
          color: '#fff'
        }
      },
      {
        name: '节点',
        type: 'scatter',
        data: nodePoints,
        label: { show: true, formatter: '{b}', position: 'inside', color: '#1f2937', fontSize: 11, fontWeight: 500 }
      },
      {
        name: '目标点',
        type: 'scatter',
        data: targetPoints,
        symbolSize: 18,
        label: { show: true, formatter: '目标', position: 'top', color: '#ef4444', fontSize: 10, fontWeight: 'bold' },
        animation: true,
        animationDuration: 1000,
        animationEasingUpdate: 'elasticOut'
      },
      {
        name: 'AGV',
        type: 'scatter',
        data: agvPoints,
        label: { show: true, formatter: '{b}', position: 'top', color: '#1f2937', fontSize: 11, fontWeight: 600 },
        animation: true,
        animationDuration: 800,
        animationEasingUpdate: 'cubicInOut'
      }
    ]
  }

  mapChartInstance.setOption(option, true)
}

const handleResize = () => {
  mapChartInstance?.resize()
}

watch(
  () => [props.agvList, props.executingTasks],
  () => {
    nextTick(() => updateChart())
  },
  { deep: true }
)

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  mapChartInstance?.dispose()
})
</script>

<style lang="scss" scoped>
.map-visualization {
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

    .legend {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #9ca3af;

      .legend-item {
        display: flex;
        align-items: center;
        gap: 6px;
      }

      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
      }
    }
  }

  .map-chart {
    flex: 1;
    min-height: 0;
  }
}
</style>
