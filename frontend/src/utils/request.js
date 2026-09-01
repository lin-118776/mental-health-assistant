
import axios from 'axios'
import { ElMessage } from 'element-plus'


const service = axios.create({
    baseURL: '/api', // 请求前缀基础URL
    timeout: 5000 // 超时时间
})

//请求拦截器
    service.interceptors.request.use(
    (config) => {
        //发起请求前的处理
        //例如：添加请求头、设置请求参数等
        // config.headers['Authorization'] = 'Bearer ' + getToken()
        const token = localStorage.getItem('token')
        if(token){
            config.headers['Authorization'] = 'Bearer ' + token // 后端JWT过滤器读取Authorization请求头
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

    // 响应拦截器
    service.interceptors.response.use(
    // 响应成功处理
    // 例如：处理响应数据、判断是否需要跳转等
    (response) => {
    const {data,config} = response
    // 兼容多种成功状态码 (200, 0)
    if(data.code == '200' || data.code == '0'){
        return data
    } else{
        // 处理登录过期情况
        if(data.code === '-1' || data.code === '401' || data.code === 'A0230'){
            if(!config.url?.includes('/login')){
                // 登录过期，提示用户重新登录
                ElMessage.error(data.msg||'登录过期，请重新登录')
                //清楚登录信息
                localStorage.removeItem('token')
                // 清除用户信息
                localStorage.removeItem('userInfo')
                window.location.href = '/auth/login'
               
            }
        } else{
            // 其他错误情况
            ElMessage.error(data.msg||'请求失败')
            return Promise.reject('网络请求失败...')
        }
    }
        
            return response
        },
        (error) => {
            // 响应失败处理
            // 例如：显示错误提示、跳转错误页等
            // 后端对未登录/token过期返回HTTP 401，此处统一提示并跳转登录页
            if (error.response && error.response.status === 401) {
                const respData = error.response.data
                if (!error.config?.url?.includes('/login')) {
                    ElMessage.error((respData && respData.msg) || '登录过期，请重新登录')
                    localStorage.removeItem('token')
                    localStorage.removeItem('userInfo')
                    window.location.href = '/auth/login'
                }
            } else {
                ElMessage.error('网络请求失败')
            }
            return Promise.reject(error)
        }
    )

export default service