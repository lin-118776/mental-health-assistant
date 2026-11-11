import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession } from './session.js'

const service = axios.create({ baseURL: '/api', timeout: 5000 })
const authCodes = new Set(['401', 'A0230', '-1', '6004'])
const isAuthApi = config => ['/user/login', '/user/add', '/user/register'].includes(config?.url)
let redirecting = false

const expireSession = (config, message) => {
    if (isAuthApi(config)) {
        ElMessage.error(message)
        return
    }
    // 旧请求的失败不能清除后来登录的账号。
    const current = localStorage.getItem('token')
    const sent = config?.headers?.Authorization
    if (current && sent && sent !== `Bearer ${current}`) return
    clearSession()
    ElMessage.error(message)
    if (config?.skipAuthRedirect || redirecting || window.location.pathname.startsWith('/auth/')) return
    redirecting = true
    const target = window.location.pathname + window.location.search + window.location.hash
    window.location.href = `/auth/login?redirect=${encodeURIComponent(target)}`
}

service.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    config.headers = config.headers || {}
    if (isAuthApi(config)) delete config.headers.Authorization
    else if (token) config.headers.Authorization = `Bearer ${token}`
    return config
})

service.interceptors.response.use(response => {
    const { data, config } = response
    if (String(data.code) === '200' || String(data.code) === '0') return data
    const error = new Error(data.msg || '请求失败')
    error.code = String(data.code)
    if (authCodes.has(error.code)) expireSession(config, error.message)
    else ElMessage.error(error.message)
    return Promise.reject(error)
}, error => {
    const code = String(error.response?.data?.code || '')
    const message = error.response?.data?.msg || (error.response?.status === 403 ? '没有访问权限' : '网络请求失败')
    if (error.response?.status === 401 || authCodes.has(code)) expireSession(error.config, message)
    else ElMessage.error(message)
    return Promise.reject(error)
})

export default service
