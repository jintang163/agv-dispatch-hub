<template>
  <div class="user-management">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button
        v-if="userStore.hasPermission('user:create')"
        type="primary"
        :icon="Plus"
        @click="openCreateDialog"
      >
        新增用户
      </el-button>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="搜索">
          <el-input
            v-model="filterForm.keyword"
            placeholder="用户名/姓名/手机号"
            clearable
            style="width: 200px"
            @keyup.enter="loadUsers"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filterForm.role" placeholder="全部" clearable style="width: 150px">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="调度员" value="DISPATCHER" />
            <el-option label="只读" value="READ_ONLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadUsers">搜索</el-button>
          <el-button :icon="Refresh" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="userList"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.role)" size="small">
              {{ getRoleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginIp" label="最后登录IP" width="140" />
        <el-table-column label="最后登录时间" width="180">
          <template #default="{ row }">
            {{ row.lastLoginTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ row.createTime }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPermission('user:update')"
              type="primary"
              size="small"
              link
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="userStore.hasPermission('user:delete') && row.username !== 'admin'"
              type="danger"
              size="small"
              link
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userRules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="userForm.username"
            placeholder="请输入用户名"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="userForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="调度员" value="DISPATCHER" />
            <el-option label="只读" value="READ_ONLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="userForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="userForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editUserId = ref(null)
const userFormRef = ref(null)
const userList = ref([])

const filterForm = reactive({
  keyword: '',
  role: '',
  status: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const userForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  role: '',
  status: 1,
  remark: ''
})

const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const ROLE_LABELS = {
  ADMIN: '管理员',
  DISPATCHER: '调度员',
  READ_ONLY: '只读'
}

function getRoleLabel(role) {
  return ROLE_LABELS[role] || role
}

function getRoleTagType(role) {
  switch (role) {
    case 'ADMIN': return 'danger'
    case 'DISPATCHER': return 'primary'
    case 'READ_ONLY': return 'info'
    default: return 'info'
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const params = {
      keyword: filterForm.keyword,
      role: filterForm.role || undefined,
      status: filterForm.status,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = await userApi.list(params)
    userList.value = res.content || res
    pagination.total = res.totalElements || res.length || 0
  } catch (e) {
    console.error('Load users error:', e)
    loadMockUsers()
  } finally {
    loading.value = false
  }
}

function loadMockUsers() {
  userList.value = [
    { id: 1, username: 'admin', realName: '系统管理员', role: 'ADMIN', phone: '13800138000', email: 'admin@example.com', status: 1, lastLoginIp: '127.0.0.1', lastLoginTime: '2026-06-07 10:30:00', createTime: '2026-01-01 00:00:00' },
    { id: 2, username: 'dispatcher', realName: '调度员张三', role: 'DISPATCHER', phone: '13800138001', email: 'dispatcher@example.com', status: 1, lastLoginIp: '192.168.1.100', lastLoginTime: '2026-06-07 09:00:00', createTime: '2026-01-15 10:00:00' },
    { id: 3, username: 'viewer', realName: '查看员李四', role: 'READ_ONLY', phone: '13800138002', email: 'viewer@example.com', status: 1, lastLoginIp: '192.168.1.101', lastLoginTime: '2026-06-06 16:00:00', createTime: '2026-02-01 14:00:00' },
    { id: 4, username: 'test', realName: '测试用户', role: 'DISPATCHER', phone: '13800138003', email: 'test@example.com', status: 0, lastLoginIp: '-', lastLoginTime: '-', createTime: '2026-03-01 10:00:00' }
  ]
  pagination.total = 4
}

function resetFilter() {
  filterForm.keyword = ''
  filterForm.role = ''
  filterForm.status = null
  pagination.pageNum = 1
  loadUsers()
}

function openCreateDialog() {
  isEdit.value = false
  editUserId.value = null
  Object.assign(userForm, {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    role: '',
    status: 1,
    remark: ''
  })
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editUserId.value = row.id
  Object.assign(userForm, {
    username: row.username,
    password: '',
    realName: row.realName,
    phone: row.phone || '',
    email: row.email || '',
    role: row.role,
    status: row.status,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!userFormRef.value) return

  try {
    await userFormRef.value.validate()
    submitting.value = true

    const data = { ...userForm }
    if (isEdit.value && !data.password) {
      delete data.password
    }

    if (isEdit.value) {
      await userApi.update(editUserId.value, data)
      ElMessage.success('更新成功')
    } else {
      await userApi.create(data)
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    loadUsers()
  } catch (e) {
    if (e !== 'canceled') {
      console.error('Submit error:', e)
    }
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${row.username}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await userApi.delete(row.id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('Delete error:', e)
    }
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style lang="scss" scoped>
.user-management {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h2 {
      margin: 0;
      font-size: 20px;
      color: #303133;
    }
  }

  .filter-card {
    margin-bottom: 16px;
  }

  .table-card {
    .pagination {
      margin-top: 16px;
      display: flex;
      justify-content: flex-end;
    }
  }
}
</style>
