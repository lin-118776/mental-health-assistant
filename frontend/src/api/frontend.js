import request from '@/utils/request'

export const register = (data) => {
    return request.post('/user/add',data)
}

export const getMyProfile = (config = {}) => request.get('/user/me', config)
export const updateMyProfile = (data) => request.put('/user/me', data)
export const updateMyPassword = (data) => request.put('/user/password', data)
export const startSession = (data) => {
    return request.post('/psychological-chat/session/start',data)
}

export const getSessionsList = (params) => {
    return request.get('/psychological-chat/sessions',{params})
}

export const deleteSession = (sessionId) =>{
    return request.delete(`/psychological-chat/sessions/${sessionId}`)
}

export const getSessionDetail = (sessionId)=>{
    return request.get(`/psychological-chat/sessions/${sessionId}/messages`)
}


export const getSessionEmotion = (sessionId) =>{
    return request.get(`/psychological-chat/session/${sessionId}/emotion`)
}

export const addEmotionDiary = (data) =>{
    return request.post(`/emotion-diary`,data)
}

export const getMyEmotionDiaries = (params) => request.get('/emotion-diary/mine', { params })
export const getMyEmotionTrend = (days) => request.get('/emotion-diary/mine/trend', { params: { days } })
export const getMyEmotionDiary = (id) => request.get(`/emotion-diary/mine/${id}`)
export const updateMyEmotionDiary = (id, data) => request.put(`/emotion-diary/mine/${id}`, data)
export const deleteMyEmotionDiary = (id) => request.delete(`/emotion-diary/mine/${id}`)

// export const getKnowledgeList = (params) =>{
//     return request.get(' /knowledge/article/page',{ params })
// }

export const getKnowledgeList = (params) =>{
    return request.get('/knowledge/article/page',{ params })
}

export const getKnowledgeDetail = (articleId) =>{
    return request.get(`/knowledge/article/${articleId}`)
}


