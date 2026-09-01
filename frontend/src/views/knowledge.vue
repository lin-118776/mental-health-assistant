<template>
    <div>
       <PageHead title="知识文章">
        <template #buttons >
            <el-button type="primary" @click="handleEdit({})">
                新增
            </el-button>
          
        </template>
        </PageHead>
        <TableSearch :formItem="formItem" @search="handleSearch" />
        <el-table :data="tableData" style="width: 100%;margin-top: 25px;" >
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
                    <span>{{ categoryMap[scope.row.categoryId] }}</span>
                   </div>
                </template>
            </el-table-column>
            <el-table-column prop="authorName" width="150px" label="作者" />
            <el-table-column prop="readCount" width="100px" label="阅读量" />
            <el-table-column prop="updatedAt" width="150px" label="发布时间" />
             <el-table-column label="操作" width="240px" fixed="right">
                <template #default="scope">
                   <el-button text type="primary" @click="handleEdit(scope.row)">编辑</el-button>
                   <el-button  @click="handlePublish(scope.row)" v-if="scope.row.status == '0' || scope.row.status == '2'"  text type="success" >发布</el-button>
                   <el-button  @click="handleUnpublish(scope.row)" v-if="scope.row.status == '1'" text type="warning" >下线</el-button>
                   <el-button  @click="handleDelete(scope.row)" v-if="scope.row.status == '0' || scope.row.status == '2'" text type="danger" >删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <el-pagination 
            style="margin-top: 25px; align-items: center; justify-content: center;"
            :page-size="pagintion.pageSize"
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
import { onMounted,ref,reactive } from 'vue'
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

//新增和编辑
const dialogVisible = ref(false)
const currentArticle = ref(null)
const handleSuccess = () => {
    dialogVisible.value = false
    handleSearch({})
}
const handleEdit = (row) => {
    dialogVisible.value = true
    // TODO: 设置编辑数据
   if(!row.id)
   {
     currentArticle.value = null
    dialogVisible.value = true
    
   } else
   {
    getArticleDetail(row.id).then(res=>{
        currentArticle.value = res.data
        dialogVisible.value = true
    })
   }
}
// 发布文章
const handlePublish = (row) => {
    ElMessageBox.confirm(`确认发布文章${row.title}吗？`, '确认', {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        type: 'info'
    }).then(() => {
        changeArticleStatus(row.id,{
            status:'1'
        }).then(res=>{
            ElMessage.success('发布成功')
            handleSearch({})
        })
    })
   
}
// 下线文章
const handleUnpublish = (row) => {
    ElMessageBox.confirm(`确认下线文章${row.title}吗？`, '确认', {
        confirmButtonText: '确认下线',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(() => {
        changeArticleStatus(row.id,{
            status:'2'
        }).then(res=>{
            ElMessage.success('下线成功')
            handleSearch({})
        })
    })
   
}
// 删除文章
const handleDelete = (row) => {
        ElMessageBox.confirm(`确认删除文章${row.title}吗？`, '确认', {
            confirmButtonText: '确认删除',
            cancelButtonText: '取消',
            type: 'danger'
        }).then(() => {
            deleteArticle(row.id).then(res=>{
                ElMessage.success('删除成功')
                handleSearch({})
            })
        })
}
onMounted( async () => {
    const res = await categoryTree()
    categories.value = res.data.map(item=>{
        categoryMap[item.id] = item.categoryName
        return {
            label:item.categoryName,
            value:item.id
        }      
    })
    formItem[1].options = categories.value

    //获取列表
    handleSearch({})
})

const formItem = [
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
]

//分页参数
const pagintion = reactive({
    currentPage:1,
    pageSize:10,
    total:0
})

// 查询文章列表
const handleSearch = async (formData) => {
    console.log(formData,'查询参数');

const params ={
    ...pagintion,
    ...formData
}

    const res = await articlePage(params)
    const {records,total} = res.data
        
        pagintion.total = total
        tableData.value = records
    }

        // 分页改变时触发
        const handleChange = (val) => {
            pagintion.currentPage = val
            handleSearch({})
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