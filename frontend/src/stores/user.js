import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))
  const permissions = ref([])

  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)
  const isAdmin = computed(() => userInfo.value?.role === 'ADMIN')
  const isDispatcher = computed(() => ['ADMIN', 'DISPATCHER'].includes(userInfo.value?.role))
  const isReadOnly = computed(() => userInfo.value?.role === 'READ_ONLY')
  const roleName = computed(() => userInfo.value?.roleDesc || '')

  async function login(loginData) {
    const res = await authApi.login(loginData)
    token.value = res.token
    userInfo.value = {
      userId: res.userId,
      username: res.username,
      realName: res.realName,
      role: res.role,
      roleDesc: res.roleDesc,
      avatar: res.avatar
    }
    localStorage.setItem('token', res.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    await fetchCurrentUser()
    return res
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      console.error('Logout error:', e)
    } finally {
      token.value = ''
      userInfo.value = null
      permissions.value = []
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }

  async function fetchCurrentUser() {
    try {
      const res = await authApi.getCurrentUser()
      if (res) {
        userInfo.value = res
        permissions.value = res.permissions || []
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
      }
    } catch (e) {
      console.error('Fetch user info error:', e)
    }
  }

  function hasPermission(permission) {
    if (!permission) return true
    if (isAdmin.value) return true
    return permissions.value.includes(permission)
  }

  function hasAnyPermission(permissionList) {
    if (!permissionList || permissionList.length === 0) return true
    return permissionList.some(p => hasPermission(p))
  }

  function updateToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  return {
    token,
    userInfo,
    permissions,
    isLoggedIn,
    isAdmin,
    isDispatcher,
    isReadOnly,
    roleName,
    login,
    logout,
    fetchCurrentUser,
    hasPermission,
    hasAnyPermission,
    updateToken
  }
})
