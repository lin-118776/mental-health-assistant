import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '../components/BackendLayout.vue'
import AuthLayout from '../components/AuthLayout.vue'
import FrontendLayout from '../components/FrontendLayout.vue'
import { createAuthGuard } from '../utils/authGuard.js'
import { setSessionUser, clearSession } from '../utils/session.js'
import { getMyProfile } from '../api/frontend.js'
import { ElMessage } from 'element-plus'


const BackendRouter=[
    {
        path:'/back',
        redirect:'/back/dashboard',
        component:BackendLayout,
        meta:{ requiresAuth:true, requiresAdmin:true },
        children:[
            {
                path:'rag-documents',
                component:()=>import('@/views/ragDocuments.vue'),
                meta:{ title:'AI知识资料', icon:'Document' }
            },
            {
                path:'users',
                component:()=>import('@/views/users.vue'),
                meta:{ title:'用户管理', icon:'UserFilled' }
            },
            {
                path:'dashboard',
                component:()=>import('@/views/dashboard.vue'),
                meta:{
                    title:'数据分析',
                    icon:'PieChart'
                }
            },
            {
                path:'knowledge',
                component:()=>import('@/views/knowledge.vue'),
                meta:{
                    title:'知识文章',
                    icon:'DocumentCopy'
                }
            },
            {
                path:'consultations',
                component:()=>import('@/views/consultations.vue'),
                meta:{
                    title:'咨询记录',
                    icon:'Message'
                }
            },
            {
                path:'emotional',
                component:()=>import('@/views/emotional.vue'),
                meta:{
                    title:'情感日志',
                    icon:'User'
                }
            }
            
        ]
    },
    {
        path:'/auth',
        component:AuthLayout,
        children:[
            {
                path:'login',
                component:()=>import('@/views/login.vue'),
                meta:{
                    title:'登录'
                   
                }
            },
            {
                path:'register',
                //懒加载
                component:()=>import('@/views/register.vue'),
                meta:{
                    title:'注册'
                    }
                
            }
        ]
    }
]

//前台路由
const frontendRouter=[
    {
        path:'/',
        component:FrontendLayout,
        children:[
            {
                path:'',
                component:()=>import('@/views/home.vue'),
            },
            {
                path:'consultation',
                component:()=>import('@/views/consultation.vue'),
                meta:{ requiresAuth:true },
               
            },
            {
                path:'emotion-diary',
                component:()=>import('@/views/emotionalDiary.vue'),        
                meta:{ requiresAuth:true },
            },
            {
                path:'knowledge',
                component:()=>import('@/views/frontendKnowledge.vue'),
                meta:{ requiresAuth:true },
               
            },
            {
                path:'profile',
                component:()=>import('@/views/profile.vue'),
                meta:{ requiresAuth:true }
            },
            {
                path:'knowledge/article/:id',
                component:()=>import('@/views/articleDetail.vue'),
                meta:{ requiresAuth:true },
                props:true,
               
            }

        ]
    },
    
]


const router =  createRouter({
    history:createWebHistory(),
    routes:[...BackendRouter,...frontendRouter]
})

// 路由前置守卫
router.beforeEach(createAuthGuard({
    getToken: () => localStorage.getItem('token'),
    fetchCurrentUser: async () => (await getMyProfile({ skipAuthRedirect: true })).data,
    setUser: setSessionUser,
    clearSession,
    resolve: value => router.resolve(value),
    notify: message => ElMessage.warning(message)
}))

export default router
