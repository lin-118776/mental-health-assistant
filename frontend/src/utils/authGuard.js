import { isAdmin, isAuthPath, safeReturnPath, userHome } from './authNavigation.js'

export const createAuthGuard = ({ getToken, fetchCurrentUser, setUser, clearSession, resolve, notify }) => async (to, from) => {
    const needsLogin = to.meta.requiresAuth || to.meta.requiresAdmin
    const login = () => ({ path: '/auth/login', query: { redirect: to.fullPath }, replace: true })
    const token = getToken()
    if (!token) {
        setUser(null)
        if (needsLogin) return login()
        return to.matched.length ? true : '/'
    }
    let user
    try {
        user = await fetchCurrentUser()
    } catch (error) {
        // 凭证失效后允许进入登录页；临时网络故障不清除有效凭证。
        if (getToken() !== token && getToken()) return false
        const code = String(error?.response?.data?.code || error?.code || '')
        if (error?.response?.status === 401 || ['401', 'A0230', '-1', '6004'].includes(code)) clearSession()
        if (!getToken()) {
            setUser(null)
            return needsLogin ? login() : to.matched.length ? true : '/'
        }
        if (needsLogin) return from?.matched?.length ? false : '/'
        setUser(null)
        return to.matched.length ? true : '/'
    }
    // 忽略验证期间发生退出或切换账号的旧结果。
    if (getToken() !== token) return false
    if (!user || ![1, 2].includes(Number(user.userType)) || Number(user.status) !== 1) {
        clearSession()
        return needsLogin ? login() : to.matched.length ? true : '/'
    }
    setUser(user)
    if (to.meta.requiresAdmin && !isAdmin(user)) {
        notify('此页面仅管理员可访问')
        return '/'
    }
    if (isAuthPath(to.path)) return safeReturnPath(to.query.redirect, user, resolve)
    return to.matched.length ? true : userHome(user)
}
