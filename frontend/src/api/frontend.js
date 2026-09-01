import request from '@/utils/request'

export const register = (data) => {
    return request.post('/user/add',data)
}
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

// export const getKnowledgeList = (params) =>{
//     return request.get(' /knowledge/article/page',{ params })
// }

export const getKnowledgeList = (params) =>{
    return request.get('/knowledge/article/page',{ params })
}

export const getKnowledgeDetail = (articleId) =>{
    return request.get(`/knowledge/article/${articleId}`)
}


