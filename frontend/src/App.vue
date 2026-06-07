<template>
  <el-container class="app-container">
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
        <el-dropdown>
          <span class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span>管理员</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>个人中心</el-dropdown-item>
              <el-dropdown-item divided>退出登录</el-dropdown-item>
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
          <el-menu-item index="/tasks">
            <el-icon><List /></el-icon>
            <span>任务管理</span>
          </el-menu-item>
          <el-menu-item index="/queue">
            <el-icon><Sort /></el-icon>
            <span>任务队列</span>
          </el-menu-item>
          <el-menu-item index="/agvs">
            <el-icon><Van /></el-icon>
            <span>AGV管理</span>
          </el-menu-item>
          <el-menu-item index="/dispatch">
            <el-icon><Setting /></el-icon>
            <span>调度控制</span>
          </el-menu-item>
          <el-menu-item index="/monitor-screen">
            <el-icon><Monitor /></el-icon>
            <span>监控大屏</span>
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
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)

const connected = ref(true)
const connectionType = computed(() => connected.value ? 'success' : 'danger')
const connectionText = computed(() => connected.value ? '已连接' : '断开连接')
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
