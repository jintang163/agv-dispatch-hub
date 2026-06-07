import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || '登录已过期'))
    }
    if (res.code === 403) {
      ElMessage.error('没有权限执行此操作')
      return Promise.reject(new Error(res.message || '没有权限'))
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  error => {
    if (error.response?.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else if (error.response?.status === 403) {
      ElMessage.error('没有权限执行此操作')
    }
    return Promise.reject(error)
  }
)

export const taskApi = {
  create: (data) => request.post('/tasks', data),
  getById: (id) => request.get(`/tasks/${id}`),
  query: (data) => request.post('/tasks/query', data),
  assign: (data) => request.post('/tasks/assign', data),
  autoAssign: (id) => request.post(`/tasks/${id}/auto-assign`),
  cancel: (data) => request.post('/tasks/cancel', data),
  reassign: (data) => request.post('/tasks/reassign', data),
  updatePriority: (data) => request.post('/tasks/priority', data),
  getQueue: () => request.get('/tasks/queue'),
  getQueueSize: () => request.get('/tasks/queue/size'),
  refreshQueue: () => request.post('/tasks/queue/refresh'),
  getLogs: (id) => request.get(`/tasks/${id}/logs`),
  getStatistics: () => request.get('/tasks/statistics')
}

export const agvApi = {
  list: (status) => request.get('/agvs', { params: { status } }),
  getById: (id) => request.get(`/agvs/${id}`),
  getCurrentTask: (id) => request.get(`/agvs/${id}/current-task`),
  getAvailable: () => request.get('/agvs/available'),
  pause: (id) => request.post(`/agvs/${id}/pause`),
  resume: (id) => request.post(`/agvs/${id}/resume`),
  stop: (id) => request.post(`/agvs/${id}/stop`),
  charge: (id, station) => request.post(`/agvs/${id}/charge`, null, { params: { chargingStation: station } }),
  getStatistics: () => request.get('/agvs/statistics')
}

export const dispatchApi = {
  /**
   * 检测所有AGV之间的冲突
   * @returns 冲突列表
   */
  detectConflicts: () => request.get('/dispatch/conflicts'),
  /**
   * 获取所有未解决的冲突
   * @returns 未解决冲突列表
   */
  getUnresolvedConflicts: () => request.get('/dispatch/conflicts/unresolved'),
  /**
   * 解决指定的冲突
   * @param {number} id 冲突ID
   * @returns 解决结果
   */
  resolveConflict: (id) => request.post(`/dispatch/conflicts/${id}/resolve`),
  /**
   * 自动解决所有未解决的冲突
   * @returns 解决结果
   */
  resolveAllConflicts: () => request.post('/dispatch/conflicts/resolve-all'),
  /**
   * 检测AGV死锁情况
   * @returns 死锁列表
   */
  detectDeadlocks: () => request.get('/dispatch/deadlocks/detect'),
  /**
   * 获取当前所有死锁
   * @returns 死锁列表
   */
  getCurrentDeadlocks: () => request.get('/dispatch/deadlocks'),
  /**
   * 解决指定的死锁
   * @param {number} id 死锁ID
   * @returns 解决结果
   */
  resolveDeadlock: (id) => request.post(`/dispatch/deadlocks/${id}/resolve`),
  /**
   * 解决所有死锁
   * @returns 解决结果
   */
  resolveAllDeadlocks: () => request.post('/dispatch/deadlocks/resolve-all'),
  /**
   * 动态重规划任务路径（遇到阻塞时）
   * @param {number} taskId 任务ID
   * @param {string} blockedNode 阻塞节点
   * @param {string} reason 重规划原因
   * @param {string} operator 操作人
   * @returns 重规划结果
   */
  dynamicReplanTask: (taskId, blockedNode, reason, operator) => request.post(`/dispatch/tasks/${taskId}/dynamic-replan`, null, { params: { blockedNode, reason, operator } }),
  /**
   * 从当前位置重新规划任务路径
   * @param {number} taskId 任务ID
   * @param {string} operator 操作人
   * @returns 重规划结果
   */
  replanTaskFromCurrent: (taskId, operator) => request.post(`/dispatch/tasks/${taskId}/replan`, null, { params: { operator } }),
  /**
   * 处理路径阻塞事件
   * @param {string} agvId AGV编号
   * @param {string} blockedNode 阻塞节点
   * @param {string} reason 阻塞原因
   * @returns 处理结果
   */
  handlePathBlocked: (agvId, blockedNode, reason) => request.post('/dispatch/path-blocked', null, { params: { agvId, blockedNode, reason } }),
  /**
   * 标记路径节点为阻塞
   * @param {string} nodeCode 节点编号
   * @param {string} reason 阻塞原因
   * @returns 标记结果
   */
  markPathBlocked: (nodeCode, reason) => request.post(`/dispatch/blocked/${nodeCode}`, null, { params: { reason } }),
  /**
   * 清除路径节点的阻塞标记
   * @param {string} nodeCode 节点编号
   * @returns 清除结果
   */
  clearPathBlocked: (nodeCode) => request.delete(`/dispatch/blocked/${nodeCode}`),
  /**
   * 获取所有阻塞的路径
   * @returns 阻塞路径列表
   */
  getBlockedPaths: () => request.get('/dispatch/blocked-paths'),
  /**
   * 获取所有锁定的路口
   * @returns 锁定路口列表
   */
  getLockedIntersections: () => request.get('/dispatch/locked-intersections'),
  /**
   * 下发任务，开始执行
   * @param {number} taskId 任务ID
   * @returns 下发结果
   */
  dispatchTask: (taskId) => request.post(`/dispatch/tasks/${taskId}/dispatch`),
  /**
   * 暂停执行中的任务
   * @param {number} taskId 任务ID
   * @param {string} operator 操作人
   * @param {string} reason 暂停原因
   * @returns 暂停结果
   */
  pauseTask: (taskId, operator, reason) => request.post(`/dispatch/tasks/${taskId}/pause`, null, { params: { operator, reason } }),
  /**
   * 恢复已暂停的任务
   * @param {number} taskId 任务ID
   * @param {string} operator 操作人
   * @returns 恢复结果
   */
  resumeTask: (taskId, operator) => request.post(`/dispatch/tasks/${taskId}/resume`, null, { params: { operator } }),
  /**
   * 取消任务
   * @param {number} taskId 任务ID
   * @param {string} operator 操作人
   * @param {string} reason 取消原因
   * @returns 取消结果
   */
  cancelTask: (taskId, operator, reason) => request.post(`/dispatch/tasks/${taskId}/cancel`, null, { params: { operator, reason } }),
  /**
   * 获取所有正在执行的任务
   * @returns 执行中任务列表
   */
  getExecutingTasks: () => request.get('/dispatch/tasks/executing'),
  /**
   * 根据AGV编号获取其当前执行的任务
   * @param {string} agvNo AGV编号
   * @returns 当前任务信息
   */
  getCurrentTaskByAgvNo: (agvNo) => request.get(`/dispatch/agvs/${agvNo}/current-task`),
  /**
   * 远程控制AGV
   * @param {Object} controlDTO 控制命令DTO
   * @param {string} controlDTO.agvNo AGV编号
   * @param {string} controlDTO.command 控制命令
   * @param {string} [controlDTO.targetPoint] 目标点（GO_TO_POINT时需要）
   * @param {number} [controlDTO.speed] 速度（SLOW_DOWN/NORMAL_SPEED时需要）
   * @param {string} controlDTO.reason 控制原因
   * @param {string} controlDTO.operator 操作人
   * @returns 控制结果
   */
  remoteControlAgv: (controlDTO) => request.post('/dispatch/agvs/control', controlDTO),
  /**
   * 获取所有未处理的告警
   * @returns 未处理告警列表
   */
  getUnhandledAlarms: () => request.get('/dispatch/alarms/unhandled'),
  /**
   * 获取所有告警记录
   * @returns 告警列表
   */
  getAllAlarms: () => request.get('/dispatch/alarms'),
  /**
   * 处理告警
   * @param {number} alarmId 告警ID
   * @param {string} handleResult 处理结果
   * @param {string} handler 处理人
   * @returns 处理结果
   */
  handleAlarm: (alarmId, handleResult, handler) => request.post(`/dispatch/alarms/${alarmId}/handle`, null, { params: { handleResult, handler } })
}

export const pathPlanningApi = {
  planPath: (startPoint, endPoint, algorithm) => request.get('/path-planning/plan', { params: { startPoint, endPoint, algorithm } }),
  getNodeLockHolder: (nodeCode) => request.get(`/path-planning/node-lock/${nodeCode}/holder`),
  tryLockNode: (nodeCode, agvId) => request.post(`/path-planning/node-lock/${nodeCode}`, null, { params: { agvId } }),
  unlockNode: (nodeCode, agvId) => request.delete(`/path-planning/node-lock/${nodeCode}`, { params: { agvId } }),
  tryIntersectionPass: (intersectionCode, agvId) => request.post(`/path-planning/intersection-pass/${intersectionCode}`, null, { params: { agvId } }),
  completeIntersectionPass: (intersectionCode, agvId) => request.post(`/path-planning/intersection-pass/${intersectionCode}/complete`, null, { params: { agvId } }),
  getOccupiedPaths: () => request.get('/path-planning/occupied'),
  getBlockedPaths: () => request.get('/path-planning/blocked'),
  getLockedIntersections: () => request.get('/path-planning/locked-intersections'),
  markPathBlocked: (nodeCode, reason) => request.post(`/path-planning/blocked/${nodeCode}`, null, { params: { reason } }),
  clearPathBlocked: (nodeCode) => request.delete(`/path-planning/blocked/${nodeCode}`),
  initGraph: () => request.post('/path-planning/graph/init')
}

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
  getCurrentUser: () => request.get('/auth/me'),
  validateToken: () => request.get('/auth/validate')
}

export const userApi = {
  list: (params) => request.get('/users', { params }),
  getById: (id) => request.get(`/users/${id}`),
  getByUsername: (username) => request.get(`/users/username/${username}`),
  getAll: () => request.get('/users/all'),
  getByRole: (role) => request.get(`/users/role/${role}`),
  create: (data) => request.post('/users', data),
  update: (id, data) => request.put(`/users/${id}`, data),
  delete: (id) => request.delete(`/users/${id}`),
  checkUsername: (username) => request.get('/users/check-username', { params: { username } }),
  getRoles: () => request.get('/users/roles')
}

export const operationLogApi = {
  list: (params) => request.get('/operation-logs', { params }),
  getById: (id) => request.get(`/operation-logs/${id}`),
  getRecent: (limit) => request.get('/operation-logs/recent', { params: { limit } }),
  getByUser: (operator) => request.get(`/operation-logs/user/${operator}`),
  getStatistics: (params) => request.get('/operation-logs/statistics', { params }),
  getTypes: () => request.get('/operation-logs/types'),
  export: (params) => request.get('/operation-logs/export', { params })
}

export const websocketService = {
  connect: () => {
    const SockJS = require('sockjs-client')
    const Stomp = require('stompjs')
    const socket = new SockJS('/ws')
    return Stomp.over(socket)
  }
}

export default request
