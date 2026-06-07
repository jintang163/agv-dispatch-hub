import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '实时监控' }
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('@/views/TaskManagement.vue'),
    meta: { title: '任务管理' }
  },
  {
    path: '/queue',
    name: 'Queue',
    component: () => import('@/views/TaskQueue.vue'),
    meta: { title: '任务队列' }
  },
  {
    path: '/agvs',
    name: 'Agvs',
    component: () => import('@/views/AgvManagement.vue'),
    meta: { title: 'AGV管理' }
  },
  {
    path: '/dispatch',
    name: 'Dispatch',
    component: () => import('@/views/DispatchControl.vue'),
    meta: { title: '调度控制' }
  },
  {
    path: '/path-planning',
    name: 'PathPlanning',
    component: () => import('@/views/PathPlanning.vue'),
    meta: { title: '路径规划与冲突解决' }
  },
  {
    path: '/monitor-screen',
    name: 'MonitorScreen',
    component: () => import('@/views/MonitorScreen.vue'),
    meta: { title: '监控大屏' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - AGV调度中心` : 'AGV调度中心'
  next()
})

export default router
