<template>
    <main class="profile-page" v-loading="loading">
        <h1>个人中心</h1>
        <el-alert v-if="loadError" title="个人资料加载失败，请重试" type="error" :closable="false" />
        <el-button v-if="loadError" @click="loadProfile">重试</el-button>
        <template v-if="loaded && !loadError">
            <section class="profile-card">
                <h2>个人资料</h2>
                <el-form label-width="90px" :disabled="saving || changingPassword">
                    <el-form-item label="用户名"><el-input :model-value="username" disabled /></el-form-item>
                    <el-form-item label="昵称"><el-input v-model="form.nickname" maxlength="50" show-word-limit /></el-form-item>
                    <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="100" type="email" /></el-form-item>
                    <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11" type="tel" /></el-form-item>
                    <el-form-item label="性别">
                        <el-select v-model="form.gender"><el-option label="未设置" :value="0" /><el-option label="男" :value="1" /><el-option label="女" :value="2" /></el-select>
                    </el-form-item>
                    <el-form-item label="生日"><el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" :disabled-date="futureDate" /></el-form-item>
                    <el-form-item><el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button></el-form-item>
                </el-form>
            </section>
            <section class="profile-card">
                <h2>修改密码</h2>
                <p>请先验证原密码。修改成功后，所有已登录设备都需要重新登录。</p>
                <el-form label-width="90px" :disabled="saving || changingPassword" @submit.prevent="savePassword">
                    <el-form-item label="原密码"><el-input v-model="password.oldPassword" type="password" show-password autocomplete="current-password" maxlength="255" /></el-form-item>
                    <el-form-item label="新密码"><el-input v-model="password.newPassword" type="password" show-password autocomplete="new-password" maxlength="50" placeholder="6–50 个字符" /></el-form-item>
                    <el-form-item label="确认密码"><el-input v-model="password.confirmPassword" type="password" show-password autocomplete="new-password" maxlength="50" /></el-form-item>
                    <el-form-item><el-button type="primary" :loading="changingPassword" native-type="submit">修改密码</el-button></el-form-item>
                </el-form>
            </section>
        </template>
    </main>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyProfile, updateMyProfile, updateMyPassword } from '@/api/frontend'
import { setSessionUser, clearSession } from '@/utils/session.js'

const router = useRouter()
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)
const saving = ref(false)
const changingPassword = ref(false)
const username = ref('')
const form = reactive({ nickname: '', email: '', phone: '', gender: 0, birthday: null })
const password = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
let disposed = false

const applyProfile = user => {
    username.value = user.username
    Object.assign(form, { nickname: user.nickname || '', email: user.email || '', phone: user.phone || '',
        gender: user.gender ?? 0, birthday: user.birthday || null })
    setSessionUser(user)
}
const loadProfile = async () => {
    if (loading.value) return
    loading.value = true
    loadError.value = false
    try {
        const res = await getMyProfile()
        if (disposed) return
        applyProfile(res.data)
        loaded.value = true
    } catch {
        if (!disposed) loadError.value = true
    } finally {
        if (!disposed) loading.value = false
    }
}
const futureDate = date => {
    const today = new Date()
    today.setHours(23, 59, 59, 999)
    return date > today
}
const saveProfile = async () => {
    if (!loaded.value || saving.value || changingPassword.value) return
    const data = { ...form, nickname: form.nickname.trim(), email: form.email.trim(), phone: form.phone.trim() }
    if (data.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) return ElMessage.error('请输入有效邮箱')
    if (data.phone && !/^1[3-9]\d{9}$/.test(data.phone)) return ElMessage.error('请输入有效手机号')
    saving.value = true
    try {
        const res = await updateMyProfile(data)
        if (disposed) return
        applyProfile(res.data)
        ElMessage.success('资料已保存')
    } catch {
        // 保留输入内容供用户重试。
    } finally {
        if (!disposed) saving.value = false
    }
}
const clearPassword = () => Object.assign(password, { oldPassword: '', newPassword: '', confirmPassword: '' })
const savePassword = async () => {
    if (!loaded.value || changingPassword.value || saving.value) return
    if (!password.oldPassword) return ElMessage.error('请输入原密码')
    if (password.newPassword.length < 6 || password.newPassword.length > 50 || !password.newPassword.trim())
        return ElMessage.error('新密码长度应为6到50个字符')
    if (new TextEncoder().encode(password.newPassword).length > 72) return ElMessage.error('新密码过长，请减少中文字符')
    if (password.newPassword === password.oldPassword) return ElMessage.error('新密码不能与原密码相同')
    if (password.newPassword !== password.confirmPassword) return ElMessage.error('两次输入的新密码不一致')
    changingPassword.value = true
    try {
        await updateMyPassword({ oldPassword: password.oldPassword, newPassword: password.newPassword })
        clearPassword()
        clearSession()
        if (!disposed) {
            ElMessage.success('密码已修改，请重新登录')
            await router.replace('/auth/login')
        }
    } catch {
        // 修改失败时保留输入供用户更正。
    } finally {
        if (!disposed) changingPassword.value = false
    }
}
onMounted(loadProfile)
onBeforeUnmount(() => { disposed = true; clearPassword() })
</script>

<style scoped>
.profile-page { max-width: 780px; margin: 0 auto; padding: 24px 16px; min-height: 65vh; }
.profile-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 24px; margin-bottom: 24px; }
h1, h2 { color: #374151; }
h2 { margin-top: 0; font-size: 20px; }
p { color: #6b7280; font-size: 14px; margin-bottom: 24px; }
</style>
