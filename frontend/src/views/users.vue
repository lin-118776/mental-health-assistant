<template>
    <div>
        <PageHead title="用户管理" />
        <el-form inline class="user-filters" @submit.prevent="search">
            <el-form-item label="关键词"><el-input v-model="keyword" maxlength="100" clearable placeholder="用户名、昵称、邮箱或手机号" style="width: 300px" /></el-form-item>
            <el-form-item label="状态"><el-select v-model="status" clearable placeholder="全部状态" style="width: 130px"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select></el-form-item>
            <el-form-item><el-button type="primary" native-type="submit" :disabled="busyId !== null">搜索</el-button><el-button :disabled="busyId !== null" @click="reset">重置</el-button></el-form-item>
        </el-form>
        <el-alert v-if="error" title="用户列表加载失败，请重试" type="error" :closable="false" />
        <el-button v-if="error" @click="loadUsers">重试</el-button>
        <el-table v-loading="loading" :data="rows" empty-text="没有符合条件的用户">
            <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="nickname" label="昵称" min-width="100" show-overflow-tooltip />
            <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
            <el-table-column prop="phone" label="手机号" width="135" />
            <el-table-column label="角色" width="100"><template #default="scope">{{ scope.row.userType === 2 ? '管理员' : '普通用户' }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">{{ scope.row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="注册时间" width="175"><template #default="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="scope">
                <el-button v-if="scope.row.userType === 1" text :type="scope.row.status === 1 ? 'danger' : 'primary'" :loading="busyId === scope.row.id" :disabled="busyId !== null || loading" @click="changeStatus(scope.row)">{{ scope.row.status === 1 ? '禁用' : '启用' }}</el-button>
                <span v-else>—</span>
            </template></el-table-column>
        </el-table>
        <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev, pager, next, total" :disabled="loading || busyId !== null" @current-change="loadUsers" />
        <p class="user-note">仅支持调整普通用户状态。禁用后将阻止登录及后续接口访问，保留原有日志和咨询记录。</p>
    </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import { getAdminUsers, updateUserStatus } from '@/api/admin'

const keyword = ref('')
const status = ref(null)
const rows = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref(false)
const busyId = ref(null)
let appliedFilters = {}
let requestId = 0
let disposed = false
const formatTime = value => value ? value.replace('T', ' ').slice(0, 19) : '--'

const loadUsers = async () => {
    const id = ++requestId
    loading.value = true
    error.value = false
    try {
        const res = await getAdminUsers({ ...appliedFilters, page: page.value, size: 10 })
        if (disposed || id !== requestId) return
        total.value = res.data.total
        const lastPage = Math.max(1, Math.ceil(total.value / 10))
        if (page.value > lastPage) {
            page.value = lastPage
            return loadUsers()
        }
        rows.value = res.data.records
    } catch {
        if (!disposed && id === requestId) {
            rows.value = []
            total.value = 0
            error.value = true
        }
    } finally {
        if (!disposed && id === requestId) loading.value = false
    }
}
const search = () => {
    if (busyId.value !== null) return
    appliedFilters = { keyword: keyword.value.trim(), status: status.value === 0 || status.value === 1 ? status.value : undefined }
    page.value = 1
    return loadUsers()
}
const reset = () => {
    if (busyId.value !== null) return
    keyword.value = ''
    status.value = null
    return search()
}
const changeStatus = async user => {
    if (busyId.value !== null || loading.value || user.userType !== 1) return
    const next = user.status === 1 ? 0 : 1
    const action = next === 1 ? '启用' : '禁用'
    busyId.value = user.id
    try {
        await ElMessageBox.confirm(`确定${action}用户“${user.nickname || user.username}”吗？`, `${action}账号`, { type: 'warning' })
        if (disposed) return
        await updateUserStatus(user.id, next)
        if (disposed) return
        ElMessage.success(`账号已${action}`)
        await loadUsers()
    } catch {
        // 取消操作或请求失败时，保留列表中原有状态。
    } finally {
        if (!disposed) busyId.value = null
    }
}
onMounted(loadUsers)
onBeforeUnmount(() => { disposed = true; requestId++ })
</script>

<style scoped>
.user-filters { margin-top: 20px; }
.user-note { color: #6b7280; font-size: 13px; margin-top: 20px; }
.el-pagination { margin-top: 20px; justify-content: center; }
</style>
