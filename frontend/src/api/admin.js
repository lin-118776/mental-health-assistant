import request from '@/utils/request'

export const getAdminUsers = (params) => request.get('/user/admin/page', { params })
export const updateUserStatus = (id, status) => request.put(`/user/admin/${id}/status`, { status })

export function login(data){
    return request.post('/user/login',data)
}

export function categoryTree(){
    return request.get('/knowledge/category/tree')
}

export function articlePage(params){
    return request.get('/knowledge/admin/article/page',{params})
}
export function uploadFile(file,businessInfo){
    const formData = new FormData()
    formData.append('file', file)
    formData.append('businessType', 'ARTICLE')
    formData.append('businessId', businessInfo.id)
    formData.append('businessField', 'cover')
    return request.post('/file/upload',formData, {
        Headers:{
            'content-type': 'multipart/form-data'
        }
    }
    ) 
}            

export function createArticle(data){
    return request.post('/knowledge/article',data)
}

export function getArticleDetail(id){
    return request.get(`/knowledge/admin/article/${id}`)
}
export function updateArticle(id,data){
    return request.put(`/knowledge/article/${id}`,data)
}
export function changeArticleStatus(id,data){
    return request.put(`/knowledge/article/${id}/status`,data)
}

export function deleteArticle(id){
    return request.delete(`/knowledge/article/${id}`)
}

export function getConsultations(params){
    return request.get('/psychological-chat/admin/sessions',{params})
}

export function getConsultationDetail(sessionId){
    return request.get(`/psychological-chat/admin/sessions/${sessionId}`)
}

export function getAnalyticsOverview(){
    return request.get('/data-analytics/overview')
}

export function getEmotionalDiary(params){
    return request.get('/emotion-diary/admin/page',{params})
}

export function getEmotionalDiaryDetail(id){
    return request.get(`/emotion-diary/admin/${id}`)
}
export function deleteEmotional(id){
    return request.delete(`/emotion-diary/admin/${id}`)
}

export function logout(){
    return request.post('/user/logout')
}
