<template>
    <div class="consultation-container">
        <div class="sidebar">
            <!-- ai助手信息 -->
            <div class="ai-assistant-info">
                <div class="breathing-circle">
                    <el-image :src="iconURL" style="width: 25px; height: 25px;" alt="AI助手" fit="contain" />
                </div>
                <h3 class="assistant-name">木子AI助手</h3>
                <div class="online-status">
                    <div class="status-dot"></div>
                    在线服务中
                </div>
            </div>
               <!-- 情绪花园 -->
             <div class="emotion-garden">
                <div class="garden-header">
                    <div class="garden-title"> 情绪花园 </div>
                </div>
                <div class="emotion-info">
                    <div class="emotion-name">{{ currentEmotion.primaryEmotion }}</div>
                    <div class="emotion-score" title="情绪积极程度：0–100，越高越积极">{{ currentEmotion.emotionScore ?? '--' }}</div>
                </div>
                <div class="warm-tips">
                    <div class="emotion-status-text">
                        <span class="status-label">今天感觉</span>
                        <span class="status-emotion">{{ !currentEmotion.available ? '待了解' : currentEmotion.isNegative ? '需要关注' : '较平稳' }}</span>
                    </div>
                    <div class="emotion-intensity">
                        <span class="intensity-dots">
                            <span v-for="dot in 3" :key="dot" class="dot" :class="{'active': getIntensityClass(currentEmotion.emotionScore) >= dot}"></span>
                        </span>
                        <span class="intensity-text">
                            {{ getRiskText(currentEmotion.riskLevel) }}
                        </span>
                    </div>
                    <div>AI 分析仅供自我了解参考</div>
                    <!-- 温暖建议卡片 -->
                    <div class="warm-suggestion" v-if="currentEmotion.suggestion">
                        <div class="suggestion-icon">💝</div>
                        <div class="suggestion-content">
                            <div class="suggestion-title">给你的小建议</div>
                            <div class="suggestion-text">{{ currentEmotion.suggestion }}</div>
                        </div>
                    </div>
                    <!-- 治愈行动 -->
                    <div class="healing-actions" v-if="currentEmotion.improvementSuggestions.length > 0">
                        <div class="actions-title">治愈小行动</div>
                        <div class="actions-list">
                            <div v-for="action in currentEmotion.improvementSuggestions" :key="action" class="action-item">
                                <div class="action-icon">✨</div>
                                <div class="action-text">{{ action }}</div>
                            </div>
                        </div>
                    </div>
                    <!-- 风险提示 -->
                     <div class="risk-notice" v-if="currentEmotion.isNegative && currentEmotion.riskLevel > 1">
                        <div class="notice-icon">🤗</div>
                        <div class="notice-content">
                            <div class="notice-title">温馨提示</div>
                            <div class="notice-text">{{ currentEmotion.riskDescription }}</div>
                        </div>
                     </div>
                </div>
             </div>

            <!-- 会话列表 -->
             <div class="session-history">
                <h4 class="history-title">会话列表</h4>
                <div class="session-list">
                    <p v-if="sessionListLoading">正在加载会话列表…</p>
                    <p v-else-if="sessionListError">会话列表加载失败 <el-button text type="primary" @click="getSessionsPage()">重试</el-button></p>
                    <p v-else-if="!sessionsList.length">暂无历史会话，发送消息即可开始。</p>
                    <div v-for="session in sessionsList" :key="session.id" @click="selectSessionClick(session)" class="session-item" :class="{active: currentSession?.sessionId === session.id}">
                        <div class="session-info">
                            <div class="session-title">
                                <span>{{ session.sessionTitle }}</span>
                                <div class="session-meta">
                                    <span class="session-time">{{ session.createdAt}}</span>
                                </div>
                                <div class="session-preview">
                                    {{ session.lastMessageContent || '暂无消息' }}
                                </div>
                                <div class="session-status">
                                    <span title="该会话的消息总数">
                                        <el-icon>
                                            <ChatRound />
                                        </el-icon>
                                        {{ session.messageCount ?? 0}} 条
                                    </span>
                                    <span title="首末消息的时间跨度，不代表实际在线时长">
                                        <el-icon>
                                            <Clock />
                                        </el-icon>
                                        {{ session.durationMinutes ?? 0 }} 分钟
                                    </span>
                                </div>
                            </div>
                            <div class="session-actions">
                                <el-button text type="danger" size="small" @click.stop="handleDeleteSession(session.id)">
                                    <el-icon>
                                        <DeleteFilled />
                                    </el-icon>
                                </el-button>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="session-pagination" v-if="!sessionListError">
                    <div>第 {{ sessionPage }} 页，共 {{ sessionTotal }} 条</div>
                    <el-button size="small" :disabled="sessionListLoading || sessionPage <= 1" @click="getSessionsPage(sessionPage - 1)">上一页</el-button>
                    <el-button size="small" :disabled="sessionListLoading || sessionPage * sessionPageSize >= sessionTotal" @click="getSessionsPage(sessionPage + 1)">下一页</el-button>
                </div>

             </div>
        </div>
        <div class="chat-main">
            <div class="chat-header">
                <div class="header-left">
                    <div class="chat-avatar">
                        <el-image :src="iconURL1" style="width: 30px; height: 30px;" />
                    </div>
                    <div class="chat-info">
                        <h2>木子AI助手</h2>
                        <p>您贴心的ai心理助手</p>
                    </div>
                </div>
                <el-button circle @click="creatNewfrontendSession" title="新建会话">
                    <el-icon>
                        <Plus />
                    </el-icon>
                </el-button>
            </div>
       
        <!-- 聊天消息区域 -->
            <div class="chat-messages">
                <p v-if="historyLoading">正在加载会话记录…</p>
                <p v-if="historyError">会话记录加载失败，请重新选择该会话后再发送消息。</p>
                <div class="message-item ai-message" v-if="messages.length===0 && !historyLoading && !historyError">
                    <div class="message-avatar">
                        <el-image :src="iconURL" style="width: 18px; height: 18px;"  />
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <p>您好！我是小暖，您的AI心理助手，为您提供温暖的心理支持。请告诉我，您今天感觉怎么样？有什么需要分享的事情吗？</p>
                        </div>
                        <div class="message-time">10:00</div>
                    </div>
                </div>
                <!-- 对话消息列表 -->
                 <div v-for="msg in messages" :key="msg.id" class="message-item" :class="msg.senderType===1?'user-message':'ai-message'">
                    <div class="message-avatar">
                        <el-image v-if="msg.senderType===1" style="width: 18px;height: 18px;" :src="iconURL2" />
                        <el-image v-if="msg.senderType===2" style="width: 18px;height: 18px;" :src="iconURL" />
                    </div>
                    <div class="message-content">
                        <div class="message-bubble">
                            <!-- AI 回复中 -->
                            <div v-if="msg.senderType===2&&isAiTyping&&!msg.content" class="typing-indicator">
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                                <div class="typing-dot"></div>
                            </div>
                           
                             <div v-else-if="msg.isError" class="error-message">
                                <p>{{ msg.lastMessageContent }}</p>
                             </div>
                             <!-- AI回复内容 -->
                             <MarkdownRenderer v-else-if="msg.senderType===2&&!msg.isError" :content="msg.content" :is-ai-message="true"></MarkdownRenderer>
                             <p v-else-if="msg.content" v-html="formatMessageContent(msg.content)"></p> 
                        </div>
                        <div class="message-time">{{ msg.senderType===2&& isAiTyping?'正在回复中...':msg.createdAt }}</div>
                    </div>
                 </div>
            </div>
            <!-- 消息输入区域 -->
             <div class="chat-input">
                <div class="input-container">
                    <el-input 
                    v-model="userMessage"
                    placeholder="请输入你想分享的内容.."
                    type="textarea"
                    :rows="3"
                    :disabled="isAiTyping || historyLoading || historyError"
                    @keydown.enter="handkeKeyDown"
                    clearable
                    class="message-input"
                    />
                    <div class="input-footer">
                        <span>按Enter发送，Shift+Enter换行</span>
                        <span>{{ userMessage.length }}/500</span>
                    </div>
                </div>
                <el-button :disabled="!userMessage.trim()||userMessage.length>500||isAiTyping||historyLoading||historyError" class="send-btn" @click="sendMessage">
                    <el-icon><promotion /></el-icon>
                </el-button>
                
             </div>
        </div>
    </div>
            
</template>


<script setup>
 

import { startSession ,getSessionsList,deleteSession,getSessionDetail,getSessionEmotion} from '@/api/frontend'
import { Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { ref ,onMounted,onBeforeUnmount} from 'vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'



const iconURL = new URL('@/assets/images/robot-fill.png', import.meta.url).href
const iconURL1 = new URL('@/assets/images/like.png', import.meta.url).href
const iconURL2 = new URL('@/assets/images/users.png', import.meta.url).href







//新建会话
const creatNewfrontendSession = () =>{
    resetConversation()
    //创建一个新的会话对象
    const newSession ={
        sessionId: `temp_${Date.now()}`,
        status:'TEMP',
        sessionTitle:'新会话'
    }
    //将新会话对象赋值给currentSession
    currentSession.value = newSession
    emotionRequestId++
    currentEmotion.value = emptyEmotion()
}



//定义当前会话对象
const currentSession = ref(null)
//定义会话列表
const sessionsList = ref([])
const sessionPage = ref(1)
const sessionTotal = ref(0)
const sessionPageSize = 10
const sessionListLoading = ref(false)
const sessionListError = ref(false)

//定义对话消息
const messages = ref([])
//定义用户消息
const userMessage = ref('')
//定义是否正在输入
const isAiTyping = ref(false)
const historyLoading = ref(false)
const historyError = ref(false)
let conversationId = 0
let activeStream = null
let listRequestId = 0
let disposed = false

const resetConversation = () => {
    conversationId++
    activeStream?.abort()
    activeStream = null
    isAiTyping.value = false
    historyLoading.value = false
    historyError.value = false
    messages.value = []
    userMessage.value = ''
}

//情绪花园
const emptyEmotion = (name = '待分析', suggestion = '开始对话后，将根据你的发言分析情绪。') => ({
    available: false,
    primaryEmotion:name,
    emotionScore:null,
    isNegative: false,
    riskLevel:null,
    suggestion,
    riskDescription:'',
    improvementSuggestions:[]
})
const currentEmotion = ref(emptyEmotion())
let emotionRequestId = 0

const getIntensityClass =(score)=>{
    if(score>= 61){
        return 3
    }
    if(score>=31){
        return 2
    }
    return 0
}

const getRiskText =(level)=>{
    switch(level){
            case 0:
            return '未见明显风险'
            case 1:
            return '关注'
            case 2:
            return '预警'
            case 3:
            return '危机'
            default:
            return '暂无分析'
    }
}

const loadSessionEmotion = (sessionId) =>{
    const requestId = ++emotionRequestId
    currentEmotion.value = emptyEmotion('分析中', '正在分析本次会话，请稍候。')
    getSessionEmotion(sessionId).then(res => {
        if (requestId !== emotionRequestId || currentSession.value?.sessionId !== sessionId) return
        currentEmotion.value = res.data
    }).catch(()=>{
        if (requestId !== emotionRequestId || currentSession.value?.sessionId !== sessionId) return
        currentEmotion.value = emptyEmotion('暂不可用', '情绪分析暂不可用，请稍后重新打开会话重试。')
    })
}

// 输入法确认和 Shift+Enter 保留原生行为。
const handkeKeyDown = e => {
    if (e.isComposing || e.keyCode === 229 || e.shiftKey) return
    e.preventDefault()
    return sendMessage()
}

const sendMessage = async () => {
    if (isAiTyping.value || historyLoading.value || historyError.value || disposed) return
    const message = userMessage.value.trim()
    if (!message || userMessage.value.length > 500) return
    if (!currentSession.value) creatNewfrontendSession()
    const requestId = conversationId
    isAiTyping.value = true
    userMessage.value = ''
    try {
        if (currentSession.value.status === 'TEMP') {
            const title = currentSession.value.sessionTitle === '新会话'
                ? '木子AI助手-' + new Date().toLocaleString() : currentSession.value.sessionTitle
            const res = await startSession({title})
            if (requestId !== conversationId || disposed) return
            currentSession.value = {sessionId:res.data, status:'ACTIVE', sessionTitle:title}
            getSessionsPage(1)
        }
        messages.value.push({id:crypto.randomUUID(), senderType:1, content:message, createdAt:new Date().toLocaleString()})
        await startAIResponse(currentSession.value.sessionId, message, requestId)
    } catch {
        if (requestId !== conversationId || disposed) return
        // 会话创建失败时保留输入，避免丢失尚未发送的内容。
        if (currentSession.value?.status === 'TEMP') userMessage.value = message
    } finally {
        if (requestId === conversationId && !disposed) isAiTyping.value = false
    }
}

const startAIResponse = async (sessionId, content, requestId) => {
    const ctrl = new AbortController()
    activeStream = ctrl
    const aiId = crypto.randomUUID()
    messages.value.push({id:aiId, senderType:2, content:'', createdAt:new Date().toLocaleString()})
    // 每次通过响应式数组定位本次消息，避免写到另一个会话或失去 Vue 更新。
    const target = () => messages.value.find(message => message.id === aiId)
    const isCurrent = () => !disposed && requestId === conversationId && activeStream === ctrl
    let completed = false
    const finish = () => {
        if (!isCurrent() || completed) return
        completed = true
        loadSessionEmotion(sessionId)
        getSessionsPage()
    }
    try {
        await fetchEventSource('/api/psychological-chat/stream', {
            method:'POST',
            headers:{'Content-Type':'application/json', 'Authorization':'Bearer ' + localStorage.getItem('token'), 'Accept':'text/event-stream'},
            body:JSON.stringify({sessionId, content}), signal:ctrl.signal,
            // 不因切换浏览器标签而自动重发这个有写入副作用的 POST。
            openWhenHidden:true,
            onopen: async response => {
                if (!response.ok || !response.headers.get('content-type')?.toLowerCase().startsWith('text/event-stream')) {
                    throw new Error('AI服务暂不可用，请稍后重试')
                }
            },
            onmessage: event => {
                if (!isCurrent() || completed) return
                if (event.event === 'done') { finish(); ctrl.abort(); return }
                if (!event.data.trim()) return
                const payload = JSON.parse(event.data)
                if (payload.sessionId != null && String(payload.sessionId) !== String(sessionId)) throw new Error('会话响应不匹配')
                if (typeof payload.content === 'string' && target()) target().content += payload.content
            },
            onclose: () => {
                if (isCurrent() && !completed) throw new Error('AI回复连接中断，请稍后重试')
            },
            // 禁止自动重试，防止相同用户消息重复入库。
            onerror: error => { throw error || new Error('AI回复失败') }
        })
    } catch {
        if (isCurrent()) {
            const message = target()
            if (message) message.content = message.content
                ? message.content + '\n\n（回复中断，请稍后继续对话）' : 'AI回复失败，请稍后重试'
            ElMessage.error('AI回复未完成，请稍后重试')
        }
    } finally {
        if (isCurrent()) { activeStream = null; isAiTyping.value = false }
    }
}

const getSessionsPage = async (page = sessionPage.value) => {
    if (disposed || !Number.isSafeInteger(page) || page < 1) return
    const requestId = ++listRequestId
    sessionPage.value = page
    sessionListLoading.value = true
    sessionListError.value = false
    sessionsList.value = []
    try {
        const res = await getSessionsList({pageNum:page, pageSize:sessionPageSize})
        if (disposed || requestId !== listRequestId) return
        sessionTotal.value = res.data.total
        const lastPage = Math.max(1, Math.ceil(res.data.total / sessionPageSize))
        // 删除或其他设备的变更可能使当前页越界。
        if (page > lastPage) return await getSessionsPage(lastPage)
        sessionsList.value = res.data.records
    } catch {
        if (!disposed && requestId === listRequestId) sessionListError.value = true
    } finally {
        if (!disposed && requestId === listRequestId) sessionListLoading.value = false
    }
}

const selectSessionClick = async session => {
    resetConversation()
    const requestId = conversationId
    currentSession.value = {sessionId:session.id, status:'ACTIVE', sessionTitle:session.sessionTitle}
    historyLoading.value = true
    loadSessionEmotion(session.id)
    try {
        const res = await getSessionDetail(session.id)
        if (requestId === conversationId && !disposed) messages.value = res.data
    } catch {
        if (requestId === conversationId && !disposed) historyError.value = true
    } finally {
        if (requestId === conversationId && !disposed) historyLoading.value = false
    }
}

const handleDeleteSession = async id => {
    try {
        await deleteSession(id)
        if (disposed) return
        if (currentSession.value?.sessionId === id) creatNewfrontendSession()
        ElMessage.success('删除成功')
        await getSessionsPage()
    } catch {
        // 删除失败时保留当前会话和消息。
    }
}

onBeforeUnmount(() => {
    disposed = true
    resetConversation()
    emotionRequestId++
    listRequestId++
})

//格式化消息内容
const formatMessageContent = (content) =>{
    return content.replace(/\n/g, '<br>')
}
onMounted(() => {
    //获取会话列表
    getSessionsPage()
    //在组件挂载完成后，新建一个会话
    creatNewfrontendSession()
})
</script>

<style scoped lang="scss">
.consultation-container {
    .session-pagination {
        margin-top: 12px;
        line-height: 2;
        color: #6b7280;
        font-size: 12px;
    }
    margin: 0 auto;
    width: 1200px;
    display: flex;
    gap: 20px;
    padding: 20px;
    .sidebar {
        width: 320px;
        .ai-assistant-info {
            margin-bottom: 20px;
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 252, 248, 0.95) 100%);
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 8px 32px rgba(251, 146, 60, 0.06), 0 2px 8px rgba(0, 0, 0, 0.04);
            border: 1px solid rgba(251, 146, 60, 0.08);
            backdrop-filter: blur(10px);
            transition: all 0.3s ease;
            .breathing-circle {
                width: 60px;
                height: 60px;
                background: linear-gradient(135deg, #fb923c 0%, #f59e0b 100%);
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                margin: 0 auto 12px;
                animation: breathing 4s ease-in-out infinite;
                box-shadow: 0 6px 24px rgba(251, 146, 60, 0.25);
                position: relative;
            }
            .assistant-name {
                font-size: 16px;
                font-weight: 700;
                background: linear-gradient(135deg, #fb923c, #f59e0b);
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
                text-align: center;
                background-clip: text;
                margin: 0 0 12px;
            }
            .online-status {
                display: flex;
                align-items: center;
                justify-content: center;
                color: #059669;
                font-size: 12px;
                font-weight: 600;
                .status-dot {
                    width: 8px;
                    height: 8px;
                    background: #059669;
                    border-radius: 50%;
                    margin-right: 8px;
                    animation: pulse 2s infinite;
                    box-shadow: 0 0 8px rgba(5, 150, 105, 0.4);
                }
            }
        }
        .session-history {
            background: white;
            border-radius: 16px;
            padding: 16px;
            box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
            margin-bottom: 20px;
            min-height: 250px;
            display: flex;
            flex-direction: column;
            .section-title {
                font-size: 16px;
                font-weight: 600;
                color: #333;
                margin: 0 0 16px;
                display: flex;
                align-items: center;
                justify-content: space-between;
                
            }
            .session-list {
                overflow-y: auto;
                max-height: 200px;
                scrollbar-width: thin;
                scrollbar-color: rgba(64, 150, 255, 0.3) transparent;
                .session-item {
                    position: relative;
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    padding: 12px;
                    margin-bottom: 8px;
                    border-radius: 12px;
                    cursor: pointer;
                    transition: all 0.3s ease;
                    border: 2px solid transparent;
                    &:hover {
                        background: #f8f9ff;
                        border-color: #e6f0ff;
                    }
                    &.active {
                        background: #e6f0ff;
                        border-color: #4096ff;
                    }
                    .session-info {
                        flex: 1;
                        .session-title {
                            font-weight: 500;
                            font-size: 14px;
                            color: #333;
                            margin-bottom: 4px;
                            white-space: nowrap;
                            overflow: hidden;
                            text-overflow: ellipsis;
                            .session-meta {
                                display: flex;
                                align-items: center;
                                gap: 8px;
                                margin-bottom: 6px;
                                .session-time {
                                    font-size: 12px;
                                    color: #999;
                                }
                            }
                            .session-preview {
                                width: 200px;
                                font-size: 12px;
                                color: #666;
                                margin-bottom: 6px;
                                white-space: nowrap;
                                overflow: hidden;
                                text-overflow: ellipsis;
                            }
                            .session-stats {
                                display: flex;
                                align-items: center;
                                gap: 12px;
                                span {
                                    font-size: 12px;
                                    color: #999;
                                    display: flex;
                                    align-items: center;
                                    gap: 4px;
                                }
                            }
                        }
                        .session-actions {
                            position: absolute;
                            top: 10px;
                            right: 12px;
                        }
                    }
                }
                .no-sessions-text {
                    text-align: center;
                    font-size: 14px;
                    color: #999;
                }
            }
        }
        .emotion-garden {
            background: linear-gradient(135deg, #fef9e7 0%, #fcf4e6 50%, #f6f0e8 100%);
            border-radius: 20px;
            padding: 16px;
            margin-bottom: 20px;
            box-shadow: 0 8px 32px rgba(252, 244, 230, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.2);
            position: relative;
            overflow: hidden;
            min-height: 300px;
            
            .garden-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 20px;
                position: relative;
                z-index: 2;
                .garden-title {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-size: 16px;
                    font-weight: 600;
                    color: #8b4513;
                }
            }
            .emotion-info {
                margin: 0 auto;
                width: 80px;
                height: 80px;
                border-radius: 50%;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                z-index: 10;
                box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
                border: 2px solid rgba(255, 255, 255, 0.8);
                background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 50%, #fecfef 100%);
                color: #fff;
                .emotion-name {
                    font-size: 15px;
                    font-weight: 600;
                    line-height: 1;
                    margin-bottom: 2px;
                }
                .emotion-score {
                    font-size: 14px;
                    font-weight: 700;
                    opacity: 0.9;
                }
            }
            .warm-tips {
                text-align: center;
                margin-bottom: 16px;
                .emotion-status-text {
                    margin-bottom: 12px;
                    .status-label {
                        font-size: 14px;
                        color: #8b7355;
                        margin-right: 8px;
                    }
                    .status-emotion {
                        font-size: 16px;
                        font-weight: 600;
                        padding: 4px 12px;
                        border-radius: 16px;
                        display: inline-block;
                    }
                }
                .emotion-intensity {
                    margin-bottom: 16px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                    .intensity-dots {
                        display: flex;
                        gap: 4px;
                        .dot {
                            width: 8px;
                            height: 8px;
                            border-radius: 50%;
                            background: #e0e0e0;
                            transition: all 0.3s ease;
                            &.active {
                                background: linear-gradient(135deg, #ff9a9e, #fecfef);
                                transform: scale(1.2);
                                box-shadow: 0 2px 8px rgba(255, 154, 158, 0.4);
                            }
                        }
                    }
                    .intensity-text {
                        font-size: 12px;
                        color: #8b7355;
                        font-weight: 500;
                    }
                }
                .warm-suggestion {
                    background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(255, 255, 255, 0.8));
                    border-radius: 16px;
                    padding: 12px;
                    margin-bottom: 16px;
                    display: flex;
                    align-items: flex-start;
                    gap: 10px;
                    border: 1px solid rgba(255, 255, 255, 0.6);
                    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
                    .suggestion-icon {
                        font-size: 20px;
                        flex-shrink: 0;
                        margin-top: 2px;
                    }
                    .suggestion-content {
                        text-align: left;
                        flex: 1;
                        .suggestion-title {
                            font-size: 14px;
                            font-weight: 600;
                            color: #8b7355;
                            margin-bottom: 6px;
                        }
                        .suggestion-text {
                            font-size: 13px;
                            color: #6b5b47;
                            line-height: 1.5;
                        }
                    }
                }
                .healing-actions {
                    margin-bottom: 16px;
                    .actions-title {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        color: #8b7355;
                        margin-bottom: 16px;
                    }
                    .actions-list {
                        display: flex;
                        flex-direction: column;
                        gap: 10px;
                        .action-item {
                            background: linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.7));
                            border-radius: 12px;
                            padding: 12px;
                            display: flex;
                            align-items: center;
                            gap: 10px;
                            border: 1px solid rgba(255, 255, 255, 0.5);
                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
                            text-align: left;
                            .action-icon {
                                font-size: 14px;
                                color: #ffd700;
                                flex-shrink: 0;
                            }
                            .action-text {
                                font-size: 12px;
                                color: #6b5b47;
                                line-height: 1.4;
                                flex: 1;
                            }
                        }
                    }
                }
                .risk-notice {
                    background: linear-gradient(135deg, #fff9e6, #ffeaa7);
                    border-radius: 16px;
                    padding: 16px;
                    display: flex;
                    align-items: flex-start;
                    gap: 12px;
                    border: 1px solid rgba(255, 234, 167, 0.6);
                    box-shadow: 0 6px 20px rgba(255, 234, 167, 0.3);
                    .notice-icon {
                        font-size: 20px;
                        flex-shrink: 0;
                        margin-top: 2px;
                    }
                    .notice-content {
                        flex: 1;
                        .notice-title {
                            font-size: 14px;
                            font-weight: 600;
                            color: #d4840f;
                            margin-bottom: 6px;
                        }
                        .notice-text {
                            font-size: 13px;
                            color: #b8740c;
                            line-height: 1.5;
                        }
                    }
                }
            }
        }
    }
    .chat-main {
        background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(255, 252, 250, 0.98) 100%);
        border-radius: 20px;
        box-shadow: 0 12px 40px rgba(251, 146, 60, 0.08), 0 4px 16px rgba(0, 0, 0, 0.04);
        border: 1px solid rgba(251, 146, 60, 0.1);
        backdrop-filter: blur(10px);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        flex: 1;
        .chat-header {
            background: linear-gradient(135deg, #fb923c 0%, #f59e0b 100%);
            color: white;
            padding: 20px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: relative;
            flex-shrink: 0;
            .header-left {
                display: flex;
                align-items: center;
                .chat-avatar {
                    width: 48px;
                    height: 48px;
                    background: rgba(255, 255, 255, 0.25);
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    margin-right: 16px;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                    position: relative;
                    z-index: 1;
                }
                .chat-info {
                    h2 {
                        font-size: 20px;
                        font-weight: 700;
                        margin-bottom: 4px;
                    }
                    p {
                        font-size: 14px;
                    }
                }
            }
        }
        .chat-messages {
            flex: 1;
            overflow-y: auto;
            padding: 24px;
            display: flex;
            flex-direction: column;
            gap: 16px;
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.02) 0%, rgba(255, 252, 248, 0.05) 100%);
            min-height: 0;
            max-height: calc(100vh - 200px);
            scrollbar-width: thin;
            scrollbar-color: rgba(251, 146, 60, 0.3) transparent;
            .message-item {
                display: flex;
                align-items: flex-start;
                gap: 12px;
                .message-avatar {
                    width: 32px;
                    height: 32px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 14px;
                    color: white;
                    flex-shrink: 0;
                }
                &.ai-message {
                    .message-avatar {
                        background: linear-gradient(135deg, #fb923c, #f59e0b);
                        box-shadow: 0 4px 12px rgba(251, 146, 60, 0.3);
                    }
                }
                &.user-message {
                    .message-avatar {
                        background: linear-gradient(135deg, #6b7280, #4b5563);
                        box-shadow: 0 4px 12px rgba(107, 114, 128, 0.3);
                    }
                }
                .message-content {
                    max-width: 70%;
                    .message-bubble {
                        background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(255, 252, 248, 0.95) 100%);
                        border-radius: 16px;
                        padding: 12px 16px;
                        position: relative;
                        animation: fadeInUp 0.4s ease-out;
                        border: 1px solid rgba(251, 146, 60, 0.1);
                        box-shadow: 0 4px 16px rgba(251, 146, 60, 0.05);
                        .typing-indicator {
                            display: flex;
                            gap: 4px;
                            padding: 8px 0;
                            .typing-dot {
                                width: 8px;
                                height: 8px;
                                background: #ccc;
                                border-radius: 50%;
                                animation: typing 1.5s ease-in-out infinite;
                                &:nth-child(2) {
                                    animation-delay: 0.2s;
                                }
                                &:nth-child(3) {
                                    animation-delay: 0.4s;
                                }   
                            }
                        }
                        /* 错误消息样式 */
                        .error-message {
                            background: linear-gradient(135deg, #FEF2F2 0%, #FECACA 100%);
                            border: 1px solid #F87171;
                            border-radius: 12px;
                            padding: 12px 16px;
                            color: #991B1B;
                            font-weight: 500;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                        }
                    }
                    .message-time {
                        font-size: 12px;
                        color: #999;
                        margin-top: 4px;
                    }
                }
            }
        }
        .chat-input {
            border-top: 1px solid rgba(251, 146, 60, 0.1);
            padding: 20px 24px;
            display: flex;
            gap: 12px;
            align-items: flex-end;
            background: linear-gradient(135deg, rgba(255, 255, 255, 0.5) 0%, rgba(255, 252, 248, 0.7) 100%);
            backdrop-filter: blur(10px);
            flex-shrink: 0;
            .input-container {
                flex: 1;
            }
            .input-footer {
                display: flex;
                justify-content: space-between;
                align-items: center;
                font-size: 12px;
                color: #78716c;
                font-weight: 500;
            }
            .send-btn {
                height: 60px;
                width: 60px;
                border-radius: 16px;
                background: linear-gradient(135deg, #fb923c 0%, #f59e0b 100%) !important;
                border: none !important;
                box-shadow: 0 6px 20px rgba(251, 146, 60, 0.25);
                transition: all 0.3s ease;
            }

        }

    }
}
</style>
