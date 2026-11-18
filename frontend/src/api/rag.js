import request from '@/utils/request'
export const listDocuments = page => request.get('/rag/admin/documents', {params:{page,size:10}})
export const createDocument = data => request.post('/rag/admin/documents', data)
export const retryDocument = id => request.post(`/rag/admin/documents/${id}/retry`)
export const disableDocument = id => request.delete(`/rag/admin/documents/${id}`)
export const searchSources = query => request.get('/rag/search', {params:{query}})
export const integrationStatus = () => request.get('/rag/admin/status')
export const evictCategories = () => request.post('/rag/admin/cache/evict')
