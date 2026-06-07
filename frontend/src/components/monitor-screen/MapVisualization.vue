<template>
  <div class="map-visualization">
    <div class="panel-header">
      <h3>
        <el-icon><Location /></el-icon>
        AGV实时地图
      </h3>
      <div class="map-legend">
        <span v-for="legend in nodeLegends" :key="legend.type" class="legend-item">
          <span class="legend-dot" :style="{ background: legend.color }"></span>
          {{ legend.label }}
        </span>
      </div>
    </div>
    <div ref="mapChartRef" class="map-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Location } from '@element-plus/icons-vue'
import {
  getAgvStatusColor,
  getNodeTypeColor,
  getNodePosition,
  interpolatePosition
} from '@/utils/helpers'
import { MAP_NODES, MAP_PATHS, NODE_TYPE } from '@/utils/constants'

const props = defineProps({
  agvList: {
    type: Array,
    default: () => []
  },
  executingTasks: {
    type: Array,
    default: () => []
  },
  getAgvAnimatedPosition: {
    type: Function,
    default: null
  }
})

const mapChartRef = ref(null)
let mapChart = null
let animationFrame = null
let lastUpdateTime = 0

const nodeLegends = Object.values(NODE_TYPE).map(item => ({
  type: item.value,
  label: item.label,
  color: item.color
}))

const initChart = () => {
  if (!mapChartRef.value) return

  if (mapChart) {
    mapChart.dispose()
  }

  mapChart = echarts.init(mapChartRef.value)
  updateChart()
  startAnimation()
}

const updateChart = () => {
  if (!mapChart) return

  const nodePoints = MAP_NODES.map(node => ({
    name: node.code,
    value: [node.x, node.y],
    itemStyle: { color: getNodeTypeColor(node.type) }
  }))

  const pathLines = MAP_PATHS.map(path => ({
    coords: [
      getNodePosition(path[0], MAP_NODES),
      getNodePosition(path[1], MAP_NODES)
    ]
  }))

  let taskPathLines = []
  let targetPoints = []

  props.executingTasks.forEach(task => {
    if (task.currentPath && task.currentPath.length > 1) {
      const path = task.currentPath
      for (let i = 0; i < path.length - 1; i++) {
        taskPathLines.push({
          coords: [
            getNodePosition(path[i], MAP_NODES),
            getNodePosition(path[i + 1], MAP_NODES)
          ],
          lineStyle: {
            color: '#fbbf24',
            width: 4
          }
        })
      }

      const endPoint = task.currentPath[task.currentPath.length - 1]
      targetPoints.push({
        name: `目标-${task.taskNo}`,
        value: getNodePosition(endPoint, MAP_NODES),
        itemStyle: { color: '#ef4444' }
      })
    }
  })

  const agvPoints = props.agvList.map(agv => {
    let position
    if (props.getAgvAnimatedPosition) {
      position = props.getAgvAnimatedPosition(agv.id)
    } else {
      position = getNodePosition(agv.currentPosition, MAP_NODES)
    }

    return {
      name: agv.agvNo,
      value: position,
      symbolSize: 28,
      itemStyle: {
        color: getAgvStatusColor(agv.status),
        shadowBlur: 15,
        shadowColor: getAgvStatusColor(agv.status)
      },
      currentPosition: agv.currentPosition,
      status: agv.status,
      battery: agv.battery,
      currentTaskId: agv.currentTaskId
    }
  })

  mapChart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.95)',
      borderColor: 'rgba(59, 130, 246, 0.5)',
      textStyle: { color: '#f1f5f9' },
      formatter: (params) => {
        if (params.seriesName === 'AGV') {
          const data = params.data
          return `
            <div style="padding: 8px;">
              <div style="font-weight: 600; font-size: 14px; margin-bottom: 8px;">${data.name}</div>
              <div style="font-size: 12px; color: #94a3b8;">位置: ${data.currentPosition}</div>
              <div style="font-size: 12px; color: #94a3b8;">电量: ${data.battery}%</div>
              <div style="font-size: 12px; color: #94a3b8;">任务: ${data.currentTaskId || '无'}</div>
            </div>
          `
        }
        return params.name
      }
    },
    grid: { left: '3%', right: '3%', top: '3%', bottom: '3%' },
    xAxis: {
      type: 'value',
      min: -1,
      max: 16,
      splitLine: { lineStyle: { color: 'rgba(71, 85, 105, 0.2)' } },
      axisLine: { show: false },
      axisLabel: { show: false }
    },
    yAxis: {
      type: 'value',
      min: -1,
      max: 16,
      inverse: true,
      splitLine: { lineStyle: { color: 'rgba(71, 85, 105, 0.2)' } },
      axisLine: { show: false },
      axisLabel: { show: false }
    },
    series: [
      {
        name: '路径',
        type: 'lines',
        coordinateSystem: 'cartesian2d',
        data: pathLines,
        lineStyle: {
          color: 'rgba(147, 197, 253, 0.3)',
          width: 2
        },
        silent: true
      },
      {
        name: '任务路径',
        type: 'lines',
        coordinateSystem: 'cartesian2d',
        data: taskPathLines,
        effect: {
          show: true,
          symbol: 'arrow',
          symbolSize: 8,
          color: '#fbbf24',
          period: 3
        },
        lineStyle: {
          color: '#fbbf24',
          width: 3
        }
      },
      {
        name: '节点',
        type: 'scatter',
        data: nodePoints,
        symbolSize: 32,
        label: {
          show: true,
          formatter: '{b}',
          position: 'inside',
          color: '#fff',
          fontSize: 11,
          fontWeight: 500
        }
      },
      {
        name: '目标点',
        type: 'scatter',
        data: targetPoints,
        symbolSize: 10,
        symbol: 'pin',
        label: {
          show: true,
          formatter: '目标',
          position: 'top',
          color: '#ef4444',
          fontSize: 10,
          fontWeight: 600
        },
        z: 10
      },
      {
        name: 'AGV',
        type: 'scatter',
        data: agvPoints,
        symbol: 'circle',
        label: {
          show: true,
          formatter: '{b}',
          position: 'top',
          color: '#f1f5f9',
          fontSize: 11,
          fontWeight: 500,
          textBorderColor: 'rgba(15, 23, 42, 0.8)',
          textBorderWidth: 2
        },
        z: 100,
        animation: false
      }
    ]
  })
}

const startAnimation = () => {
  const animate = (timestamp) => {
    if (timestamp - lastUpdateTime >= 50) {
      if (props.getAgvAnimatedPosition) {
        const agvPoints = props.agvList.map(agv => ({
          name: agv.agvNo,
          value: props.getAgvAnimatedPosition(agv.id),
          symbolSize: 28,
          itemStyle: {
            color: getAgvStatusColor(agv.status),
            shadowBlur: 15,
            shadowColor: getAgvStatusColor(agv.status)
          },
          currentPosition: agv.currentPosition,
          status: agv.status,
          battery: agv.battery,
          currentTaskId: agv.currentTaskId
        }))

        mapChart.setOption({
          series: [{
            name: 'AGV',
            data: agvPoints
          }]
        })
      }
      lastUpdateTime = timestamp
    }
    animationFrame = requestAnimationFrame(animate)
  }
  animationFrame = requestAnimationFrame(animate)
}

const stopAnimation = () => {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
    animationFrame = null
  }
}

const handleResize = () => {
  mapChart?.resize()
}

watch(() => props.agvList, () => {
  updateChart()
}, { deep: true })

watch(() => props.executingTasks, () => {
  updateChart()
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    initChart()
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  stopAnimation()
  window.removeEventListener('resize', handleResize)
  mapChart?.dispose()
})
</script>

<style lang="scss" scoped>
.map-visualization {
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

  .map-legend {
    display: flex;
    gap: 12px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #94a3b8;

      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
      }
    }
  }

  .map-container {
    flex: 1;
    min-height: 300px;
  }
}
</style>
