<template>
    <div>
        <PageHead title="情绪日志" />
        <TableSearch :formItem="formItems" @search="handleSearch" />
        <el-table :data="tableData" style="width: 100%; margin-top: 20px;">
            <el-table-column prop="id" label="用户ID" width="80" />
            <el-table-column label="用户" width="100">
                <template #default="scope">
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <el-avatar :size="32">{{ scope.row.nickname?.charAt(0) }}</el-avatar>
                        <span style="font-size: 12px;">{{ scope.row.nickname }}</span>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="diaryDate" label="记录日期" width="120" />
            <el-table-column label="情绪评分">
                <template #default="scope">
                    <el-rate :model-value="scope.row.moodScore" max="10" disabled></el-rate>
                </template>
            </el-table-column>
            <el-table-column label="生活指标" width="100">
                <template #default="scope">
                    <div>
                        <p>睡眠：{{ scope.row.sleepQuality }}/5</p>
                        <p>压力：{{ scope.row.stressLevel }}/5</p>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="emotionTriggers" label="情绪触发因素" width="120" />
            <el-table-column prop="diaryContent" label="日记内容" width="250" />
            <el-table-column label="操作" width="240px" fixed="right">
                <template #default="scope">
                    <el-button text type="primary" @click="viewSessionDetail(scope.row)">详情</el-button>

                    <el-button text type="danger" @click="handleDelete(scope.row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <el-pagination style="margin-top: 25px; display: flex; justify-content: center;"
            v-model:current-page="pagination.currentPage" v-model:page-size="pagination.size"
            layout="prev, pager, next, total" :total="pagination.total" @current-change="handlePageChange" />

        <el-dialog title="情绪日志详情" v-model="detailDialogVisable" :close-on-click-modal="false" width="800px" @close="closeDetail">
            <p v-if="detailLoading">正在加载最新日志…</p>
            <div v-else-if="detailError">日志加载失败，可能已被删除或无权访问。<el-button text type="primary" @click="viewSessionDetail({ id: detailId })">重试</el-button></div>
            <div class="detail-content" v-if="currentDetail">
                <div class="detail-section">
                    <h4>用户信息</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="日志ID">{{ currentDetail.id }}</el-descriptions-item>
                        <el-descriptions-item label="昵称">{{ currentDetail.nickname }}</el-descriptions-item>
                        <el-descriptions-item label="用户ID">{{ currentDetail.userId }}</el-descriptions-item>
                        <el-descriptions-item label="记录日期">{{ currentDetail.diaryDate }}</el-descriptions-item>
                    </el-descriptions>
                </div>


                <div class="detail-section">
                    <h4>情绪状态</h4>
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="情绪评分">
                            <el-rate :model-value="currentDetail.moodScore" max="10" disabled></el-rate>
                        </el-descriptions-item>
                        <el-descriptions-item label="主要情绪">
                            <el-tag :type="getEmotionTagType(currentDetail.dominantEmotion)">{{
                                currentDetail.dominantEmotion ||
                                '-' }}</el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="睡眠质量">{{ currentDetail.sleepQuality || '-'
                            }}/5</el-descriptions-item>
                        <el-descriptions-item label="压力水平">{{ currentDetail.stressLevel || '-'
                            }}/5</el-descriptions-item>
                    </el-descriptions>
                </div>

                <div class="detail-section">
                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="情绪触发因素">{{ currentDetail.emotionTriggers || '无'
                            }}</el-descriptions-item>
                        <el-descriptions-item label="日记内容">{{ currentDetail.diaryContent || '无'
                            }}</el-descriptions-item>
                    </el-descriptions>
                </div>

                <div class="detail-section">
                    <h4>AI情绪分析结果</h4>
                    <p v-if="!aiData">{{ analysisNotice }}</p>
                    <div class="ai-analysis-result" v-else>
                        <el-descriptions :column="2" border>
                            <el-descriptions-item label="主要情绪">
                                <el-tag :type="getAiEmotionTagType(aiData.primaryEmotion)">{{ aiData.primaryEmotion
                                    }}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪积极程度（越高越积极）">
                                <el-progress :percentage="aiData.emotionScore"
                                    :color="getEmotionScoreColor(aiData.emotionScore)" :stroke-width="8"></el-progress>
                            </el-descriptions-item>
                            <el-descriptions-item label="风险等级">
                                <el-tag :type="getRiskLevelTagType(aiData.riskLevel)">{{
                                    getRiskLevelText(aiData.riskLevel)
                                    }}</el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="情绪性质">
                                <el-tag :type="aiData.isNegative === true ? 'danger' : aiData.isNegative === false ? 'success' : 'info'">{{ aiData.isNegative === true ? '负面情绪' : aiData.isNegative === false ? '正面情绪' : '暂无分析'
                                    }}</el-tag>
                            </el-descriptions-item>
                        </el-descriptions>

                        <div class="ai-suggestion--section">
                            <h5>AI建议（仅供参考）</h5>
                            <div class="suggestion-section">{{ aiData.suggestion || '无' }}</div>
                        </div>
                        <div class="ai-riskn--section">
                            <h5>风险描述</h5>
                            <div class="risk-content">{{ aiData.riskDescription || '无' }}</div>
                        </div>
                        <div class="ai-improvements-section">
                            <h5>改进建议</h5>
                            <ul class="improvement-list">
                                <li v-for="item in aiData.improvementSuggestions" :key="item">
                                    {{ item }}
                                </li>
                            </ul>
                        </div>

                    </div>
                </div>
                <div class="detail-section">
                    <el-descriptions :column="2" border>
                        <el-descriptions-item label="创建时间">
                            {{ currentDetail.createdAt }}
                        </el-descriptions-item>
                        <el-descriptions-item label="更新时间">
                            {{ currentDetail.updatedAt }}
                        </el-descriptions-item>
                    </el-descriptions>
                </div>
            </div>
            <template #footer>
                <el-button type="primary" @click="closeDetail">关闭</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
import PageHead from '@/components/PageHead.vue'
import TableSearch from '@/components/TableSearch.vue'
import { getEmotionalDiary, getEmotionalDiaryDetail } from '@/api/admin'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessageBox } from 'element-plus'
import { deleteEmotional } from '@/api/admin'
import { ElMessage } from 'element-plus'



const getEmotionTagType = (emotion) => {
    const emotionTypes = {
        '快乐': 'success',
        '平静': 'info',
        '兴奋': 'warning',
        '愤怒': 'danger',
        '悲伤': 'info',
        '焦虑': 'warning'
    }
    return emotionTypes[emotion] || 'info'
}

const getAiEmotionTagType = (emotion) => {
    const emotionTagMap = {
        '快乐': 'success',
        '平静': 'success',
        '兴奋': 'warning',
        '满足': 'success',
        '愤怒': 'danger',
        '悲伤': 'info',
        '焦虑': 'warning',
        '恐惧': 'danger',
        '沮丧': 'info',
        '压力': 'warning'
    }
    return emotionTagMap[emotion] || 'info'
}

const getEmotionScoreColor = (score) => {
    if (score >= 60) return '#67c23a'
    if (score >= 40) return '#e6a23c'
    return '#f56c6c'
}

const getRiskLevelTagType = (riskLevel) => {
    const riskTagMap = {
        0: 'success',
        1: 'info',
        2: 'warning',
        3: 'danger'
    }
    return riskTagMap[riskLevel] || 'info'
}

const getRiskLevelText = (riskLevel) => {
    const riskTextMap = {
        0: '正常',
        1: '关注',
        2: '预警',
        3: '危机'
    }
    return riskTextMap[riskLevel] || '未知风险等级'
}

const formItems = [
    { comp: 'input', prop: 'userId', label: '用户ID', placeholder: '请输入用户ID' },
    {
        comp: 'select',
        prop: 'moodScreRange',
        label: '情绪评分',
        placeholder: '请选择评分范围',
        options: [
            { label: '低分（1-3）', value: '1-3' },
            { label: '中分（4-6）', value: '4-6' },
            { label: '高分（7-10）', value: '7-10' },
        ]
    }
]

// 列表
const tableData = ref([])
// 分页参数
const pagination = ref({
    currentPage: 1,
    size: 10,
    total: 0
})

const handleSearch = async (formData = {}) => {
    const params = {
        page: pagination.value.currentPage,
        size: pagination.value.size,
        ...formData
    }

    try {
        const res = await getEmotionalDiary(params)
        const { records, total } = res.data
        tableData.value = records
        pagination.value.total = total
    } catch (error) {
        console.error('获取情绪日志失败:', error)
    }
}

const handlePageChange = (val) => {
    pagination.value.currentPage = val
    handleSearch()
}

// 详情弹窗
const detailDialogVisable = ref(false)
const currentDetail = ref(null)
const aiData = ref(null)
const analysisNotice = ref('尚无AI分析结果')
const detailLoading = ref(false)
const detailError = ref(false)
const detailId = ref(null)
let detailRequestId = 0
let disposed = false

const closeDetail = () => {
    detailRequestId++
    detailDialogVisable.value = false
    currentDetail.value = null
    aiData.value = null
    detailLoading.value = false
}

const parseAnalysis = raw => {
    aiData.value = null
    analysisNotice.value = '尚无AI分析结果'
    if (!raw) return
    try {
        const value = JSON.parse(raw)
        if (!value || Array.isArray(value) || typeof value !== 'object'
            || typeof value.primaryEmotion !== 'string' || !value.primaryEmotion.trim()
            || !Number.isFinite(value.emotionScore) || value.emotionScore < 0 || value.emotionScore > 100
            || !Number.isInteger(value.riskLevel) || value.riskLevel < 0 || value.riskLevel > 3
            || typeof value.isNegative !== 'boolean') throw new Error('invalid analysis')
        aiData.value = {...value, suggestion:typeof value.suggestion === 'string' ? value.suggestion : '',
            riskDescription:typeof value.riskDescription === 'string' ? value.riskDescription : '',
            improvementSuggestions:Array.isArray(value.improvementSuggestions)
                ? value.improvementSuggestions.filter(item => typeof item === 'string') : []}
    } catch {
        analysisNotice.value = '已有分析数据无法读取'
    }
}

const viewSessionDetail = async row => {
    const requestId = ++detailRequestId
    detailId.value = row.id
    currentDetail.value = null
    aiData.value = null
    detailError.value = false
    detailLoading.value = true
    detailDialogVisable.value = true
    try {
        const res = await getEmotionalDiaryDetail(row.id)
        if (disposed || requestId !== detailRequestId) return
        currentDetail.value = res.data
        parseAnalysis(res.data.aiEmotionAnalysis)
    } catch {
        if (!disposed && requestId === detailRequestId) detailError.value = true
    } finally {
        if (!disposed && requestId === detailRequestId) detailLoading.value = false
    }
}

onBeforeUnmount(() => { disposed = true; closeDetail() })

//删除
const handleDelete =  (row) => {
   ElMessageBox.confirm('确认删除吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then( () => {
       deleteEmotional(row.id).then(() => {
        if (detailId.value === row.id) closeDetail()
        ElMessage.success('删除成功')
        handleSearch()
       })
    })
}

onMounted(() => {
    handleSearch()
})
</script>

<style lang="scss" scoped>
.detail-content {
    .detail-section {
        margin-bottom: 24px;

        h4 {
            margin: 0 0 16px 0;
            color: #303133;
            font-size: 16px;

            i {
                margin-right: 8px;
                color: #409eff;
            }
        }
    }
}

// AI分析相关样式
.ai-analysis-status {
    .ai-status-tag {
        margin-bottom: 4px;

        i {
            margin-right: 4px;
        }
    }

    .ai-analysis-preview {
        font-size: 11px;
        color: #909399;
        margin-top: 2px;
    }
}

.ai-analysis-result {

    .ai-keywords-section,
    .ai-suggestion-section,
    .ai-risk-section,
    .ai-improvements-section {
        margin-top: 16px;
        padding: 12px;
        background-color: #f8f9fa;
        border-radius: 4px;

        h5 {
            margin: 0 0 8px 0;
            color: #606266;
            font-size: 14px;
            font-weight: 600;

            i {
                margin-right: 6px;
                color: #909399;
            }
        }
    }

    .keywords-container {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;

        .keyword-tag {
            background-color: #e1f3d8;
            color: #67c23a;
            border-color: #b3d8a4;
        }
    }

    .suggestion-content,
    .risk-content {
        line-height: 1.6;
        color: #606266;
        background-color: white;
        padding: 8px;
        border-radius: 4px;
        border: 1px solid #ebeef5;
    }

    .improvement-list {
        margin: 0;
        padding-left: 20px;

        li {
            margin-bottom: 4px;
            color: #606266;
            line-height: 1.5;
        }
    }

    .ai-analysis-meta {
        margin-top: 16px;
        padding-top: 12px;
        border-top: 1px solid #ebeef5;

        .analysis-time {
            margin: 0;
            font-size: 12px;
            color: #909399;

            i {
                margin-right: 4px;
            }
        }
    }

    .el-progress {
        .el-progress__text {
            font-size: 12px !important;
        }
    }
}
</style>
