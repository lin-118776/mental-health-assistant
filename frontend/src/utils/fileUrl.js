// 后端上传路径默认走当前站点；完整HTTP(S)地址保持不变，避免重复拼接域名。
export const resolveFileUrl = value => {
    if (typeof value !== 'string') return ''
    const url = value.trim()
    if (!url || /[\\\u0000-\u001f\u007f]/.test(url) || url.startsWith('//')) return ''
    if (/^https?:\/\//i.test(url)) {
        try {
            return new URL(url).hostname ? url : ''
        } catch {
            return ''
        }
    }
    if (/^[a-z][a-z\d+.-]*:/i.test(url)) return ''
    return url.startsWith('/') ? url : `/${url}`
}
