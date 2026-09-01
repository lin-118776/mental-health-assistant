<template>
    <div class="container">
        <div class="title">
            <div class="back-home">
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
            <el-button class="btn" type="primary" size="large" @click="submitForm(ruleFormRef)">登录</el-button>
            </el-form>
            <div class="footer">
                <p>还没有登陆？<router-link to="/auth/register">去注册</router-link></p>
            
            </div>

        </div>

    </div>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/admin'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'


const ruleFormRef = ref()
const router = useRouter()

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
    if (!formEl) return
    await formEl.validate((valid) => {
        if (valid) {
            login(formData.value).then(res => {
                // 兼容处理：尝试从根部或 data 字段中获取 token
                const token = res.token || (res.data && res.data.token)
                
                if (!token) {
                    return ElMessage.error('登录失败：未获取到 Token')
                }
                
                // 登录成功处理
                localStorage.setItem('token', token)
                // 保存用户信息，优先取 res.data，否则取 res 本身
                localStorage.setItem('userInfo', JSON.stringify((res.data && res.data.userInfo) || res.data || res))
                //根据用户角色选择跳转路径
                if(res.data && res.data.userInfo && res.data.userInfo.userType === 2){
                    router.push('/back/dashboard')
                }else{
                    router.push('/')
                }               
                
            }).catch(err => {
                console.error('登录逻辑执行异常:', err)
            })
        }
    })
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
