<template>
    <div class="container">
        <div class="title">
            <div class="back-home" @click="router.push('/')">
                <el-icon><Back /></el-icon>
                <span>返回首页</span>

            </div>
            <div class="title-text">
                <h2>登陆您的帐户</h2>
                <p>请输入您的登录信息</p>
            </div>
        </div>
        <div class="form-container">
            <el-form
            ref="ruleFormRef"
            :model="formData"
            :rules="rules"
            label-position="top"
            >
            

            <el-form-item label="用户名和邮箱" prop="username">
                <el-input v-model="formData.username" size="large" placeholder="请输入用户名" />
            </el-form-item>
             <el-form-item label="密码" prop="password">
                <el-input v-model="formData.password" size="large" placeholder="请输入密码" type="password" show-password />
            </el-form-item>
            <el-button class="btn" type="primary" size="large" :loading="submitting" @click="submitForm(ruleFormRef)">登录</el-button>
            </el-form>
            <div class="footer">
                <p>还没有登陆？<router-link :to="{ path: '/auth/register', query: typeof route.query.redirect === 'string' ? { redirect: route.query.redirect } : {} }">去注册</router-link></p>
            
            </div>

        </div>

    </div>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/admin'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { setSessionUser } from '@/utils/session.js'
import { safeReturnPath } from '@/utils/authNavigation.js'


const ruleFormRef = ref()
const router = useRouter()
const route = useRoute()
const submitting = ref(false)

const formData = ref({
    username: '',
    password: ''
})
const rules = ref({
    username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
    ],
    password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
    ]
})

//登录
const submitForm = async (formEl) => {
    if (!formEl || submitting.value) return
    submitting.value = true
    try {
        if (!await formEl.validate()) return
        const res = await login(formData.value)
        const data = res.data || res
        if (!data.token || !data.userInfo) return ElMessage.error('登录失败：未获取到有效用户信息')
        localStorage.setItem('token', data.token)
        setSessionUser(data.userInfo)
        await router.replace(safeReturnPath(route.query.redirect, data.userInfo, value => router.resolve(value)))
    } catch {
        // 表单和请求工具负责显示校验错误；保留输入供重试。
    } finally {
        submitting.value = false
    }
}

// const submitForm = async (formEl)=>{
//     if(!formEl) return
//     await formEl.validate((valid,fields) =>{
//         if(valid){
//             login(formData.value).then(data=>{
//                 //判断token是否存在
//                 if(!data.token){
//                     return console.error('登录失败')
//                 }
//                 //登录成功 保存token
//                 localStorage.setItem('token', data.token)
//                 localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
//                 //根据用户角色选择跳转路径
//                 if(data.userInfo.userType === 2){
//                     router.push('/back/dashboard')
//                 }else{
//                     router.push('/')
//                 }               
//                 // 执行跳转
//                 router.push('/back')
//             })
//         }
//     })
// }

// const submitForm = async (formEl) => {
//   if (!formEl) return
//   await formEl.validate(async (valid) => {
//     if (!valid) return

//     const res = await login(formData.value)
//     const data = res.data || res // 自动兼容两种写法

//     if (!data.token) {
//       return ElMessage.error('登录失败：未获取到token')
//     }

//     localStorage.setItem('token', data.token)
//     localStorage.setItem('userInfo', JSON.stringify(data.userInfo))

//     if (data.userInfo.userType == 2) {
//       router.push('/back/dashboard')
//     } else {
//       router.push('/user')
//     }
//   })
// }

</script>

<style lang="scss" scoped>
.container {
    width: 385px;
    .title {
        
        .back-home{
        margin-bottom: 60px;
        }
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
            margin-top: 40px;
            text-align: center;
            p {
                font-size: 18px;
                color: #6b7280;
            }
        }
        
        
    }

}
</style>
