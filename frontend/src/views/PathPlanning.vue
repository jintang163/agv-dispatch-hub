<template>
  <div class="page-container">
    <div class="page-header">
      <h2>路径规划与冲突解决</h2>
      <div class="header-actions">
        <el-button @click="handleRefreshAll">
          <el-icon><Refresh /></el-icon>
          刷新全部
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="card" class="main-tabs">
      <el-tab-pane label="路径规划" name="path-planning">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>路径规划</span>
                </div>
              </template>
              <el-form :model="pathForm" label-width="80px">
                <el-form-item label="起点">
                  <el-input
                    v-model="pathForm.startPoint"
                    placeholder="请输入起点节点编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="终点">
                  <el-input
                    v-model="pathForm.endPoint"
                    placeholder="请输入终点节点编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="算法">
                  <el-select v-model="pathForm.algorithm" style="width: 100%">
                    <el-option label="A* 算法" value="A*" />
                    <el-option label="Dijkstra 算法" value="DIJKSTRA" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handlePlanPath" :loading="pathPlanningLoading">
                    <el-icon><Position /></el-icon>
                    规划路径
                  </el-button>
                  <el-button @click="handleClearPathResult">清空结果</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <el-col :span="16">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>规划结果</span>
                  <span v-if="pathResult.success" class="path-stats">
                    <el-tag type="success">路径长度: {{ pathResult.totalDistance.toFixed(2) }} m</el-tag>
                    <el-tag type="warning" style="margin-left: 10px">预计时间: {{ pathResult.estimatedTime.toFixed(1) }} s</el-tag>
                    <el-tag type="info" style="margin-left: 10px">算法: {{ pathResult.algorithm }}</el-tag>
                    <el-tag v-if="pathResult.hasDetour" type="danger" style="margin-left: 10px">已绕行</el-tag>
                  </span>
                </div>
              </template>

              <div v-if="pathResult.success && pathResult.path && pathResult.path.length > 0">
                <div class="path-visualization">
                  <div class="path-nodes">
                    <span
                      v-for="(node, index) in pathResult.path"
                      :key="index"
                      class="path-node-item"
                    >
                      <span class="node-name">{{ node }}</span>
                      <span v-if="index < pathResult.path.length - 1" class="path-arrow">→</span>
                    </span>
                  </div>
                </div>

                <el-table :data="pathTableData" border stripe style="margin-top: 20px">
                  <el-table-column type="index" label="序号" width="60" align="center" />
                  <el-table-column prop="node" label="节点编号" width="150" align="center" />
                  <el-table-column prop="type" label="节点类型" width="120" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.type === '起点' ? 'success' : row.type === '终点' ? 'danger' : 'info'" size="small">
                        {{ row.type }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="distance" label="累计距离(m)" width="150" align="center">
                    <template #default="{ row }">
                      {{ row.distance.toFixed(2) }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div v-else-if="pathResult.message" class="empty-result">
                <el-alert :title="pathResult.message" type="error" :closable="false" />
              </div>

              <el-empty v-else description="请输入起点和终点进行路径规划" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="冲突管理" name="conflict">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="stat-card conflict-pending">
              <div class="stat-value">{{ pendingConflictCount }}</div>
              <div class="stat-label">待解决冲突</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card conflict-headon">
              <div class="stat-value">{{ headOnCount }}</div>
              <div class="stat-label">对向冲突</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card conflict-cross">
              <div class="stat-value">{{ crossCount }}</div>
              <div class="stat-label">交叉冲突</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card conflict-follow">
              <div class="stat-value">{{ followCount }}</div>
              <div class="stat-label">跟车冲突</div>
            </div>
          </el-col>
        </el-row>

        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>冲突列表</span>
              <div class="filter-bar">
                <el-select v-model="conflictStatusFilter" placeholder="状态筛选" clearable style="width: 150px; margin-right: 10px">
                  <el-option label="待解决" value="pending" />
                  <el-option label="已解决" value="resolved" />
                </el-select>
                <el-button type="primary" @click="handleDetectConflicts" :loading="conflictLoading">
                  <el-icon><Warning /></el-icon>
                  检测冲突
                </el-button>
                <el-button type="success" @click="handleResolveAllConflicts" :loading="resolveAllLoading" style="margin-left: 10px">
                  <el-icon><Check /></el-icon>
                  解决所有冲突
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="filteredConflictList" border stripe v-loading="conflictLoading">
            <el-table-column prop="id" label="冲突ID" width="80" align="center" />
            <el-table-column prop="conflictType" label="冲突类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getConflictTypeClass(row.conflictType)">
                  {{ getConflictTypeName(row.conflictType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="location" label="冲突位置" width="120" align="center" />
            <el-table-column label="涉及AGV" width="180" align="center">
              <template #default="{ row }">
                <el-tag type="primary" size="small">{{ row.agvId1 }}</el-tag>
                <span style="margin: 0 5px">↔</span>
                <el-tag type="primary" size="small">{{ row.agvId2 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="resolutionStrategy" label="解决策略" width="150" align="center">
              <template #default="{ row }">
                <span v-if="row.resolutionStrategy !== null && row.resolutionStrategy !== undefined">
                  {{ getResolutionStrategyName(row.resolutionStrategy) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="resolved" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.resolved ? 'success' : 'danger'" size="small">
                  {{ row.resolved ? '已解决' : '待解决' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" align="center">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="resolveTime" label="解决时间" width="180" align="center">
              <template #default="{ row }">
                {{ row.resolveTime ? formatDateTime(row.resolveTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.resolved"
                  type="primary"
                  size="small"
                  @click="handleResolveConflict(row)"
                  :loading="resolvingConflictId === row.id"
                >
                  解决
                </el-button>
                <el-button type="primary" link size="small" @click="showConflictDetail(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="死锁管理" name="deadlock">
        <el-row :gutter="20">
          <el-col :span="8">
            <div class="stat-card deadlock-pending">
              <div class="stat-value">{{ pendingDeadlockCount }}</div>
              <div class="stat-label">待解决死锁</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card deadlock-total">
              <div class="stat-value">{{ totalDeadlockCount }}</div>
              <div class="stat-label">历史死锁总数</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card deadlock-resolved">
              <div class="stat-value">{{ resolvedDeadlockCount }}</div>
              <div class="stat-label">已解决死锁</div>
            </div>
          </el-col>
        </el-row>

        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>死锁列表</span>
              <div class="filter-bar">
                <el-select v-model="deadlockStatusFilter" placeholder="状态筛选" clearable style="width: 150px; margin-right: 10px">
                  <el-option label="待解决" value="pending" />
                  <el-option label="已解决" value="resolved" />
                </el-select>
                <el-button type="primary" @click="handleDetectDeadlocks" :loading="deadlockLoading">
                  <el-icon><Warning /></el-icon>
                  检测死锁
                </el-button>
                <el-button type="success" @click="handleResolveAllDeadlocks" :loading="resolveAllDeadlockLoading" style="margin-left: 10px">
                  <el-icon><Check /></el-icon>
                  解决所有死锁
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="filteredDeadlockList" border stripe v-loading="deadlockLoading">
            <el-table-column prop="id" label="死锁ID" width="80" align="center" />
            <el-table-column prop="waitChain" label="等待链" min-width="250">
              <template #default="{ row }">
                <div class="wait-chain">
                  <el-tag
                    v-for="(agv, index) in parseWaitChain(row.waitChain)"
                    :key="index"
                    type="warning"
                    size="small"
                  >
                    {{ agv }}
                  </el-tag>
                  <span v-if="parseWaitChain(row.waitChain).length > 0" class="wait-arrow">↻</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="agvCount" label="AGV数量" width="100" align="center" />
            <el-table-column prop="selectedAgvId" label="选中AGV" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.selectedAgvId" type="danger" size="small">{{ row.selectedAgvId }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="resolutionStrategy" label="解决策略" width="150" align="center">
              <template #default="{ row }">
                <span v-if="row.resolutionStrategy !== null && row.resolutionStrategy !== undefined">
                  {{ getResolutionStrategyName(row.resolutionStrategy) }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="resolved" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.resolved ? 'success' : 'danger'" size="small">
                  {{ row.resolved ? '已解决' : '待解决' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" align="center">
              <template #default="{ row }">
                {{ formatDateTime(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="resolvedTime" label="解决时间" width="180" align="center">
              <template #default="{ row }">
                {{ row.resolvedTime ? formatDateTime(row.resolvedTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.resolved"
                  type="primary"
                  size="small"
                  @click="handleResolveDeadlock(row)"
                  :loading="resolvingDeadlockId === row.id"
                >
                  解决
                </el-button>
                <el-button type="primary" link size="small" @click="showDeadlockDetail(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="节点锁管理" name="node-lock">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>节点锁查询</span>
                </div>
              </template>
              <el-form :model="nodeLockForm" label-width="100px">
                <el-form-item label="节点编号">
                  <el-input
                    v-model="nodeLockForm.nodeCode"
                    placeholder="请输入节点编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="AGV编号">
                  <el-input
                    v-model="nodeLockForm.agvId"
                    placeholder="请输入AGV编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleGetNodeLockHolder" :loading="nodeLockLoading">
                    查询锁状态
                  </el-button>
                  <el-button type="success" @click="handleLockNode" :loading="lockNodeLoading">
                    锁定节点
                  </el-button>
                  <el-button type="danger" @click="handleUnlockNode" :loading="unlockNodeLoading">
                    释放节点
                  </el-button>
                </el-form-item>
              </el-form>

              <div v-if="nodeLockHolder !== null" class="node-lock-result">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="节点编号">
                    {{ nodeLockForm.nodeCode }}
                  </el-descriptions-item>
                  <el-descriptions-item label="锁状态">
                    <el-tag :type="nodeLockHolder ? 'danger' : 'success'" size="small">
                      {{ nodeLockHolder ? '已锁定' : '未锁定' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="持有者">
                    {{ nodeLockHolder || '-' }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>路口通行管理</span>
                </div>
              </template>
              <el-form :model="intersectionForm" label-width="100px">
                <el-form-item label="路口编号">
                  <el-input
                    v-model="intersectionForm.intersectionCode"
                    placeholder="请输入路口编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="AGV编号">
                  <el-input
                    v-model="intersectionForm.agvId"
                    placeholder="请输入AGV编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleTryIntersectionPass" :loading="tryPassLoading">
                    尝试通过
                  </el-button>
                  <el-button type="success" @click="handleCompleteIntersectionPass" :loading="completePassLoading">
                    完成通行
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>阻塞节点管理</span>
                </div>
              </template>
              <el-form :model="blockedForm" label-width="100px">
                <el-form-item label="节点编号">
                  <el-input
                    v-model="blockedForm.nodeCode"
                    placeholder="请输入节点编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="阻塞原因">
                  <el-input
                    v-model="blockedForm.reason"
                    placeholder="请输入阻塞原因"
                    clearable
                    type="textarea"
                    :rows="2"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="danger" @click="handleMarkBlocked" :loading="markBlockedLoading">
                    标记阻塞
                  </el-button>
                  <el-button type="success" @click="handleClearBlocked" :loading="clearBlockedLoading">
                    清除阻塞
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px">
          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>占用节点列表</span>
                  <el-button size="small" @click="handleLoadOccupiedPaths">
                    <el-icon><Refresh /></el-icon>
                    刷新
                  </el-button>
                </div>
              </template>
              <el-table :data="occupiedPathsList" border stripe size="small" v-loading="occupiedLoading" max-height="300">
                <el-table-column prop="nodeCode" label="节点编号" width="120" align="center" />
                <el-table-column prop="agvId" label="占用AGV" align="center">
                  <template #default="{ row }">
                    <el-tag type="primary" size="small">{{ row.agvId }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="occupiedPathsList.length === 0 && !occupiedLoading" description="暂无占用节点" :image-size="80" />
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>阻塞节点列表</span>
                  <el-button size="small" @click="handleLoadBlockedPaths">
                    <el-icon><Refresh /></el-icon>
                    刷新
                  </el-button>
                </div>
              </template>
              <el-table :data="blockedPathsList" border stripe size="small" v-loading="blockedLoading" max-height="300">
                <el-table-column prop="nodeCode" label="节点编号" width="120" align="center" />
                <el-table-column prop="reason" label="阻塞原因" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="blockedPathsList.length === 0 && !blockedLoading" description="暂无阻塞节点" :image-size="80" />
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>锁定路口列表</span>
                  <el-button size="small" @click="handleLoadLockedIntersections">
                    <el-icon><Refresh /></el-icon>
                    刷新
                  </el-button>
                </div>
              </template>
              <el-table :data="lockedIntersectionsList" border stripe size="small" v-loading="lockedLoading" max-height="300">
                <el-table-column prop="intersectionCode" label="路口编号" width="120" align="center" />
                <el-table-column prop="agvId" label="锁定AGV" align="center">
                  <template #default="{ row }">
                    <el-tag type="warning" size="small">{{ row.agvId }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="lockedIntersectionsList.length === 0 && !lockedLoading" description="暂无锁定路口" :image-size="80" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="任务执行监控" name="task-monitor">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="stat-card task-executing">
              <div class="stat-value">{{ executingTaskCount }}</div>
              <div class="stat-label">执行中任务</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card task-pending">
              <div class="stat-value">{{ pendingDispatchCount }}</div>
              <div class="stat-label">待下发任务</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card task-completed">
              <div class="stat-value">{{ completedTaskCount }}</div>
              <div class="stat-label">已完成任务</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card task-timeout">
              <div class="stat-value">{{ timeoutTaskCount }}</div>
              <div class="stat-label">超时任务</div>
            </div>
          </el-col>
        </el-row>

        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>任务执行列表</span>
              <div class="filter-bar">
                <el-select v-model="refreshInterval" @change="handleRefreshIntervalChange" style="width: 180px; margin-right: 10px">
                  <el-option label="关闭自动刷新" value="0" />
                  <el-option label="5秒刷新一次" value="5000" />
                  <el-option label="10秒刷新一次" value="10000" />
                  <el-option label="30秒刷新一次" value="30000" />
                </el-select>
                <el-button type="primary" @click="loadExecutingTasks" :loading="taskMonitorLoading">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="executingTaskList" border stripe v-loading="taskMonitorLoading">
            <el-table-column prop="id" label="任务ID" width="80" align="center" />
            <el-table-column prop="taskNo" label="任务编号" width="150" align="center" />
            <el-table-column prop="agvNo" label="AGV编号" width="120" align="center">
              <template #default="{ row }">
                <el-tag type="primary" size="small">{{ row.agvNo || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currentNode" label="当前节点" width="120" align="center" />
            <el-table-column prop="status" label="执行状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getTaskStatusType(row.status)" size="small">
                  {{ getTaskStatusName(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="执行进度" width="200" align="center">
              <template #default="{ row }">
                <el-progress :percentage="row.progress || 0" :status="row.progress >= 100 ? 'success' : ''" :stroke-width="12" />
              </template>
            </el-table-column>
            <el-table-column label="步骤" width="120" align="center">
              <template #default="{ row }">
                {{ row.currentStep || 0 }} / {{ row.totalSteps || 0 }}
              </template>
            </el-table-column>
            <el-table-column prop="executedTime" label="已执行时间" width="130" align="center">
              <template #default="{ row }">
                {{ formatDuration(row.executedTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="estimatedCompleteTime" label="预计完成时间" width="180" align="center">
              <template #default="{ row }">
                {{ row.estimatedCompleteTime ? formatDateTime(row.estimatedCompleteTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'PENDING_DISPATCH'"
                  type="primary"
                  size="small"
                  @click="handleDispatchTask(row)"
                  :loading="operatingTaskId === row.id && operatingAction === 'dispatch'"
                >
                  下发
                </el-button>
                <el-button
                  v-if="row.status === 'EXECUTING'"
                  type="warning"
                  size="small"
                  @click="handlePauseTask(row)"
                  :loading="operatingTaskId === row.id && operatingAction === 'pause'"
                >
                  暂停
                </el-button>
                <el-button
                  v-if="row.status === 'PAUSED'"
                  type="success"
                  size="small"
                  @click="handleResumeTask(row)"
                  :loading="operatingTaskId === row.id && operatingAction === 'resume'"
                >
                  恢复
                </el-button>
                <el-button
                  v-if="row.status !== 'COMPLETED' && row.status !== 'CANCELED'"
                  type="danger"
                  size="small"
                  @click="handleCancelTask(row)"
                  :loading="operatingTaskId === row.id && operatingAction === 'cancel'"
                >
                  取消
                </el-button>
                <el-button type="primary" link size="small" @click="showTaskDetail(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="executingTaskList.length === 0 && !taskMonitorLoading" description="暂无执行中的任务" :image-size="80" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="AGV控制 & 告警管理" name="agv-control-alarm">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card>
              <template #header>
                <div class="card-header">
                  <span>AGV远程控制</span>
                </div>
              </template>
              <el-form :model="agvControlForm" label-width="100px">
                <el-form-item label="AGV编号">
                  <el-input
                    v-model="agvControlForm.agvNo"
                    placeholder="请输入AGV编号"
                    clearable
                  />
                  <el-button type="text" @click="handleQueryCurrentTask" :loading="queryCurrentTaskLoading" style="margin-top: 5px">
                    查询当前任务
                  </el-button>
                </el-form-item>
                <el-form-item label="控制命令">
                  <el-select v-model="agvControlForm.command" style="width: 100%" placeholder="请选择控制命令">
                    <el-option label="PAUSE - 暂停" value="PAUSE" />
                    <el-option label="RESUME - 恢复" value="RESUME" />
                    <el-option label="CANCEL - 取消任务" value="CANCEL" />
                    <el-option label="STOP - 立即停车" value="STOP" />
                    <el-option label="SLOW_DOWN - 慢速行驶" value="SLOW_DOWN" />
                    <el-option label="NORMAL_SPEED - 正常行驶" value="NORMAL_SPEED" />
                    <el-option label="DETOUR - 避障绕行" value="DETOUR" />
                    <el-option label="GO_CHARGE - 返回充电站" value="GO_CHARGE" />
                    <el-option label="GO_TO_POINT - 到指定点" value="GO_TO_POINT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="目标点" v-if="agvControlForm.command === 'GO_TO_POINT'">
                  <el-input
                    v-model="agvControlForm.targetPoint"
                    placeholder="请输入目标点编号"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="速度" v-if="agvControlForm.command === 'SLOW_DOWN' || agvControlForm.command === 'NORMAL_SPEED'">
                  <el-input-number
                    v-model="agvControlForm.speed"
                    :min="0.1"
                    :max="5"
                    :step="0.1"
                    :precision="1"
                    style="width: 100%"
                    placeholder="请输入速度 (m/s)"
                  />
                </el-form-item>
                <el-form-item label="控制原因">
                  <el-input
                    v-model="agvControlForm.reason"
                    placeholder="请输入控制原因"
                    clearable
                    type="textarea"
                    :rows="2"
                  />
                </el-form-item>
                <el-form-item label="操作人">
                  <el-input
                    v-model="agvControlForm.operator"
                    placeholder="请输入操作人"
                    clearable
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSendControlCommand" :loading="controlCommandLoading">
                    <el-icon><Operation /></el-icon>
                    发送控制命令
                  </el-button>
                  <el-button @click="handleResetControlForm">
                    重置
                  </el-button>
                </el-form-item>
              </el-form>

              <div v-if="currentAgvTask !== null" class="current-task-info">
                <el-divider content-position="left">当前任务信息</el-divider>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="任务ID">{{ currentAgvTask.id || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="任务编号">{{ currentAgvTask.taskNo || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="任务状态">
                    <el-tag :type="getTaskStatusType(currentAgvTask.status)" size="small">
                      {{ getTaskStatusName(currentAgvTask.status) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="当前节点">{{ currentAgvTask.currentNode || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="执行进度">
                    <el-progress :percentage="currentAgvTask.progress || 0" :stroke-width="8" />
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-card>
          </el-col>

          <el-col :span="12">
            <el-row :gutter="20">
              <el-col :span="6">
                <div class="stat-card alarm-unhandled">
                  <div class="stat-value">{{ unhandledAlarmCount }}</div>
                  <div class="stat-label">未处理告警</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-card alarm-critical">
                  <div class="stat-value">{{ criticalAlarmCount }}</div>
                  <div class="stat-label">严重告警</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-card alarm-warning">
                  <div class="stat-value">{{ warningAlarmCount }}</div>
                  <div class="stat-label">警告</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="stat-card alarm-today">
                  <div class="stat-value">{{ todayAlarmCount }}</div>
                  <div class="stat-label">今日告警</div>
                </div>
              </el-col>
            </el-row>

            <el-card style="margin-top: 20px">
              <template #header>
                <div class="card-header">
                  <span>告警列表</span>
                  <div class="filter-bar">
                    <el-select v-model="alarmLevelFilter" placeholder="级别筛选" clearable style="width: 120px; margin-right: 10px">
                      <el-option label="全部" value="" />
                      <el-option label="错误" value="ERROR" />
                      <el-option label="警告" value="WARNING" />
                    </el-select>
                    <el-select v-model="alarmStatusFilter" placeholder="状态筛选" clearable style="width: 120px; margin-right: 10px">
                      <el-option label="全部" value="" />
                      <el-option label="未处理" value="unhandled" />
                      <el-option label="已处理" value="handled" />
                    </el-select>
                    <el-button type="primary" @click="loadAllAlarms" :loading="alarmLoading">
                      <el-icon><Refresh /></el-icon>
                      刷新
                    </el-button>
                  </div>
                </div>
              </template>

              <el-table :data="filteredAlarmList" border stripe v-loading="alarmLoading" max-height="400">
                <el-table-column prop="id" label="告警ID" width="80" align="center" />
                <el-table-column prop="alarmType" label="告警类型" width="120" align="center" />
                <el-table-column prop="level" label="级别" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="getAlarmLevelType(row.level)" size="small">
                      {{ getAlarmLevelName(row.level) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
                <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
                <el-table-column prop="agvNo" label="关联AGV" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.agvNo" type="primary" size="small">{{ row.agvNo }}</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="taskId" label="关联任务" width="100" align="center">
                  <template #default="{ row }">
                    {{ row.taskId || '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="nodeCode" label="关联节点" width="100" align="center">
                  <template #default="{ row }">
                    {{ row.nodeCode || '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="创建时间" width="150" align="center">
                  <template #default="{ row }">
                    {{ formatDateTime(row.createTime) }}
                  </template>
                </el-table-column>
                <el-table-column prop="handled" label="状态" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.handled ? 'success' : 'danger'" size="small">
                      {{ row.handled ? '已处理' : '未处理' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="!row.handled"
                      type="primary"
                      size="small"
                      @click="handleHandleAlarm(row)"
                      :loading="handlingAlarmId === row.id"
                    >
                      处理
                    </el-button>
                    <el-button type="primary" link size="small" @click="showAlarmDetail(row)">
                      详情
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="filteredAlarmList.length === 0 && !alarmLoading" description="暂无告警记录" :image-size="80" />
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="taskDetailVisible"
      title="任务详情"
      width="800px"
    >
      <el-descriptions :column="2" border v-if="currentTaskDetail">
        <el-descriptions-item label="任务ID">{{ currentTaskDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="任务编号">{{ currentTaskDetail.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="AGV编号">
          <el-tag type="primary" size="small">{{ currentTaskDetail.agvNo || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="getTaskStatusType(currentTaskDetail.status)" size="small">
            {{ getTaskStatusName(currentTaskDetail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ currentTaskDetail.currentNode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行进度">
          <el-progress :percentage="currentTaskDetail.progress || 0" :status="currentTaskDetail.progress >= 100 ? 'success' : ''" :stroke-width="10" />
        </el-descriptions-item>
        <el-descriptions-item label="当前步骤">{{ currentTaskDetail.currentStep || 0 }} / {{ currentTaskDetail.totalSteps || 0 }}</el-descriptions-item>
        <el-descriptions-item label="已执行时间">{{ formatDuration(currentTaskDetail.executedTime) }}</el-descriptions-item>
        <el-descriptions-item label="预计完成时间">{{ currentTaskDetail.estimatedCompleteTime ? formatDateTime(currentTaskDetail.estimatedCompleteTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(currentTaskDetail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ currentTaskDetail.taskType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ currentTaskDetail.priority || '-' }}</el-descriptions-item>
        <el-descriptions-item label="路径节点" :span="2">
          <div class="path-nodes">
            <span
              v-for="(node, index) in currentTaskDetail.pathNodes || []"
              :key="index"
              class="path-node-item"
            >
              <span class="node-name" :class="{ 'node-current': node === currentTaskDetail.currentNode }">{{ node }}</span>
              <span v-if="index < (currentTaskDetail.pathNodes?.length || 0) - 1" class="path-arrow">→</span>
            </span>
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">执行日志</el-divider>
      <el-table :data="currentTaskDetail.logs || []" border stripe size="small" max-height="200">
        <el-table-column prop="timestamp" label="时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.level === 'ERROR' ? 'danger' : row.level === 'WARN' ? 'warning' : 'info'" size="small">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="日志内容" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!currentTaskDetail.logs || currentTaskDetail.logs.length === 0" description="暂无执行日志" :image-size="60" />
    </el-dialog>

    <el-dialog
      v-model="alarmDetailVisible"
      title="告警详情"
      width="600px"
    >
      <el-descriptions :column="2" border v-if="currentAlarmDetail">
        <el-descriptions-item label="告警ID">{{ currentAlarmDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="告警类型">{{ currentAlarmDetail.alarmType }}</el-descriptions-item>
        <el-descriptions-item label="告警级别">
          <el-tag :type="getAlarmLevelType(currentAlarmDetail.level)" size="small">
            {{ getAlarmLevelName(currentAlarmDetail.level) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag :type="currentAlarmDetail.handled ? 'success' : 'danger'" size="small">
            {{ currentAlarmDetail.handled ? '已处理' : '未处理' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ currentAlarmDetail.title }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentAlarmDetail.description }}</el-descriptions-item>
        <el-descriptions-item label="关联AGV">{{ currentAlarmDetail.agvNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联任务">{{ currentAlarmDetail.taskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联节点">{{ currentAlarmDetail.nodeCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(currentAlarmDetail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="处理人" v-if="currentAlarmDetail.handled">{{ currentAlarmDetail.handler || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理时间" v-if="currentAlarmDetail.handled">{{ currentAlarmDetail.handleTime ? formatDateTime(currentAlarmDetail.handleTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理结果" :span="2" v-if="currentAlarmDetail.handled">{{ currentAlarmDetail.handleResult || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog
      v-model="conflictDetailVisible"
      title="冲突详情"
      width="600px"
    >
      <el-descriptions :column="2" border v-if="currentConflict">
        <el-descriptions-item label="冲突ID">{{ currentConflict.id }}</el-descriptions-item>
        <el-descriptions-item label="冲突类型">
          <el-tag :type="getConflictTypeClass(currentConflict.conflictType)">
            {{ getConflictTypeName(currentConflict.conflictType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="冲突位置">{{ currentConflict.location }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentConflict.resolved ? 'success' : 'danger'">
            {{ currentConflict.resolved ? '已解决' : '待解决' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AGV1">{{ currentConflict.agvId1 }}</el-descriptions-item>
        <el-descriptions-item label="AGV2">{{ currentConflict.agvId2 }}</el-descriptions-item>
        <el-descriptions-item label="任务1">{{ currentConflict.taskId1 || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务2">{{ currentConflict.taskId2 || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解决策略">
          {{ currentConflict.resolutionStrategy !== null && currentConflict.resolutionStrategy !== undefined
            ? getResolutionStrategyName(currentConflict.resolutionStrategy)
            : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="解决措施">{{ currentConflict.resolution || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(currentConflict.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="解决时间">{{ currentConflict.resolveTime ? formatDateTime(currentConflict.resolveTime) : '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog
      v-model="deadlockDetailVisible"
      title="死锁详情"
      width="600px"
    >
      <el-descriptions :column="2" border v-if="currentDeadlock">
        <el-descriptions-item label="死锁ID">{{ currentDeadlock.id }}</el-descriptions-item>
        <el-descriptions-item label="AGV数量">{{ currentDeadlock.agvCount }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="currentDeadlock.resolved ? 'success' : 'danger'">
            {{ currentDeadlock.resolved ? '已解决' : '待解决' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="等待链" :span="2">
          <div class="wait-chain">
            <el-tag
              v-for="(agv, index) in parseWaitChain(currentDeadlock.waitChain)"
              :key="index"
              type="warning"
              size="small"
            >
              {{ agv }}
            </el-tag>
            <span class="wait-arrow">↻</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="选中AGV">{{ currentDeadlock.selectedAgvId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="解决策略">
          {{ currentDeadlock.resolutionStrategy !== null && currentDeadlock.resolutionStrategy !== undefined
            ? getResolutionStrategyName(currentDeadlock.resolutionStrategy)
            : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="解决详情" :span="2">{{ currentDeadlock.resolutionDetail || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(currentDeadlock.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="解决时间">{{ currentDeadlock.resolvedTime ? formatDateTime(currentDeadlock.resolvedTime) : '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Warning, Check, Position, Operation } from '@element-plus/icons-vue'
import { dispatchApi, pathPlanningApi } from '@/api'

const activeTab = ref('path-planning')
let autoRefreshTimer = null

const pathPlanningLoading = ref(false)
const pathForm = reactive({
  startPoint: '',
  endPoint: '',
  algorithm: 'A*'
})
const pathResult = reactive({
  success: false,
  path: [],
  totalDistance: 0,
  estimatedTime: 0,
  algorithm: '',
  hasDetour: false,
  message: ''
})

const pathTableData = computed(() => {
  if (!pathResult.path || pathResult.path.length === 0) return []
  return pathResult.path.map((node, index) => {
    const stepDistance = pathResult.totalDistance / (pathResult.path.length - 1 || 1)
    return {
      node,
      type: index === 0 ? '起点' : index === pathResult.path.length - 1 ? '终点' : '途经点',
      distance: index * stepDistance
    }
  })
})

const conflictLoading = ref(false)
const resolveAllLoading = ref(false)
const resolvingConflictId = ref(null)
const conflictList = ref([])
const conflictStatusFilter = ref('')
const conflictDetailVisible = ref(false)
const currentConflict = ref(null)

const pendingConflictCount = computed(() => conflictList.value.filter(c => !c.resolved).length)
const headOnCount = computed(() => conflictList.value.filter(c => c.conflictType === 'HEAD_ON' && !c.resolved).length)
const crossCount = computed(() => conflictList.value.filter(c => c.conflictType === 'CROSS' && !c.resolved).length)
const followCount = computed(() => conflictList.value.filter(c => c.conflictType === 'FOLLOW' && !c.resolved).length)

const filteredConflictList = computed(() => {
  let list = conflictList.value
  if (conflictStatusFilter.value === 'pending') {
    list = list.filter(c => !c.resolved)
  } else if (conflictStatusFilter.value === 'resolved') {
    list = list.filter(c => c.resolved)
  }
  return list
})

const deadlockLoading = ref(false)
const resolveAllDeadlockLoading = ref(false)
const resolvingDeadlockId = ref(null)
const deadlockList = ref([])
const deadlockStatusFilter = ref('')
const deadlockDetailVisible = ref(false)
const currentDeadlock = ref(null)

const pendingDeadlockCount = computed(() => deadlockList.value.filter(d => !d.resolved).length)
const totalDeadlockCount = computed(() => deadlockList.value.length)
const resolvedDeadlockCount = computed(() => deadlockList.value.filter(d => d.resolved).length)

const filteredDeadlockList = computed(() => {
  let list = deadlockList.value
  if (deadlockStatusFilter.value === 'pending') {
    list = list.filter(d => !d.resolved)
  } else if (deadlockStatusFilter.value === 'resolved') {
    list = list.filter(d => d.resolved)
  }
  return list
})

const nodeLockLoading = ref(false)
const lockNodeLoading = ref(false)
const unlockNodeLoading = ref(false)
const nodeLockForm = reactive({
  nodeCode: '',
  agvId: ''
})
const nodeLockHolder = ref(null)

const tryPassLoading = ref(false)
const completePassLoading = ref(false)
const intersectionForm = reactive({
  intersectionCode: '',
  agvId: ''
})

const markBlockedLoading = ref(false)
const clearBlockedLoading = ref(false)
const blockedForm = reactive({
  nodeCode: '',
  reason: ''
})

const occupiedLoading = ref(false)
const blockedLoading = ref(false)
const lockedLoading = ref(false)
const occupiedPathsList = ref([])
const blockedPathsList = ref([])
const lockedIntersectionsList = ref([])

// 任务执行监控
const taskMonitorLoading = ref(false)
const executingTaskList = ref([])
const refreshInterval = ref('0')
const operatingTaskId = ref(null)
const operatingAction = ref('')
const taskDetailVisible = ref(false)
const currentTaskDetail = ref(null)

const executingTaskCount = computed(() => executingTaskList.value.filter(t => t.status === 'EXECUTING').length)
const pendingDispatchCount = computed(() => executingTaskList.value.filter(t => t.status === 'PENDING_DISPATCH').length)
const completedTaskCount = computed(() => executingTaskList.value.filter(t => t.status === 'COMPLETED').length)
const timeoutTaskCount = computed(() => executingTaskList.value.filter(t => t.isTimeout).length)

// AGV远程控制
const agvControlForm = reactive({
  agvNo: '',
  command: '',
  targetPoint: '',
  speed: null,
  reason: '',
  operator: ''
})
const controlCommandLoading = ref(false)
const queryCurrentTaskLoading = ref(false)
const currentAgvTask = ref(null)

// 告警管理
const alarmLoading = ref(false)
const alarmList = ref([])
const alarmLevelFilter = ref('')
const alarmStatusFilter = ref('')
const handlingAlarmId = ref(null)
const alarmDetailVisible = ref(false)
const currentAlarmDetail = ref(null)

const unhandledAlarmCount = computed(() => alarmList.value.filter(a => !a.handled).length)
const criticalAlarmCount = computed(() => alarmList.value.filter(a => a.level === 'ERROR' && !a.handled).length)
const warningAlarmCount = computed(() => alarmList.value.filter(a => a.level === 'WARNING' && !a.handled).length)
const todayAlarmCount = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return alarmList.value.filter(a => new Date(a.createTime) >= today).length
})

const filteredAlarmList = computed(() => {
  let list = alarmList.value
  if (alarmLevelFilter.value) {
    list = list.filter(a => a.level === alarmLevelFilter.value)
  }
  if (alarmStatusFilter.value === 'unhandled') {
    list = list.filter(a => !a.handled)
  } else if (alarmStatusFilter.value === 'handled') {
    list = list.filter(a => a.handled)
  }
  return list
})

const getConflictTypeName = (type) => {
  const map = {
    HEAD_ON: '对向冲突',
    CROSS: '交叉冲突',
    FOLLOW: '跟车冲突',
    RESOURCE: '资源冲突',
    PATH: '路径冲突',
    POSITION: '位置冲突'
  }
  return map[type] || type
}

const getConflictTypeClass = (type) => {
  const map = {
    HEAD_ON: 'danger',
    CROSS: 'warning',
    FOLLOW: 'info',
    RESOURCE: 'warning',
    PATH: 'danger',
    POSITION: 'warning'
  }
  return map[type] || 'info'
}

const getResolutionStrategyName = (strategy) => {
  const map = {
    1: '低优先级等待',
    2: '绕行重规划',
    3: '让行动作（路口会车）',
    4: '任务重分配',
    5: '死锁恢复',
    'WAIT': '低优先级等待',
    'DETOUR': '绕行重规划',
    'YIELD': '让行动作（路口会车）',
    'REASSIGN': '任务重分配',
    'DEADLOCK_RECOVERY': '死锁恢复'
  }
  return map[strategy] || strategy
}

const formatDateTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const parseWaitChain = (waitChain) => {
  if (!waitChain) return []
  try {
    if (typeof waitChain === 'string') {
      return JSON.parse(waitChain)
    }
    return Array.isArray(waitChain) ? waitChain : []
  } catch (e) {
    return [waitChain]
  }
}

const getTaskStatusName = (status) => {
  const map = {
    PENDING_DISPATCH: '待下发',
    DISPATCHED: '已下发',
    EXECUTING: '执行中',
    PAUSED: '已暂停',
    COMPLETED: '已完成',
    CANCELED: '已取消',
    TIMEOUT: '已超时',
    FAILED: '执行失败'
  }
  return map[status] || status
}

const getTaskStatusType = (status) => {
  const map = {
    PENDING_DISPATCH: 'info',
    DISPATCHED: 'warning',
    EXECUTING: 'primary',
    PAUSED: 'warning',
    COMPLETED: 'success',
    CANCELED: 'info',
    TIMEOUT: 'danger',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

const getAlarmLevelName = (level) => {
  const map = {
    ERROR: '错误',
    WARNING: '警告',
    INFO: '信息'
  }
  return map[level] || level
}

const getAlarmLevelType = (level) => {
  const map = {
    ERROR: 'danger',
    WARNING: 'warning',
    INFO: 'info'
  }
  return map[level] || 'info'
}

const formatDuration = (seconds) => {
  if (!seconds || seconds < 0) return '00:00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const handleRefreshAll = () => {
  if (activeTab.value === 'conflict') {
    loadConflictList()
  } else if (activeTab.value === 'deadlock') {
    loadDeadlockList()
  } else if (activeTab.value === 'node-lock') {
    loadNodeLockData()
  } else if (activeTab.value === 'task-monitor') {
    loadExecutingTasks()
  } else if (activeTab.value === 'agv-control-alarm') {
    loadAllAlarms()
  }
}

const loadExecutingTasks = async () => {
  taskMonitorLoading.value = true
  try {
    const data = await dispatchApi.getExecutingTasks()
    executingTaskList.value = data || []
  } catch (error) {
    ElMessage.error(error.message || '加载任务列表失败')
  } finally {
    taskMonitorLoading.value = false
  }
}

const handleRefreshIntervalChange = (val) => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
  if (val && parseInt(val) > 0) {
    autoRefreshTimer = setInterval(() => {
      if (activeTab.value === 'task-monitor') {
        loadExecutingTasks()
      }
    }, parseInt(val))
    ElMessage.success(`已设置${parseInt(val) / 1000}秒自动刷新一次`)
  } else {
    ElMessage.info('已关闭自动刷新')
  }
}

const handleDispatchTask = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要下发任务 ${row.taskNo} 吗？`, '确认下发', {
      type: 'warning'
    })
    operatingTaskId.value = row.id
    operatingAction.value = 'dispatch'
    await dispatchApi.dispatchTask(row.id)
    ElMessage.success('任务下发成功')
    loadExecutingTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '下发失败')
    }
  } finally {
    operatingTaskId.value = null
    operatingAction.value = ''
  }
}

const handlePauseTask = async (row) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入暂停原因', '暂停任务', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (value) => {
        if (!value || value.trim() === '') {
          return '请输入暂停原因'
        }
        return true
      }
    })
    operatingTaskId.value = row.id
    operatingAction.value = 'pause'
    await dispatchApi.pauseTask(row.id, reason)
    ElMessage.success('任务已暂停')
    loadExecutingTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '暂停失败')
    }
  } finally {
    operatingTaskId.value = null
    operatingAction.value = ''
  }
}

const handleResumeTask = async (row) => {
  try {
    await ElMessageBox.confirm('确定要恢复此任务吗？', '恢复任务', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    operatingTaskId.value = row.id
    operatingAction.value = 'resume'
    await dispatchApi.resumeTask(row.id)
    ElMessage.success('任务已恢复')
    loadExecutingTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '恢复失败')
    }
  } finally {
    operatingTaskId.value = null
    operatingAction.value = ''
  }
}

const handleCancelTask = async (row) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消任务', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (value) => {
        if (!value || value.trim() === '') {
          return '请输入取消原因'
        }
        return true
      }
    })
    operatingTaskId.value = row.id
    operatingAction.value = 'cancel'
    await dispatchApi.cancelTask(row.id, reason)
    ElMessage.success('任务已取消')
    loadExecutingTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '取消失败')
    }
  } finally {
    operatingTaskId.value = null
    operatingAction.value = ''
  }
}

const showTaskDetail = (row) => {
  currentTaskDetail.value = row
  taskDetailVisible.value = true
}

const handleQueryCurrentTask = async () => {
  if (!agvControlForm.agvNo) {
    ElMessage.warning('请输入AGV编号')
    return
  }
  queryCurrentTaskLoading.value = true
  try {
    const data = await dispatchApi.getCurrentTaskByAgvNo(agvControlForm.agvNo)
    currentAgvTask.value = data
    if (data) {
      ElMessage.success('查询成功')
    } else {
      ElMessage.info('该AGV当前没有执行任务')
    }
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    queryCurrentTaskLoading.value = false
  }
}

const handleSendControlCommand = async () => {
  if (!agvControlForm.agvNo) {
    ElMessage.warning('请输入AGV编号')
    return
  }
  if (!agvControlForm.command) {
    ElMessage.warning('请选择控制命令')
    return
  }
  if (agvControlForm.command === 'GO_TO_POINT' && !agvControlForm.targetPoint) {
    ElMessage.warning('请输入目标点')
    return
  }
  if (agvControlForm.command === 'SLOW_DOWN' || agvControlForm.command === 'NORMAL_SPEED') {
    if (agvControlForm.speed === null || agvControlForm.speed === undefined) {
      ElMessage.warning('请输入速度')
      return
    }
  }
  if (!agvControlForm.reason) {
    ElMessage.warning('请输入控制原因')
    return
  }
  if (!agvControlForm.operator) {
    ElMessage.warning('请输入操作人')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要对 AGV ${agvControlForm.agvNo} 发送 ${agvControlForm.command} 命令吗？`,
      '确认发送',
      { type: 'warning' }
    )
    controlCommandLoading.value = true
    const controlDTO = {
      agvNo: agvControlForm.agvNo,
      command: agvControlForm.command,
      reason: agvControlForm.reason,
      operator: agvControlForm.operator
    }
    if (agvControlForm.command === 'GO_TO_POINT') {
      controlDTO.targetPoint = agvControlForm.targetPoint
    }
    if (agvControlForm.command === 'SLOW_DOWN' || agvControlForm.command === 'NORMAL_SPEED') {
      controlDTO.speed = agvControlForm.speed
    }
    await dispatchApi.remoteControlAgv(controlDTO)
    ElMessage.success('控制命令发送成功')
    handleQueryCurrentTask()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '发送失败')
    }
  } finally {
    controlCommandLoading.value = false
  }
}

const handleResetControlForm = () => {
  agvControlForm.agvNo = ''
  agvControlForm.command = ''
  agvControlForm.targetPoint = ''
  agvControlForm.speed = null
  agvControlForm.reason = ''
  agvControlForm.operator = ''
  currentAgvTask.value = null
}

const loadAllAlarms = async () => {
  alarmLoading.value = true
  try {
    const data = await dispatchApi.getAllAlarms()
    alarmList.value = data || []
  } catch (error) {
    ElMessage.error(error.message || '加载告警列表失败')
  } finally {
    alarmLoading.value = false
  }
}

const handleHandleAlarm = async (row) => {
  try {
    const { value: handleResult } = await ElMessageBox.prompt('请输入处理结果', '处理告警', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: (value) => {
        if (!value || value.trim() === '') {
          return '请输入处理结果'
        }
        return true
      }
    })
    handlingAlarmId.value = row.id
    await dispatchApi.handleAlarm(row.id, handleResult)
    ElMessage.success('告警已处理')
    loadAllAlarms()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '处理失败')
    }
  } finally {
    handlingAlarmId.value = null
  }
}

const showAlarmDetail = (row) => {
  currentAlarmDetail.value = row
  alarmDetailVisible.value = true
}

const handlePlanPath = async () => {
  if (!pathForm.startPoint || !pathForm.endPoint) {
    ElMessage.warning('请输入起点和终点')
    return
  }
  pathPlanningLoading.value = true
  try {
    const data = await pathPlanningApi.planPath(pathForm.startPoint, pathForm.endPoint, pathForm.algorithm)
    Object.assign(pathResult, data)
    if (data.success) {
      ElMessage.success('路径规划成功')
    } else {
      ElMessage.error(data.message || '路径规划失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '路径规划失败')
  } finally {
    pathPlanningLoading.value = false
  }
}

const handleClearPathResult = () => {
  pathResult.success = false
  pathResult.path = []
  pathResult.totalDistance = 0
  pathResult.estimatedTime = 0
  pathResult.algorithm = ''
  pathResult.hasDetour = false
  pathResult.message = ''
}

const loadConflictList = async () => {
  conflictLoading.value = true
  try {
    const data = await dispatchApi.detectConflicts()
    conflictList.value = data || []
  } catch (error) {
    ElMessage.error(error.message || '加载冲突列表失败')
  } finally {
    conflictLoading.value = false
  }
}

const handleDetectConflicts = async () => {
  try {
    await dispatchApi.detectConflicts()
    ElMessage.success('冲突检测完成')
    loadConflictList()
  } catch (error) {
    ElMessage.error(error.message || '冲突检测失败')
  }
}

const handleResolveConflict = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要解决ID为 ${row.id} 的冲突吗？`, '确认解决', {
      type: 'warning'
    })
    resolvingConflictId.value = row.id
    await dispatchApi.resolveConflict(row.id)
    ElMessage.success('冲突已解决')
    loadConflictList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解决失败')
    }
  } finally {
    resolvingConflictId.value = null
  }
}

const handleResolveAllConflicts = async () => {
  try {
    await ElMessageBox.confirm('确定要自动解决所有未解决的冲突吗？', '确认解决', {
      type: 'warning'
    })
    resolveAllLoading.value = true
    await dispatchApi.resolveAllConflicts()
    ElMessage.success('所有冲突已解决')
    loadConflictList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解决失败')
    }
  } finally {
    resolveAllLoading.value = false
  }
}

const showConflictDetail = (row) => {
  currentConflict.value = row
  conflictDetailVisible.value = true
}

const loadDeadlockList = async () => {
  deadlockLoading.value = true
  try {
    const data = await dispatchApi.getCurrentDeadlocks()
    deadlockList.value = data || []
  } catch (error) {
    ElMessage.error(error.message || '加载死锁列表失败')
  } finally {
    deadlockLoading.value = false
  }
}

const handleDetectDeadlocks = async () => {
  try {
    const data = await dispatchApi.detectDeadlocks()
    deadlockList.value = data || []
    ElMessage.success(`死锁检测完成，检测到 ${data.length} 个死锁`)
  } catch (error) {
    ElMessage.error(error.message || '死锁检测失败')
  }
}

const handleResolveDeadlock = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要解决ID为 ${row.id} 的死锁吗？此操作将强制重新规划路径。`, '确认解决', {
      type: 'warning'
    })
    resolvingDeadlockId.value = row.id
    await dispatchApi.resolveDeadlock(row.id)
    ElMessage.success('死锁已解决')
    loadDeadlockList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解决失败')
    }
  } finally {
    resolvingDeadlockId.value = null
  }
}

const handleResolveAllDeadlocks = async () => {
  try {
    await ElMessageBox.confirm('确定要强制解决所有未解决的死锁吗？此操作将影响多个AGV的路径规划。', '确认解决', {
      type: 'warning'
    })
    resolveAllDeadlockLoading.value = true
    await dispatchApi.resolveAllDeadlocks()
    ElMessage.success('所有死锁已解决')
    loadDeadlockList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '解决失败')
    }
  } finally {
    resolveAllDeadlockLoading.value = false
  }
}

const showDeadlockDetail = (row) => {
  currentDeadlock.value = row
  deadlockDetailVisible.value = true
}

const handleGetNodeLockHolder = async () => {
  if (!nodeLockForm.nodeCode) {
    ElMessage.warning('请输入节点编号')
    return
  }
  nodeLockLoading.value = true
  try {
    const data = await pathPlanningApi.getNodeLockHolder(nodeLockForm.nodeCode)
    nodeLockHolder.value = data
    if (data) {
      ElMessage.info(`节点 ${nodeLockForm.nodeCode} 被 AGV ${data} 锁定`)
    } else {
      ElMessage.info(`节点 ${nodeLockForm.nodeCode} 未被锁定`)
    }
  } catch (error) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    nodeLockLoading.value = false
  }
}

const handleLockNode = async () => {
  if (!nodeLockForm.nodeCode || !nodeLockForm.agvId) {
    ElMessage.warning('请输入节点编号和AGV编号')
    return
  }
  lockNodeLoading.value = true
  try {
    const success = await pathPlanningApi.tryLockNode(nodeLockForm.nodeCode, nodeLockForm.agvId)
    if (success) {
      ElMessage.success(`节点 ${nodeLockForm.nodeCode} 锁定成功`)
      handleGetNodeLockHolder()
    } else {
      ElMessage.warning(`节点 ${nodeLockForm.nodeCode} 锁定失败，可能已被其他AGV锁定`)
    }
  } catch (error) {
    ElMessage.error(error.message || '锁定失败')
  } finally {
    lockNodeLoading.value = false
  }
}

const handleUnlockNode = async () => {
  if (!nodeLockForm.nodeCode || !nodeLockForm.agvId) {
    ElMessage.warning('请输入节点编号和AGV编号')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要释放节点 ${nodeLockForm.nodeCode} 的锁吗？`, '确认释放', {
      type: 'warning'
    })
    unlockNodeLoading.value = true
    await pathPlanningApi.unlockNode(nodeLockForm.nodeCode, nodeLockForm.agvId)
    ElMessage.success(`节点 ${nodeLockForm.nodeCode} 锁释放成功`)
    handleGetNodeLockHolder()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '释放失败')
    }
  } finally {
    unlockNodeLoading.value = false
  }
}

const handleTryIntersectionPass = async () => {
  if (!intersectionForm.intersectionCode || !intersectionForm.agvId) {
    ElMessage.warning('请输入路口编号和AGV编号')
    return
  }
  tryPassLoading.value = true
  try {
    const allowed = await pathPlanningApi.tryIntersectionPass(intersectionForm.intersectionCode, intersectionForm.agvId)
    if (allowed) {
      ElMessage.success(`AGV ${intersectionForm.agvId} 可以通过路口 ${intersectionForm.intersectionCode}`)
    } else {
      ElMessage.warning(`路口 ${intersectionForm.intersectionCode} 已被其他AGV占用，请等待`)
    }
    handleLoadLockedIntersections()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    tryPassLoading.value = false
  }
}

const handleCompleteIntersectionPass = async () => {
  if (!intersectionForm.intersectionCode || !intersectionForm.agvId) {
    ElMessage.warning('请输入路口编号和AGV编号')
    return
  }
  completePassLoading.value = true
  try {
    await pathPlanningApi.completeIntersectionPass(intersectionForm.intersectionCode, intersectionForm.agvId)
    ElMessage.success(`AGV ${intersectionForm.agvId} 已完成通过路口 ${intersectionForm.intersectionCode}`)
    handleLoadLockedIntersections()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    completePassLoading.value = false
  }
}

const handleMarkBlocked = async () => {
  if (!blockedForm.nodeCode) {
    ElMessage.warning('请输入节点编号')
    return
  }
  markBlockedLoading.value = true
  try {
    await pathPlanningApi.markPathBlocked(blockedForm.nodeCode, blockedForm.reason || '手动标记阻塞')
    ElMessage.success(`节点 ${blockedForm.nodeCode} 已标记为阻塞`)
    handleLoadBlockedPaths()
  } catch (error) {
    ElMessage.error(error.message || '标记失败')
  } finally {
    markBlockedLoading.value = false
  }
}

const handleClearBlocked = async () => {
  if (!blockedForm.nodeCode) {
    ElMessage.warning('请输入节点编号')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要清除节点 ${blockedForm.nodeCode} 的阻塞标记吗？`, '确认清除', {
      type: 'warning'
    })
    clearBlockedLoading.value = true
    await pathPlanningApi.clearPathBlocked(blockedForm.nodeCode)
    ElMessage.success(`节点 ${blockedForm.nodeCode} 阻塞已清除`)
    handleLoadBlockedPaths()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '清除失败')
    }
  } finally {
    clearBlockedLoading.value = false
  }
}

const handleLoadOccupiedPaths = async () => {
  occupiedLoading.value = true
  try {
    const data = await pathPlanningApi.getOccupiedPaths()
    occupiedPathsList.value = Object.entries(data || {}).map(([nodeCode, agvId]) => ({
      nodeCode,
      agvId
    }))
  } catch (error) {
    ElMessage.error(error.message || '加载占用节点失败')
  } finally {
    occupiedLoading.value = false
  }
}

const handleLoadBlockedPaths = async () => {
  blockedLoading.value = true
  try {
    const data = await pathPlanningApi.getBlockedPaths()
    blockedPathsList.value = Object.entries(data || {}).map(([nodeCode, reason]) => ({
      nodeCode,
      reason
    }))
  } catch (error) {
    ElMessage.error(error.message || '加载阻塞节点失败')
  } finally {
    blockedLoading.value = false
  }
}

const handleLoadLockedIntersections = async () => {
  lockedLoading.value = true
  try {
    const data = await pathPlanningApi.getLockedIntersections()
    lockedIntersectionsList.value = Object.entries(data || {}).map(([intersectionCode, agvId]) => ({
      intersectionCode,
      agvId
    }))
  } catch (error) {
    ElMessage.error(error.message || '加载锁定路口失败')
  } finally {
    lockedLoading.value = false
  }
}

const loadNodeLockData = () => {
  handleLoadOccupiedPaths()
  handleLoadBlockedPaths()
  handleLoadLockedIntersections()
}

watch(activeTab, (newTab) => {
  if (newTab === 'task-monitor') {
    loadExecutingTasks()
  } else if (newTab === 'agv-control-alarm') {
    loadAllAlarms()
  }
})

onMounted(() => {
  loadConflictList()
  loadDeadlockList()
  loadNodeLockData()
  loadExecutingTasks()
  loadAllAlarms()
})

onUnmounted(() => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  color: #1f2937;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.main-tabs {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.path-stats {
  display: flex;
  align-items: center;
}

.path-visualization {
  padding: 20px;
  background: #f9fafb;
  border-radius: 8px;
}

.path-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.path-node-item {
  display: flex;
  align-items: center;
}

.node-name {
  padding: 8px 16px;
  background: #fff;
  border: 2px solid #3b82f6;
  border-radius: 6px;
  font-weight: 600;
  color: #1f2937;
  min-width: 60px;
  text-align: center;
}

.path-arrow {
  color: #6b7280;
  font-size: 20px;
  font-weight: bold;
  margin: 0 8px;
}

.empty-result {
  padding: 20px 0;
}

.stat-card {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #1f2937;
}

.stat-label {
  font-size: 14px;
  color: #6b7280;
  margin-top: 5px;
}

.conflict-pending {
  border-left: 4px solid #dc2626;
}

.conflict-headon {
  border-left: 4px solid #b91c1c;
}

.conflict-cross {
  border-left: 4px solid #d97706;
}

.conflict-follow {
  border-left: 4px solid #2563eb;
}

.deadlock-pending {
  border-left: 4px solid #dc2626;
}

.deadlock-total {
  border-left: 4px solid #7c3aed;
}

.deadlock-resolved {
  border-left: 4px solid #059669;
}

.wait-chain {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.wait-arrow {
  font-size: 18px;
  color: #dc2626;
  animation: rotate 2s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.node-lock-result {
  margin-top: 20px;
}

.task-executing {
  border-left: 4px solid #2563eb;
}

.task-pending {
  border-left: 4px solid #d97706;
}

.task-completed {
  border-left: 4px solid #059669;
}

.task-timeout {
  border-left: 4px solid #dc2626;
}

.alarm-unhandled {
  border-left: 4px solid #dc2626;
}

.alarm-critical {
  border-left: 4px solid #b91c1c;
}

.alarm-warning {
  border-left: 4px solid #d97706;
}

.alarm-today {
  border-left: 4px solid #7c3aed;
}

.current-task-info {
  margin-top: 20px;
}

.node-name.node-current {
  background: #ef4444;
  color: #fff;
  border-color: #ef4444;
}
</style>
