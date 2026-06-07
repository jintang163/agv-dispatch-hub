<template>
  <router-view v-if="$route.name === 'Login'" />
  <el-container class="app-container" v-else>
    <el-header class="app-header">
      <div class="header-left">
        <el-icon :size="32" color="#409EFF"><Operation /></el-icon>
        <h1>AGV智能调度中心</h1>
      </div>
      <div class="header-right">
        <el-tag :type="connectionType" class="connection-status">
          <el-icon><Connection /></el-icon>
          {{ connectionText }}
        </el-tag>
        <el-dropdown trigger="click">
          <span class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span>{{ userStore.userInfo?.realName || '用户' }}</span>
            <el-tag size="small" :type="roleTagType">{{ userStore.roleName }}</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                <el-icon><User /></el-icon>
                {{ userStore.userInfo?.username }}
              </el-dropdown-item>
              <el-dropdown-item disabled>
                <el-icon><Message /></el-icon>
                {{ userStore.userInfo?.email || '-' }}
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-container>
      <el-aside width="220px" class="app-aside">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#001529"
          text-color="#fff"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>实时监控</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('task:view')" index="/tasks">
            <el-icon><List /></el-icon>
            <span>任务管理</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('task:view')" index="/queue">
            <el-icon><Sort /></el-icon>
            <span>任务队列</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('agv:view')" index="/agvs">
            <el-icon><Van /></el-icon>
            <span>AGV管理</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('task:dispatch')" index="/dispatch">
            <el-icon><Setting /></el-icon>
            <span>调度控制</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('agv:view')" index="/path-planning">
            <el-icon><Connection /></el-icon>
            <span>路径规划</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('task:view')" index="/monitor-screen">
            <el-icon><Monitor /></el-icon>
            <span>监控大屏</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('user:view')" index="/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPermission('log:view')" index="/operation-logs">
            <el-icon><Document /></el-icon>
            <span>操作日志</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Monitor, Document, SwitchButton, Message } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeMenu = computed(() => route.path)

const connected = ref(true)
const connectionType = computed(() => connected.value ? 'success' : 'danger')
const connectionText = computed(() => connected.value ? '已连接' : '断开连接')

const roleTagType = computed(() => {
  const role = userStore.userInfo?.role
  switch (role) {
    case 'ADMIN': return 'danger'
    case 'DISPATCHER': return 'primary'
    case 'READ_ONLY': return 'info'
    default: return 'info'
  }
})

onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.permissions.length) {
    try {
      await userStore.fetchCurrentUser()
    } catch (e) {
      console.error('Failed to fetch user info:', e)
    }
  }
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '确认退出', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (e) {
    if (e !== 'cancel') {
      console.error('Logout error:', e)
    }
  }
}
</script>

<style lang="scss" scoped>
.app-container {
  height: 100vh;
  overflow: hidden;
}

.app-header {
  background: linear-gradient(90deg, #001529 0%, #002140 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #1890ff20;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    h1 {
      font-size: 20px;
      margin: 0;
      font-weight: 500;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 20px;

    .connection-status {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
    }
  }
}

.app-aside {
  background: #001529;
  border-right: 1px solid #1890ff20;

  :deep(.el-menu) {
    border-right: none;
  }
}

.app-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
