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
  detectConflicts: () => request.get('/dispatch/conflicts'),
  getUnresolvedConflicts: () => request.get('/dispatch/conflicts/unresolved'),
  resolveConflict: (id) => request.post(`/dispatch/conflicts/${id}/resolve`),
  resolveAllConflicts: () => request.post('/dispatch/conflicts/resolve-all'),
  planPath: (start, end) => request.get('/dispatch/path/plan', { params: { startPoint: start, endPoint: end } }),
  getOccupiedPaths: () => request.get('/dispatch/path/occupied'),
  initGraph: () => request.post('/dispatch/path/init')
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
