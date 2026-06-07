<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo">
          <el-icon :size="48" color="#3b82f6">
            <component :is="'Connection'" />
          </el-icon>
        </div>
        <h1 class="title">AGV 调度系统</h1>
        <p class="subtitle">智能仓储物流调度管理平台</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            clearable
          >
            <template #prefix>
              <el-icon><component :is="'User'" /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            clearable
          >
            <template #prefix>
              <el-icon><component :is="'Lock'" /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="login-footer">
        <p class="tip">默认账号：admin / dispatcher / viewer</p>
        <p class="tip">默认密码：admin123 / dispatcher123 / viewer123</p>
      </div>
    </div>

    <div class="bg-animation">
      <div class="circle c1"></div>
      <div class="circle c2"></div>
      <div class="circle c3"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
    loading.value = true

    const data = {
      username: loginForm.username.trim(),
      password: loginForm.password
    }

    await userStore.login(data)
    ElMessage.success('登录成功')

    const redirect = router.currentRoute.value.query.redirect || '/dashboard'
    router.push(redirect)
  } catch (error) {
    if (error.message !== 'canceled') {
      ElMessage.error(error.message || '登录失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.bg-animation {
  position: absolute;
  inset: 0;
  overflow: hidden;

  .circle {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.1);
    animation: float 15s infinite ease-in-out;

    &.c1 {
      width: 400px;
      height: 400px;
      top: -100px;
      left: -100px;
    }

    &.c2 {
      width: 300px;
      height: 300px;
      bottom: -50px;
      right: -50px;
      animation-delay: -5s;
    }

    &.c3 {
      width: 200px;
      height: 200px;
      top: 50%;
      left: 50%;
      animation-delay: -10s;
    }
  }
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(30px, -30px) scale(1.1);
  }
  66% {
    transform: translate(-20px, 20px) scale(0.9);
  }
}

.login-box {
  position: relative;
  z-index: 10;
  width: 420px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);

  .login-header {
    text-align: center;
    margin-bottom: 32px;

    .logo {
      width: 80px;
      height: 80px;
      margin: 0 auto 16px;
      background: linear-gradient(135deg, #3b82f6, #8b5cf6);
      border-radius: 20px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .title {
      font-size: 28px;
      font-weight: 700;
      color: #1f2937;
      margin: 0 0 8px;
    }

    .subtitle {
      font-size: 14px;
      color: #6b7280;
      margin: 0;
    }
  }

  .login-form {
    .login-btn {
      width: 100%;
      height: 48px;
      font-size: 16px;
      font-weight: 600;
      margin-top: 8px;
      background: linear-gradient(135deg, #3b82f6, #8b5cf6);
      border: none;

      &:hover {
        opacity: 0.9;
      }
    }
  }

  .login-footer {
    margin-top: 24px;
    text-align: center;

    .tip {
      font-size: 12px;
      color: #9ca3af;
      margin: 4px 0;
    }
  }
}
</style>
