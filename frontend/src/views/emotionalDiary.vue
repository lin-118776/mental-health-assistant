<template>
    <div class="emotionDiary-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px; height: 60px;" />
                <h1>情绪日志</h1>
            </div>
        </div>
        <div class="content">
            <div class="diary-card">
                <div class="history-header">
                    <div class="title">我的日志</div>
                    <el-button :disabled="saving || operationLoading" @click="newDiary">写新日志</el-button>
                </div>
                <el-alert v-if="listError" title="日志加载失败，请点击刷新重试" type="error" :closable="false" />
                <el-button v-if="listError" @click="loadDiaries">刷新</el-button>
                <el-table v-loading="listLoading" :data="diaries" empty-text="暂无情绪日志，写下今天的心情吧">
                    <el-table-column prop="diaryDate" label="日期" width="120" />
                    <el-table-column prop="dominantEmotion" label="主要情绪" width="100" />
                    <el-table-column prop="moodScore" label="评分" width="80" />
                    <el-table-column prop="diaryContent" label="今日感想" show-overflow-tooltip />
                    <el-table-column label="操作" width="210">
                        <template #default="scope">
                            <el-button text type="primary" :disabled="saving || operationLoading" @click="openDiary(scope.row.id, false)">查看</el-button>
                            <el-button text type="primary" :disabled="saving || operationLoading" @click="openDiary(scope.row.id, true)">编辑</el-button>
                            <el-button text type="danger" :disabled="saving || operationLoading" @click="removeDiary(scope.row)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>
                <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="prev, pager, next, total" :disabled="listLoading" @current-change="loadDiaries" />
            </div>
            <div ref="editorElement" class="diary-card">
                <div class="title">{{ editingId === null ? '写日志' : '编辑日志' }}</div>
                <el-alert v-if="editingId !== null" title="正在编辑历史日志，保存后会更新原记录" type="info" :closable="false" />
                <div class="diary-date">
                    <span>日志日期</span>
                    <el-date-picker v-model="diaryForm.diaryDate" type="date" value-format="YYYY-MM-DD" :clearable="false" :disabled="saving" />
                </div>
            </div>
            <!-- 情绪评分 -->
            <div class="diary-card">
                <div class="title">情绪评分</div>
                <div class="section">
                    <p>您今天的整体情绪状态如何?（1-10分）</p>
                    <div class="rate">
                        <el-rate 
                            v-model="diaryForm.moodScore"
                            :texts="emotionStatus"
                            show-text
                            :max="10"
                            size="large"
                        />
                    </div>
                </div>
            </div>
            <!-- 主要情绪 -->
            <div class="diary-card">
                <div class="title">主要情绪</div>
                <div class="emotion-grid">
                    <div v-for="emotion in emotionOptions" :key="emotion.name" class="emotion-card" :class="{'selected': emotion.name === diaryForm.dominantEmotion}" @click="selectEmotion(emotion.name)">
                        <el-image :src="emotion.url" style="width: 50px; height: 50px;" />
                        <div class="emotion-name">{{ emotion.name }}</div>
                    </div>
                </div>
            </div>
            <!-- 评测记录 -->
            <div class="diary-card">
                <div class="title">评测记录</div>
                <div class="detail-form">
                    <div class="form-group">
                        <div class="form-label">情绪触发因素</div>
                        <el-input v-model="diaryForm.emotionTriggers" placeholder="今天什么事情影响了您的情绪？" type="textarea" :rows="3" maxLength="1000" show-word-limit />
                    </div>
                    <div class="form-group">
                        <div class="form-label">今日感想</div>
                        <el-input v-model="diaryForm.diaryContent" placeholder="写下您今天的想法，感受或发生的有趣事情..." type="textarea" :rows="5" maxLength="1000" show-word-limit />
                    </div>
                    <!-- 生活指标 -->
                     <div class="life-indicators">
                        <div class="indicator-group">
                            <div class="form-label">睡眠质量</div>
                            <el-select v-model="diaryForm.sleepQuality" placeholder="请选择" clearable>
                                <el-option label="很差" value="1" />
                                <el-option label="较差" value="2" />
                                <el-option label="一般" value="3" />
                                <el-option label="良好" value="4" />
                                <el-option label="优秀" value="5" />
                            </el-select>
                        </div>
                        <div class="indicator-group">
                            <div class="form-label">压力水平</div>
                            <el-select v-model="diaryForm.stressLevel" placeholder="请选择" clearable>
                                <el-option label="很低" value="1" />
                                <el-option label="较低" value="2" />
                                <el-option label="中等" value="3" />
                                <el-option label="较高" value="4" />
                                <el-option label="很高" value="5" />
                            </el-select>
                        </div>
                     </div>
                     <div class="action-buttons">
                        <el-button :disabled="saving" @click="resetForm">{{ editingId === null ? '重置' : '取消编辑' }}</el-button>
                        <el-button type="primary" :loading="saving" @click="submit">{{ editingId === null ? '提交' : '保存修改' }}</el-button>
                     </div>
                </div>
            </div>
        </div>
        <el-dialog v-model="detailVisible" title="日志详情" width="min(680px, 92vw)">
            <el-descriptions v-if="detail" :column="2" border>
                <el-descriptions-item label="日期">{{ detail.diaryDate }}</el-descriptions-item>
                <el-descriptions-item label="评分">{{ detail.moodScore }} / 10</el-descriptions-item>
                <el-descriptions-item label="主要情绪">{{ detail.dominantEmotion || '未填写' }}</el-descriptions-item>
                <el-descriptions-item label="睡眠质量">{{ sleepLabels[detail.sleepQuality] || '未填写' }}</el-descriptions-item>
                <el-descriptions-item label="压力水平" :span="2">{{ stressLabels[detail.stressLevel] || '未填写' }}</el-descriptions-item>
                <el-descriptions-item label="情绪触发因素" :span="2"><div class="diary-text">{{ detail.emotionTriggers || '未填写' }}</div></el-descriptions-item>
                <el-descriptions-item label="今日感想" :span="2"><div class="diary-text">{{ detail.diaryContent || '未填写' }}</div></el-descriptions-item>
            </el-descriptions>
        </el-dialog>
    </div>
</template>
 
<script setup>
    import { dayjs, ElMessage, ElMessageBox } from 'element-plus'
    import { ref , reactive, onMounted } from 'vue'
    import { addEmotionDiary, getMyEmotionDiaries, getMyEmotionDiary, updateMyEmotionDiary, deleteMyEmotionDiary } from '@/api/frontend'

    const diaries = ref([])
    const page = ref(1)
    const total = ref(0)
    const listLoading = ref(false)
    const listError = ref(false)
    const saving = ref(false)
    const operationLoading = ref(false)
    const editingId = ref(null)
    const editorElement = ref(null)
    const detailVisible = ref(false)
    const detail = ref(null)
    const sleepLabels = ['', '很差', '较差', '一般', '良好', '优秀']
    const stressLabels = ['', '很低', '较低', '中等', '较高', '很高']
    let listRequestId = 0

    const loadDiaries = async () => {
        const requestId = ++listRequestId
        listLoading.value = true
        listError.value = false
        try {
            const res = await getMyEmotionDiaries({ page: page.value, size: 10 })
            if (requestId !== listRequestId) return
            total.value = res.data.total
            const lastPage = Math.max(1, Math.ceil(total.value / 10))
            if (page.value > lastPage) {
                page.value = lastPage
                return loadDiaries()
            }
            diaries.value = res.data.records
        } catch {
            if (requestId === listRequestId) {
                diaries.value = []
                listError.value = true
            }
        } finally {
            if (requestId === listRequestId) listLoading.value = false
        }
    }

    const newDiary = () => {
        resetForm()
        editorElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }

    const openDiary = async (id, edit) => {
        if (saving.value || operationLoading.value) return
        operationLoading.value = true
        try {
            const res = await getMyEmotionDiary(id)
            if (edit) {
                const record = res.data
                Object.assign(diaryForm, {
                    diaryDate: record.diaryDate,
                    moodScore: record.moodScore,
                    dominantEmotion: record.dominantEmotion || '',
                    emotionTriggers: record.emotionTriggers || '',
                    diaryContent: record.diaryContent || '',
                    sleepQuality: record.sleepQuality == null ? null : String(record.sleepQuality),
                    stressLevel: record.stressLevel == null ? null : String(record.stressLevel)
                })
                editingId.value = id
                editorElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
            } else {
                detail.value = res.data
                detailVisible.value = true
            }
        } catch {
            // 请求工具已提示错误，保留当前表单。
        } finally {
            operationLoading.value = false
        }
    }

    const removeDiary = async (record) => {
        if (saving.value || operationLoading.value) return
        operationLoading.value = true
        try {
            await ElMessageBox.confirm(`确定删除 ${record.diaryDate} 的日志吗？删除后无法恢复。`, '删除日志', { type: 'warning' })
            await deleteMyEmotionDiary(record.id)
            if (editingId.value === record.id) resetForm()
            if (detail.value?.id === record.id) detailVisible.value = false
            ElMessage.success('删除成功')
            await loadDiaries()
        } catch {
            // 取消删除或接口失败时保留原记录。
        } finally {
            operationLoading.value = false
        }
    }
 
    //情绪评分
    const emotionStatus = ['绝望崩溃', '消沉抑郁', '焦虑烦躁', '低落不悦', '平静淡然', '轻松惬意', '愉悦舒心', '欢欣满足', '兴奋欣喜', '极致幸福']
 
    //情绪选项
    const emotionOptions =[
        { name: '开心' , url: new URL('@/assets/images/开心.png', import.meta.url).href},
        { name: '平静' , url: new URL('@/assets/images/平静.png', import.meta.url).href},
        { name: '焦虑' , url: new URL('@/assets/images/焦虑.png', import.meta.url).href},
        { name: '悲伤' , url: new URL('@/assets/images/悲伤.png', import.meta.url).href},
        { name: '兴奋' , url: new URL('@/assets/images/兴奋.png', import.meta.url).href},
        { name: '疲惫' , url: new URL('@/assets/images/疲惫.png', import.meta.url).href},
        { name: '惊讶' , url: new URL('@/assets/images/惊讶.png', import.meta.url).href},
        { name: '困惑' , url: new URL('@/assets/images/困惑.png', import.meta.url).href}
    ]
 
    //选择情绪
    const selectEmotion = (emotion) => {
        diaryForm.dominantEmotion = emotion
    }
 
    const diaryForm = reactive({
        diaryDate: dayjs().format('YYYY-MM-DD'),
        moodScore: null,
        dominantEmotion: '',
        emotionTriggers: '',
        diaryContent: '',
        sleepQuality: null,
        stressLevel: null
    })
 
    //重置表单
    const resetForm = () => {
        editingId.value = null
        Object.assign(diaryForm, {
            diaryDate: dayjs().format('YYYY-MM-DD'),
            moodScore: null,
            dominantEmotion: '',
            emotionTriggers: '',
            diaryContent: '',
            sleepQuality: null,
            stressLevel: null
        })
    }
    
    //提交表单
    const submit = async () => {
        if (saving.value || operationLoading.value) return
        if(!diaryForm.moodScore) {
            ElMessage.error('请选择情绪评分')
            return
        }
        if (!diaryForm.diaryDate) {
            ElMessage.error('请选择日志日期')
            return
        }
        saving.value = true
        const id = editingId.value
        const data = {
            ...diaryForm,
            sleepQuality: diaryForm.sleepQuality ? Number(diaryForm.sleepQuality) : null,
            stressLevel: diaryForm.stressLevel ? Number(diaryForm.stressLevel) : null
        }
        try {
            if (id === null) await addEmotionDiary(data)
            else await updateMyEmotionDiary(id, data)
            ElMessage.success(id === null ? '提交成功' : '修改成功')
            resetForm()
            page.value = 1
            await loadDiaries()
        } catch {
            // 失败时保留填写内容，便于重试。
        } finally {
            saving.value = false
        }
    }

    onMounted(loadDiaries)
    
    const iconUrl = new URL('@/assets/images/like.png', import.meta.url).href
</script>
 
<style scoped lang="scss">
.history-header { display: flex; justify-content: space-between; align-items: center; }
.diary-date { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
.diary-text { white-space: pre-wrap; overflow-wrap: anywhere; }
.emotionDiary-container {
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .header-section {
        background: linear-gradient(135deg, #7ED321 0%, #F5A623 100%);
        color: white;
        padding: 48px;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
        }
    }
    .content {
        margin: 0 auto;
        width: 980px;
        padding: 20px;
        .diary-card {
            margin-bottom: 20px;
            background: white;
            border-radius: 10px;
            padding: 20px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
            .title {
                margin-bottom: 20px;
                font-size: 25px;
                font-weight: 600;
                color: #374151;
            }
            .section {
                margin-bottom: 20px;
                p {
                    font-size: 15px;
                    color: #6B7280;
                    margin-bottom: 15px;
                }
            }
            .emotion-grid {
                display: flex;
                flex-wrap: wrap;
                gap: 10px;
                .emotion-card {
                    padding: 15px;
                    border: 2px solid #E5E7EB;
                    border-radius: 15px;
                    text-align: center;
                    cursor: pointer;
                    background: #F9FAFB;
                    .emotion-name {
                        margin-top: 10px;
                        padding: 0 75px;
                        color: #374151;
                    }
                    &.selected {
                        border-color: #7ED321;
                        background: #F0FDF4;
                        transform: translateY(-3px);
                    }
                }
            }
            .detail-form {
                .form-label {
                    margin: 10px 0;
                    color: #374151;
                }
                .life-indicators {
                    display: flex;
                    gap: 20px;
                    .indicator-group {
                        flex: 1;
                    }
                }
                .action-buttons {
                    margin-top: 40px
                }
            }
        }
    }
}
</style>
