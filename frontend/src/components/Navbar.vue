<template>
    <div class="navbar">
        <div class="flex-box">
            <el-button @click="handleCollapse">
                <el-icon><Expand /></el-icon>
            </el-button>
            <p class="nav-title">{{route.meta.title}}</p>
        </div>
        <div class="flex-box">
            <el-dropdown @command="handleCommand">
                <div class="flex-box el-dropdown-link">
                    <el-avatar src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" size="small" />
                    <p class="user-name">admin</p>
                    <el-icon><ArrowDown /></el-icon>
                </div>
                <template #dropdown>
                    <el-dropdown-menu>
                        <el-dropdown-item command="layout">退出登录</el-dropdown-item>
                    </el-dropdown-menu> 
                </template>
            </el-dropdown>
        </div>
    </div>
</template>

<script setup>
import { useAdminStore } from '@/stores/admin'
import { useRouter,useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { logout } from '@/api/admin'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()

const handleCommand = (command) => {
    if (command === 'layout') {
        ElMessageBox.confirm('确定要退出登录吗？', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        }).then(() => {
            logout().then(() => {
                //清除缓存
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                //跳转登录页
                router.push('/auth/login')
            })
        })
    }
}

const handleCollapse = ()=>{
    adminStore.toggleCollapse()
}
</script>

<style lang="scss" scoped>

.navbar {
    height: 74px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 15px;
    background: white;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    border-bottom: 1px solid #353636;

    .flex-box {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .el-dropdown-link {
        cursor: pointer;
        outline: none;
        
        .user-name {
            margin: 0 8px;
            font-size: 14px;
            color: #333;
        }
    }

    .nav-title {
        font-size: 20px;
        margin-left: 20px;
        font-weight: bold;
        color: #1f2937;
    }
}
</style>
