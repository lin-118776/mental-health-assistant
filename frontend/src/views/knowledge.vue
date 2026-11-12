<template>
    <div>
       <PageHead title="知识文章">
        <template #buttons >
            <el-button type="primary" :disabled="busyId !== null" @click="handleEdit({})">
                新增
            </el-button>
          
        </template>
        </PageHead>
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <p v-if="categoryError">分类加载失败 <el-button text type="primary" @click="loadCategories">重试</el-button></p>
        <el-alert v-if="listError" title="文章列表加载失败，请重试" type="error" :closable="false" />
        <el-button v-if="listError" @click="loadArticles()">重试</el-button>
        <el-table v-loading="listLoading" :data="tableData" empty-text="没有符合条件的文章" style="width: 100%;margin-top: 25px;" >
            <el-table-column label="文章标题" width="200px" fixed="left">
                <template #default="scope">
                   <div style="display: flex; align-items: center;">
                    <el-icon><timer /></el-icon>
                    <span>{{ scope.row.title }}</span>
                   </div>
                </template>
            </el-table-column>
            <el-table-column label="分类" width="150px">
                <template #default="scope">
                   <div style="display: flex; align-items: center;">
                    <span>{{ scope.row.categoryName || categoryMap[scope.row.categoryId] || '未分类' }}</span>
                   </div>
                </template>
            </el-table-column>
            <el-table-column prop="authorName" width="150px" label="作者" />
            <el-table-column prop="readCount" width="100px" label="阅读量" />
            <el-table-column label="状态" width="100"><template #default="scope">{{ ['草稿', '已发布', '已下线'][scope.row.status] || '未知' }}</template></el-table-column>
            <el-table-column prop="publishedAt" width="150px" label="发布时间" />
             <el-table-column label="操作" width="240px" fixed="right">
                <template #default="scope">
                   <el-button text type="primary" :disabled="busyId !== null || listLoading" @click="handleEdit(scope.row)">编辑</el-button>
                   <el-button :disabled="busyId !== null || listLoading" @click="handlePublish(scope.row)" v-if="scope.row.status == '0' || scope.row.status == '2'" text type="success">发布</el-button>
                   <el-button :disabled="busyId !== null || listLoading" @click="handleUnpublish(scope.row)" v-if="scope.row.status == '1'" text type="warning">下线</el-button>
                   <el-button :disabled="busyId !== null || listLoading" @click="handleDelete(scope.row)" v-if="scope.row.status == '0' || scope.row.status == '2'" text type="danger">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination 
            v-if="!listError"
            style="margin-top: 25px; align-items: center; justify-content: center;"
            :page-size="pagintion.pageSize"
            :current-page="pagintion.currentPage"
            :disabled="listLoading || busyId !== null"
            layout="prev, pager, next"
            :total="pagintion.total"
            @current-change="handleChange"
            />

        <ArticleDialog ref="articleDialogRef" v-model="dialogVisible" :categories="categories" @success="handleSuccess" :article="currentArticle" />
        
    </div>
</template>

<script setup>
import ArticleDialog from '@/components/ArticleDialog.vue';
import PageHead from '@/components/PageHead.vue';
import TableSearch from '@/components/TableSearch.vue';
import { categoryTree,articlePage } from '@/api/admin'
import { onMounted,onBeforeUnmount,ref,reactive } from 'vue'
import { Timer } from '@element-plus/icons-vue'
import { getArticleDetail } from '@/api/admin'
import { ElMessageBox,ElMessage } from 'element-plus';
import { changeArticleStatus,deleteArticle } from '@/api/admin'



// 分类映射
const categoryMap = reactive({})
// 分类选项
const categories = ref([])

//列表数据
const tableData = ref([])
const listLoading = ref(false)
const listError = ref(false)
const categoryError = ref(false)
const busyId = ref(null)
let appliedFilters = {}
let listRequestId = 0
let categoryRequestId = 0
let disposed = false

//新增和编辑
const dialogVisible = ref(false)
const currentArticle = ref(null)
const handleSuccess = async () => {
    dialogVisible.value = false
    await loadArticles()
}
let editRequestId = 0
const handleEdit = async (row) => {
    if (busyId.value !== null || disposed) return
    const requestId = ++editRequestId
    dialogVisible.value = false
    currentArticle.value = null
    if (!row.id) {
        dialogVisible.value = true
        return
    }
    try {
        const res = await getArticleDetail(row.id)
        if (disposed || requestId !== editRequestId) return
        currentArticle.value = res.data
        dialogVisible.value = true
    } catch {
        // 详情失败时不打开空白或上一篇文章的编辑表单。
    }
}
const runAction = async (row, action) => {
    if (disposed || busyId.value !== null || listLoading.value) return
    const status = String(row.status)
    if (action === '下线' ? status !== '1' : !['0','2'].includes(status)) return
    busyId.value = row.id
    // 操作开始后，丢弃之前仍在加载的编辑详情。
    editRequestId++
    try {
        await ElMessageBox.confirm('确认' + action + '文章“' + row.title + '”吗？', '确认', {
            confirmButtonText:'确认' + action, cancelButtonText:'取消', type:action === '发布' ? 'info' : 'warning'
        })
        if (disposed) return
        if (action === '删除') await deleteArticle(row.id)
        else await changeArticleStatus(row.id,{status:action === '发布' ? '1' : '2'})
        if (disposed) return
        ElMessage.success(action + '成功')
        await loadArticles()
    } catch {
        // 取消或请求失败时保留原列表，状态只由后端确认后的查询更新。
    } finally {
        if (!disposed) busyId.value = null
    }
}
const handlePublish = row => runAction(row,'发布')
const handleUnpublish = row => runAction(row,'下线')
const handleDelete = row => runAction(row,'删除')

const loadCategories = async () => {
    const requestId = ++categoryRequestId
    categoryError.value = false
    try {
        const res = await categoryTree()
        if (disposed || requestId !== categoryRequestId) return
        categories.value = res.data.map(item => ({label:item.categoryName,value:item.id}))
        for (const key of Object.keys(categoryMap)) delete categoryMap[key]
        res.data.forEach(item => {categoryMap[item.id] = item.categoryName})
        formItem[1].options = categories.value
    } catch {
        if (!disposed && requestId === categoryRequestId) categoryError.value = true
    }
}
onMounted(() => { loadCategories(); loadArticles() })
onBeforeUnmount(() => { disposed = true; listRequestId++; categoryRequestId++; editRequestId++ })

const formItem = reactive([
    {
        comp:'input',
        label:'文章标题',
        prop:'title',
        placeholder:'请输入文章标题'
    },
    {
        comp:'select',
        label:'分类',
        prop:'categoryId',
        placeholder:'请选择分类',
       
    },
    {
        comp:'select',
        label:'状态',
        prop:'status',
        placeholder:'请选择状态',
        options:[
            {
                label:'草稿',
                value:'0'
            },
            {
                label:'已发布',
                value:'1'
            },
            {
                label:'已下线',
                value:'2'
            }
        ]
    }
])

//分页参数
const pagintion = reactive({
    currentPage:1,
    pageSize:10,
    total:0
})

const handleSearch = (formData = {}) => {
    if (busyId.value !== null) return
    appliedFilters = {}
    if (formData.title?.trim()) appliedFilters.title = formData.title.trim()
    if (formData.categoryId !== '' && formData.categoryId != null) appliedFilters.categoryId = formData.categoryId
    if (formData.status !== '' && formData.status != null) appliedFilters.status = String(formData.status)
    pagintion.currentPage = 1
    return loadArticles()
}
const loadArticles = async () => {
    if (disposed) return
    const requestId = ++listRequestId
    listLoading.value = true
    listError.value = false
    tableData.value = []
    try {
        const res = await articlePage({currentPage:pagintion.currentPage,pageSize:pagintion.pageSize,...appliedFilters})
        if (disposed || requestId !== listRequestId) return
        const {records,total} = res.data
        const lastPage = Math.max(1, Math.ceil(total / pagintion.pageSize))
        if (pagintion.currentPage > lastPage) {
            pagintion.currentPage = lastPage
            return await loadArticles()
        }
        pagintion.total = total
        tableData.value = records
    } catch {
        if (!disposed && requestId === listRequestId) listError.value = true
    } finally {
        if (!disposed && requestId === listRequestId) listLoading.value = false
    }
}
const handleChange = val => {
    if (val === pagintion.currentPage || busyId.value !== null) return
    pagintion.currentPage = val
    return loadArticles()
}

</script>

<style lang="scss" scoped>
    .page-head {
        height: 60px;
        line-height: 60px;
        padding-left: 20px;
        background-color: #fff
    }

    .el-button {
        margin-bottom: 3px;
    }
</style>
