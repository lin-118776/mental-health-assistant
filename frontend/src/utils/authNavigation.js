export const isAdmin = user => Number(user?.userType) === 2
export const userHome = user => isAdmin(user) ? '/back/dashboard' : '/'
export const isAuthPath = path => path === '/auth' || path.startsWith('/auth/')

export const safeReturnPath = (value, user, resolve) => {
    const fallback = userHome(user)
    if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020]/.test(value)) return fallback
    try {
        const target = resolve(value)
        if (!target.matched.length || isAuthPath(target.path)) return fallback
        if (target.meta.requiresAdmin && !isAdmin(user)) return fallback
        return target.fullPath
    } catch {
        return fallback
    }
}
