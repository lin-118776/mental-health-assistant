<template>
    <main>
        <PageHead title="AI知识资料" />
        <el-alert v-if="status && !status.ingestionEnabled" title="异步入库尚未启用，请按部署文档启动 RabbitMQ 并设置 RAG_INGESTION_ENABLED=true；任务将保留在队列中。" type="warning" :closable="false" />
        <p>导入经过审核的纯文本资料。资料用于 AI 回答参考，不用于诊断；勿导入个人隐私。来源地址仅作记录，不会抓取网页。</p>
        <el-form label-width="90px" :disabled="busy" @submit.prevent="create">
            <el-form-item label="标题"><el-input v-model="form.title" maxlength="200" placeholder="例如：学校心理咨询预约说明" /></el-form-item>
            <el-form-item label="来源地址"><el-input v-model="form.sourceUrl" maxlength="500" placeholder="可选，HTTP(S)地址" /></el-form-item>
            <el-form-item label="资料正文"><el-input v-model="form.content" type="textarea" :rows="6" maxlength="100000" show-word-limit placeholder="粘贴经审核的资料正文" /></el-form-item>
            <el-form-item><el-button native-type="submit" type="primary" :loading="busy">提交异步入库</el-button></el-form-item>
        </el-form>
        <el-button :disabled="loading || busy" @click="load">刷新状态</el-button>
        <el-button :disabled="busy" @click="clearCache">清理知识分类缓存</el-button>
        <p v-if="status">Redis缓存：{{ status.cacheEnabled ? '已配置启用' : '未启用' }}；MCP：{{ status.mcpEndpoint }}（工具调用见部署文档）</p>
        <el-alert v-if="error" title="资料加载失败，请刷新重试" type="error" :closable="false" />
        <el-table :data="rows" v-loading="loading" empty-text="暂无资料">
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column label="状态"><template #default="{row}">{{ labels[row.status] || row.status }}</template></el-table-column>
            <el-table-column prop="chunks" label="分块数" />
            <el-table-column prop="taskStatus" label="任务状态" />
            <el-table-column prop="attempts" label="发布次数" />
            <el-table-column prop="error" label="失败说明" min-width="180" />
            <el-table-column label="操作" width="170"><template #default="{row}">
                <el-button v-if="row.status === 'FAILED'" text type="primary" :disabled="busy" @click="retry(row)">重试</el-button>
                <el-button v-if="row.status !== 'DISABLED'" text type="danger" :disabled="busy" @click="disable(row)">停用</el-button>
            </template></el-table-column>
        </el-table>
        <el-pagination :current-page="page" :page-size="10" :total="total" :disabled="loading || busy" layout="prev,pager,next,total" @current-change="changePage" />
    </main>
</template>
<script setup>
import {ref,reactive,onMounted,onBeforeUnmount} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import {listDocuments,createDocument,retryDocument,disableDocument,integrationStatus,evictCategories} from '@/api/rag'
const form=reactive({title:'',sourceUrl:'',content:''})
const rows=ref([]), page=ref(1),total=ref(0),loading=ref(false),busy=ref(false),error=ref(false),status=ref(null)
const labels={QUEUED:'等待入库',READY:'可检索',FAILED:'入库失败',DISABLED:'已停用'}
let disposed=false,requestId=0
const load=async()=>{
    const id=++requestId;loading.value=true;error.value=false
    try {
        const res=await listDocuments(page.value)
        if(disposed || id!==requestId)return
        const last=Math.max(1,Math.ceil(res.data.total/10))
        if(page.value>last){page.value=last;return await load()}
        rows.value=res.data.records;total.value=res.data.total
    }catch{if(!disposed && id===requestId){error.value=true;rows.value=[]}}
    finally{if(!disposed && id===requestId)loading.value=false}
}
const create=async()=>{
    if(busy.value || disposed)return
    if(!form.title.trim() || !form.content.trim())return ElMessage.error('请填写标题和正文')
    busy.value=true
    try{await createDocument({...form,title:form.title.trim()});if(disposed)return
        Object.assign(form,{title:'',sourceUrl:'',content:''});page.value=1;ElMessage.success('资料和入库任务已保存');await load()
    }catch{/* 保留输入供重试 */}finally{if(!disposed)busy.value=false}
}
const retry=async row=>{
    if(busy.value)return;busy.value=true
    try{await retryDocument(row.id);if(!disposed)await load()}catch{}finally{if(!disposed)busy.value=false}
}
const disable=async row=>{
    if(busy.value)return;busy.value=true
    try{await ElMessageBox.confirm('停用后该资料不再参与新回答的检索。已有聊天引用会保留。','停用资料',{type:'warning'})
        if(disposed)return;await disableDocument(row.id);if(!disposed)await load()
    }catch{}finally{if(!disposed)busy.value=false}
}
const clearCache=async()=>{
    if(busy.value)return;busy.value=true
    try{await evictCategories();if(!disposed)ElMessage.success('缓存清理请求已完成')}catch{}finally{if(!disposed)busy.value=false}
}
const changePage=value=>{if(page.value===value)return;page.value=value;return load()}
onMounted(()=>{load();integrationStatus().then(res=>{if(!disposed)status.value=res.data}).catch(()=>{})})
onBeforeUnmount(()=>{disposed=true;requestId++})
</script>
