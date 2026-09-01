import { createRouter, createWebHistory } from 'vue-router'
import BackendLayout from '../components/BackendLayout.vue'
import AuthLayout from '../components/AuthLayout.vue'
import FrontendLayout from '../components/FrontendLayout.vue'


const BackendRouter=[
    {
        path:'/back',
        redirect:'/back/dashboard',
        component:BackendLayout,
        children:[
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
               
            },
            {
                path:'emotion-diary',
                component:()=>import('@/views/emotionalDiary.vue'),        
            },
            {
                path:'knowledge',
                component:()=>import('@/views/frontendKnowledge.vue'),
               
            },
            {
                path:'knowledge/article/:id',
                component:()=>import('@/views/articleDetail.vue'),
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
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    const isAuthPath = to.path.startsWith('/auth')
    const isBackPath = to.path.startsWith('/back')

    if (token) {
        // 已登录，尝试访问登录/注册页面时跳转到后台首页
        if (isAuthPath) {
            next('/back/dashboard')
        } else {
            // 已登录，访问其他页面（如 / 或 /back 等）
            // 确保 next() 只在目标路径不是当前路径时调用跳转，或者直接调用 next() 放行
            if (to.matched.length === 0) {
                next('/back/dashboard')
            } else {
                next()
            }
        }
    } else {
        // 未登录
        if (isBackPath) {
            // 访问后台页面，重定向到登录
            next('/auth/login')
        } else {
            // 访问登录/注册或其它非后台页面，放行
            next()
        }
    }
})

export default router