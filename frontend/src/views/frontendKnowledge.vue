<template>
    <div class="knowledge-container">
        <div class="header-section">
            <div class="header-content">
                <el-image :src="iconUrl" style="width: 60px; height: 60px;" />
                <h1>知识库</h1>
            </div>
        </div>
        <el-form class="knowledge-filters" inline @submit.prevent="handleSearch">
            <el-form-item label="文章标题">
                <el-input v-model="filters.title" placeholder="输入标题关键词" maxlength="200" clearable />
            </el-form-item>
            <el-form-item label="分类">
                <el-select v-model="filters.categoryId" placeholder="全部分类" clearable style="width: 180px;" :loading="categoriesLoading">
                    <el-option v-for="category in categories" :key="category.id" :label="category.categoryName" :value="category.id" />
                </el-select>
                <el-button v-if="categoriesError" text type="primary" @click="loadCategories">重试加载分类</el-button>
            </el-form-item>
            <el-form-item label="排序">
                <el-select v-model="filters.sortField" style="width: 150px;">
                    <el-option label="最新发布" value="publishedAt" />
                    <el-option label="最多阅读" value="readCount" />
                </el-select>
            </el-form-item>
            <el-form-item>
                <el-button type="primary" native-type="submit">搜索</el-button>
                <el-button @click="resetSearch">重置</el-button>
            </el-form-item>
        </el-form>
        <el-card class="source-search">
            <h3>AI参考资料检索</h3>
            <p>检索已入库的知识片段，AI 对话会使用相关资料并在回复末尾标注来源。资料仅供参考。</p>
            <el-input v-model="sourceQuery" maxlength="4000" placeholder="例如：如何改善睡眠" @keydown.enter.prevent="findSources" />
            <el-button :loading="sourceLoading" @click="findSources">检索资料</el-button>
            <p v-if="sourceError">资料检索失败，请重试。</p>
            <p v-else-if="sourceSearched && !sourceLoading && !sources.length">未找到相关资料，可换个关键词。</p>
            <article v-for="source in sources" :key="source.documentId + ':' + source.chunkIndex">
                <strong>{{ source.title }}</strong>
                <p>文档 {{ source.documentId }} · 片段 {{ source.chunkIndex }}</p>
                <p>{{ source.text }}</p>
                <p v-if="source.url">原始来源：{{ source.url }}</p>
            </article>
        </el-card>
        <div class="content">
            <!-- 左侧菜单 -->
            <div class="recommend-section">
                <div class="section-title">推荐阅读</div>
                <div class="recommend-list">
                    <p v-if="recommendLoading">正在加载推荐…</p>
                    <div v-else-if="recommendError">推荐加载失败 <el-button text type="primary" @click="loadRecommendations">重试</el-button></div>
                    <p v-else-if="!recommendList.length">暂无推荐文章</p>
                    <div v-for="item in recommendList" :key="item.id" class="recommend-item" @click="goToArticle(item.id)">
                        <h4>{{ item.title }}</h4>
                        <p class="read-count">
                            <el-icon><Histogram /></el-icon>
                            阅读量 {{ item.readCount }}
                        </p>
                    </div>
                </div>
            </div>
            <!-- 右侧内容 -->
            <div class="article-list">
                <p v-if="loading">正在加载文章…</p>
                <div v-else-if="loadError">文章加载失败 <el-button type="primary" text @click="getPageList">重试</el-button></div>
                <el-empty v-else-if="!articleList.length" description="暂无符合条件的文章" />
                <div v-for="item in articleList" :key="item.id" class="article-item" @click="goToArticle(item.id)">
                    <el-image :src="getImage(item.coverImage)" style="width: 240px; height: 150px;">
                        <template #error><img :src="iconUrl" alt="暂无封面" style="width: 100%; height: 100%; object-fit: contain;" /></template>
                    </el-image>
                    <div class="info">
                        <div class="title">
                            <h3>{{ item.title }}</h3>
                            <el-tag plain type="primary">{{ item.categoryName }}</el-tag>
                        </div>
                        <div :style="{marginTop: '10px'}">
                            <div class="flex-box">
                                <el-icon><Avatar /></el-icon>
                                <span>{{ item.authorName }}</span>
                            </div>
                            <div class="flex-box">
                                <el-icon><List /></el-icon>
                                <span>{{ dayjs(item.updatedAt).format('YYYY-MM-DD') }}</span>
                            </div>
                        </div>
                        <div :style="{marginTop: '10px'}">
                            <div class="flex-box">
                                <el-icon><Platform /></el-icon>
                                <span>观看人数 {{ item.readCount }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- 分页 -->
        <div v-if="!loadError" class="pagination-wrapper">
            <el-pagination
                style="margin-top: 25px;"
                :page-size="pagination.size"
                :current-page="pagination.currentPage"
                :disabled="loading"
                layout="prev, pager, next, total"
                :total="pagination.total"
                @current-change="handleChange"
            />
        </div>
    </div>
</template>
 
<script setup>
    import { dayjs } from 'element-plus'
    import { ref , reactive , onMounted, onBeforeUnmount } from 'vue'
    import { getKnowledgeList, getKnowledgeCategories } from '@/api/frontend'
    import { useRouter } from 'vue-router'
    import { resolveFileUrl } from '@/config/index'
    import { searchSources } from '@/api/rag'
 
    const iconUrl = new URL('@/assets/images/book.png', import.meta.url).href
    const router = useRouter()
 
    // 推荐阅读列表
    const recommendList = ref([])
    // 右侧列表数据
    const pagination = reactive({
        currentPage: 1,
        size: 10,
        total: 0
    })
 
    const articleList = ref([])
    const filters = reactive({title:'', categoryId:undefined, sortField:'publishedAt'})
    // 翻页和失败重试沿用最近一次提交的条件，不读取尚未提交的输入。
    let appliedFilters = {sortField:'publishedAt'}
    const categories = ref([])
    const categoriesLoading = ref(false)
    const categoriesError = ref(false)
    const loading = ref(false)
    const loadError = ref(false)
    const recommendLoading = ref(false)
    const recommendError = ref(false)
    let pageRequestId = 0
    let categoryRequestId = 0
    let recommendRequestId = 0
    let disposed = false
    const sourceQuery=ref(''),sources=ref([]),sourceLoading=ref(false),sourceError=ref(false),sourceSearched=ref(false)
    let sourceRequestId=0
    const findSources=async()=>{
        const query=sourceQuery.value.trim()
        if(!query)return
        const id=++sourceRequestId;sourceLoading.value=true;sourceError.value=false;sources.value=[];sourceSearched.value=true
        try{const res=await searchSources(query);if(!disposed && id===sourceRequestId)sources.value=res.data}
        catch{if(!disposed && id===sourceRequestId)sourceError.value=true}
        finally{if(!disposed && id===sourceRequestId)sourceLoading.value=false}
    }
    //获取列表数据
    const getPageList = async () =>{
        const requestId = ++pageRequestId
        loading.value = true
        loadError.value = false
        articleList.value = []
        const params = {
            ...appliedFilters,
            sortDirection: 'desc',
            page: pagination.currentPage,
            size: pagination.size
        }
        try {
            const res = await getKnowledgeList(params)
            if (disposed || requestId !== pageRequestId) return
            articleList.value = res.data.records
            pagination.total = res.data.total
        } catch {
            if (!disposed && requestId === pageRequestId) {
                loadError.value = true
            }
        } finally {
            if (!disposed && requestId === pageRequestId) loading.value = false
        }
    }

    const handleSearch = () => {
        appliedFilters = {sortField:filters.sortField === 'readCount' ? 'readCount' : 'publishedAt'}
        const title = filters.title.trim()
        if (title) appliedFilters.title = title
        if (filters.categoryId !== undefined && filters.categoryId !== null && filters.categoryId !== '') appliedFilters.categoryId = filters.categoryId
        pagination.currentPage = 1
        return getPageList()
    }

    const resetSearch = () => {
        Object.assign(filters, {title:'', categoryId:undefined, sortField:'publishedAt'})
        return handleSearch()
    }

    const loadCategories = async () => {
        const requestId = ++categoryRequestId
        categoriesLoading.value = true
        categoriesError.value = false
        try {
            const res = await getKnowledgeCategories()
            if (!disposed && requestId === categoryRequestId) categories.value = res.data
        } catch {
            if (!disposed && requestId === categoryRequestId) categoriesError.value = true
        } finally {
            if (!disposed && requestId === categoryRequestId) categoriesLoading.value = false
        }
    }

    const loadRecommendations = async () => {
        const requestId = ++recommendRequestId
        recommendLoading.value = true
        recommendError.value = false
        try {
            const res = await getKnowledgeList({sortField:'readCount', sortDirection:'desc', page:1, size:5})
            if (!disposed && requestId === recommendRequestId) recommendList.value = res.data.records
        } catch {
            if (!disposed && requestId === recommendRequestId) recommendError.value = true
        } finally {
            if (!disposed && requestId === recommendRequestId) recommendLoading.value = false
        }
    }
 
    //获取封面图片
    const getImage = (url) =>{
        return resolveFileUrl(url) || iconUrl
    }
 
    // 分页改变时触发
    const handleChange = (page) =>{
        if (page === pagination.currentPage) return
        pagination.currentPage = page
        return getPageList()
    }

    //跳转到详情
    const goToArticle =(id)=>{
        router.push(`/knowledge/article/${id}`)
    }
 
    onMounted(()=>{
        getPageList()
        loadCategories()
        loadRecommendations()
    })
    onBeforeUnmount(() => { disposed = true })
 
</script>
 
<style scoped lang="scss">
.knowledge-container {
    .source-search {max-width:1160px;margin:20px auto;}
    .knowledge-filters {
        max-width: 1200px;
        margin: 20px auto 0;
        padding: 0 20px;
    }
    background: linear-gradient(135deg, #fafbfc 0%, #f7f9fc 50%, #f2f6fa 100%);
    .flex-box {
        display: flex;
        align-items: center;
        span {
            margin-left: 10px;
        }
    }
    .header-section {
        background: linear-gradient(135deg, #f59e0b 0%, #8b5cf6 100%);
        color: white;
        padding: 48px;
        .header-content {
            display: flex;
            align-items: center;
            gap: 12px;
        }
    }
    .content {
        display: flex;
        gap: 20px;
        margin: 0 auto;
        width: 1200px;
        padding: 20px;
        .recommend-section {
            width: 280px;
            background: white;
            border-radius: 12px;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
            padding: 15px;
            height: 400px;
            .section-title {
                font-size: 12;
                font-weight: 600;
                color: #374151;
                margin-bottom: 10px;
                display: flex;
                align-items: center;
                gap: 5px;
            }
            .recommend-list {
                display: flex;
                flex-direction: column;
                gap: 1rem;
                .recommend-item {
                    border-left: 4px solid #f59e0b;
                    padding-left: 10px;
                    cursor: pointer;
                    .read-count {
                        margin-top: 15px;
                        font-size: 12px;
                        color: #6b7280;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                }
            }
        }
        .article-list {
            flex: 1;
            .article-item {
                background: white;
                border-radius: 12px;
                box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
                padding: 15px;
                margin-bottom: 20px;
                display: flex;
                .info {
                    margin-left: 20px;
                    .title {
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                }
            }
        }
    }
    .pagination-wrapper {
        display: flex;
        justify-content: center;
        padding-bottom: 30px;
    }
}
</style>
