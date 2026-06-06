import axios from 'axios'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

request.interceptors.request.use(
  config => config,
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  error => Promise.reject(error)
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
  // 冲突检测与解决
  detectConflicts: () => request.get('/dispatch/conflicts'),
  getUnresolvedConflicts: () => request.get('/dispatch/conflicts/unresolved'),
  resolveConflict: (id) => request.post(`/dispatch/conflicts/${id}/resolve`),
  resolveAllConflicts: () => request.post('/dispatch/conflicts/resolve-all'),
  // 死锁检测与处理
  detectDeadlocks: () => request.get('/dispatch/deadlocks/detect'),
  getCurrentDeadlocks: () => request.get('/dispatch/deadlocks'),
  resolveDeadlock: (id) => request.post(`/dispatch/deadlocks/${id}/resolve`),
  resolveAllDeadlocks: () => request.post('/dispatch/deadlocks/resolve-all'),
  // 动态重规划
  dynamicReplanTask: (taskId, blockedNode, reason, operator) => request.post(`/dispatch/tasks/${taskId}/dynamic-replan`, null, { params: { blockedNode, reason, operator } }),
  replanTaskFromCurrent: (taskId, operator) => request.post(`/dispatch/tasks/${taskId}/replan`, null, { params: { operator } }),
  handlePathBlocked: (agvId, blockedNode, reason) => request.post('/dispatch/path-blocked', null, { params: { agvId, blockedNode, reason } }),
  // 状态查询
  markPathBlocked: (nodeCode, reason) => request.post(`/dispatch/blocked/${nodeCode}`, null, { params: { reason } }),
  clearPathBlocked: (nodeCode) => request.delete(`/dispatch/blocked/${nodeCode}`),
  getBlockedPaths: () => request.get('/dispatch/blocked-paths'),
  getLockedIntersections: () => request.get('/dispatch/locked-intersections')
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

export const websocketService = {
  connect: () => {
    const SockJS = require('sockjs-client')
    const Stomp = require('stompjs')
    const socket = new SockJS('/ws')
    return Stomp.over(socket)
  }
}

export default request
