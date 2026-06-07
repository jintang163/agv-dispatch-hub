import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '实时监控', requiresAuth: true, permissions: ['task:view', 'agv:view'] }
  },
  {
    path: '/tasks',
    name: 'Tasks',
    component: () => import('@/views/TaskManagement.vue'),
    meta: { title: '任务管理', requiresAuth: true, permissions: ['task:view'] }
  },
  {
    path: '/queue',
    name: 'Queue',
    component: () => import('@/views/TaskQueue.vue'),
    meta: { title: '任务队列', requiresAuth: true, permissions: ['task:view'] }
  },
  {
    path: '/agvs',
    name: 'Agvs',
    component: () => import('@/views/AgvManagement.vue'),
    meta: { title: 'AGV管理', requiresAuth: true, permissions: ['agv:view'] }
  },
  {
    path: '/dispatch',
    name: 'Dispatch',
    component: () => import('@/views/DispatchControl.vue'),
    meta: { title: '调度控制', requiresAuth: true, permissions: ['task:dispatch'] }
  },
  {
    path: '/path-planning',
    name: 'PathPlanning',
    component: () => import('@/views/PathPlanning.vue'),
    meta: { title: '路径规划与冲突解决', requiresAuth: true, permissions: ['agv:view'] }
  },
  {
    path: '/monitor-screen',
    name: 'MonitorScreen',
    component: () => import('@/views/MonitorScreen.vue'),
    meta: { title: '监控大屏', requiresAuth: true, permissions: ['task:view', 'agv:view'] }
  },
  {
    path: '/users',
    name: 'Users',
    component: () => import('@/views/UserManagement.vue'),
    meta: { title: '用户管理', requiresAuth: true, permissions: ['user:view'] }
  },
  {
    path: '/operation-logs',
    name: 'OperationLogs',
    component: () => import('@/views/OperationLog.vue'),
    meta: { title: '操作日志', requiresAuth: true, permissions: ['log:view'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - AGV调度中心` : 'AGV调度中心'

  const userStore = useUserStore()

  if (to.meta.requiresAuth === false) {
    if (to.path === '/login' && userStore.isLoggedIn) {
      next('/dashboard')
    } else {
      next()
    }
    return
  }

  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.permissions && !userStore.hasAnyPermission(to.meta.permissions)) {
    next('/403')
    return
  }

  next()
})

export default router
