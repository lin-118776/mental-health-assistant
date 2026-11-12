

<template>
    <div class="container">
        <div class="title">
            <div class="title-text">
            <h2>注册</h2>
            <p>创建您的账号，开始使用我们的服务</p>
            </div>
        </div>
        <div class="form-container">
            <el-form label-position="top" :model="formData" :rules="rules" ref="submitFormRef" :disabled="submitting" @submit.prevent="submitForm(submitFormRef)">
                <el-form-item label="用户名" prop="username">
                    <el-input v-model="formData.username" placeholder="3–50位字母、数字或下划线" maxlength="50" size="large" autocomplete="username" />
                </el-form-item>
                <el-form-item label="邮箱（可选）" prop="email">
                    <el-input v-model="formData.email" placeholder="请输入邮箱（可选）" maxlength="100" size="large" type="email" />
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                    <el-input v-model="formData.nickname" placeholder="请输入昵称（可选）" maxlength="50" size="large" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                    <el-input v-model="formData.phone" placeholder="请输入11位手机号" maxlength="11" size="large" type="tel" />
                </el-form-item>
                <el-form-item label="密码" prop="password">
                    <el-input v-model="formData.password" placeholder="6–50个字符" maxlength="50" size="large" type="password" show-password autocomplete="new-password" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="formData.confirmPassword" placeholder="请再次输入密码" maxlength="50" size="large" type="password" show-password autocomplete="new-password" />
                </el-form-item>
                 <el-button class="btn" type="primary" size="large" native-type="submit" :loading="submitting">注册</el-button>
            </el-form>
            <p>已有账号？<router-link :to="loginLocation">去登录</router-link></p>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { register } from '@/api/frontend'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const submitting = ref(false)
const loginLocation = computed(() => ({ path: '/auth/login',
    query: typeof route.query.redirect === 'string' ? { redirect: route.query.redirect } : {} }))

const formData = reactive({
   
    "username": "",
    "email": "",
    "nickname": "",
    "phone": "",
    "password": "",
    "confirmPassword": ""

})

const rules = reactive({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' },
        { min: 3, max: 50, message: '用户名长度为3到50位', trigger: 'blur' },
        { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
    ],
    email: [
        { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
        { max: 100, message: '邮箱不能超过100个字符', trigger: 'blur' }
    ],
    nickname: [{ max: 50, message: '昵称不能超过50个字符', trigger: 'blur' }],
    phone: [
        { required: true, message: '请输入手机号', trigger: 'blur' },
        { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
    ],
    password: [
        { required: true, whitespace: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, max: 50, message: '密码长度为6到50个字符', trigger: 'blur' },
        { validator: (_, value, callback) => callback(new TextEncoder().encode(value || '').length > 72
            ? new Error('密码过长，请减少中文字符') : undefined), trigger: 'blur' }
    ],
    confirmPassword: [
        { required: true, message: '请确认密码', trigger: 'blur' },
        { validator: (_, value, callback) => callback(value === formData.password
            ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }
    ]
})

//表单提交
const submitFormRef = ref(null)
const submitForm = async (formEl) => {
    if (!formEl || submitting.value) return
    submitting.value = true
    formData.username = formData.username.trim()
    formData.email = formData.email.trim()
    formData.phone = formData.phone.trim()
    formData.nickname = formData.nickname.trim()
    try {
        if (!await formEl.validate()) return
        await register({ username: formData.username, email: formData.email || null,
            nickname: formData.nickname || null, phone: formData.phone, password: formData.password })
        formData.password = ''
        formData.confirmPassword = ''
        ElMessage.success('注册成功，请登录')
        await router.replace(loginLocation.value)
    } catch {
        // 校验及请求工具显示错误；失败时保留表单内容供更正和重试。
    } finally {
        submitting.value = false
    }
}

</script>

<style scoped lang="scss" >
.container {
    width: 384px;
    .flex-box {
        display: flex;
        align-items: center;
    }
    .title {
        .title-text {
            text-align: center;
            h2 {
                font-size: 36px;
                margin-bottom: 10px;
            }
            p {
                font-size: 18px;
                color: #6b7280;
            }
        }
    }
    .form-container {
        margin-top: 30px;
        .btn {
            margin-top: 40px;
            width: 100%;
        }
        .footer {
            padding: 30px;
            text-align: center;
        }
    }
}
</style>
