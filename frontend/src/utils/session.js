import { ref } from 'vue'

// 角色仅由登录响应或 /user/me 填入，不读取本地缓存中的角色做授权判断。
export const sessionUser = ref(null)
export const setSessionUser = user => {
    sessionUser.value = user
    if (user) localStorage.setItem('userInfo', JSON.stringify(user))
}
export const clearSession = () => {
    sessionUser.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
}
