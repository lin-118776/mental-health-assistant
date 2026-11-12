export const MAX_COVER_BYTES = 5 * 1024 * 1024

export const coverFileError = file => {
    if (!file || !Number.isFinite(file.size) || file.size <= 0) return '请选择非空图片'
    if (file.size > MAX_COVER_BYTES) return '封面图片不能超过5MB'
    const extension = /\.(png|jpe?g)$/i.exec(file.name || '')?.[1].toLowerCase()
    if (!extension) return '封面仅支持JPG、JPEG或PNG格式'
    const expected = extension === 'png' ? 'image/png' : 'image/jpeg'
    const type = (file.type || '').split(';')[0].trim().toLowerCase()
    if (type && type !== 'application/octet-stream' && type !== expected) return '图片类型与文件扩展名不一致'
    return ''
}
