<template>
    <el-dialog :title="isEdit ? '编辑文章' : '新增文章'"  v-model="dialogVisible" width="60%" @close="handleClose" destroy-on-close>

        <el-form :model="formData" ref="formRef" :rules="rules" label-width="120px">
            <el-form-item label="文章标题" prop="title" >
            <el-input v-model="formData.title" maxlength="200" placeholder="请输入文章标题" show-word-limit clearable></el-input>
            </el-form-item>
            <el-form-item label="所属分类" prop="categoryId" >
                <el-select v-model="formData.categoryId" placeholder="请选择分类" clearable>
                    <el-option v-for="item in props.categories" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
            </el-form-item>
            <el-form-item label="文章摘要" prop="summary" >
                <!-- 显示字数 一键清除 最大字数 列数 -->
                <el-input type="textarea" v-model="formData.summary" placeholder="请输入文章摘要(可选)" show-word-limit clearable maxlength="1000" rows="4"></el-input>
            </el-form-item>
            <el-form-item label="标签" prop="tags" >
                <!-- 多选 筛选标签 可创建新标签 -->
                <el-select v-model="formData.tagArray" placeholder="请输入文章标签(可选)" multiple filterable allow-create style="width: 100%;">
                     <el-option v-for="item in commonTags" :key="item" :label="item" :value="item" />
                </el-select>
            </el-form-item>
            <el-form-item label="封面图片"  >
                <div class="avatar-uploader">
                    <el-upload class="avatar-uploader" action="" :before-upload="beforeUpload" :http-request="handleUploadRequest" accept="image/*" :show-file-list="false">
                       <img v-if="imgUrl" :src="imgUrl" class="cover-image" alt="上传图片" />
                       <div v-else class="cover-plaseholder">
                        <p>点击上传封面图片</p>
                       </div>
                    </el-upload>
                    <div v-if="imgUrl" class="cover-remove">
                        <el-button type="danger" @click="handleRemove" size="small">移除封面</el-button>
                    </div>
                </div>
            
            </el-form-item>
            <el-form-item label="文章内容" prop="content" >
                <RichTextEditor v-model="formData.content" 
                placeholder="请输入文章内容"
                :maxCharCount="10000"
                @created="handleEditorCreated"
                @change="handleCountChange"
                min-height="400px"
                />
                
            </el-form-item>
            </el-form>
            <div v-if="btnPreview">
                <h3>内容预览</h3>
                <div v-html="formData.content"></div>
            </div>
              <template #footer>
            <div class="dialog-footer">
                <el-button @click="btnPreview = !btnPreview">
                {{ btnPreview ? '隐藏效果' : '预览效果' }}
                </el-button>
                <el-button @click="handleClose">取消</el-button>
                <el-button type="primary" @click="handleSubmit" :loading="loading">
                {{ isEdit ? '更新文章' : '创建文章' }}
                </el-button>
            </div>
            </template> 
    </el-dialog>


</template>
<script setup>

import { ElDialog } from 'element-plus'
import { computed, reactive, ref,nextTick,watch } from 'vue';
import { ElMessage } from 'element-plus'
import { uploadFile } from '@/api/admin'
import { fileBaseUrl } from '@/config/index'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { createArticle,updateArticle } from '@/api/admin'



const props = defineProps({
    modelValue: {
        type: Boolean,
        default: false
    },
    categories: {
        type: Array,
        default: () => []
    },
    article: {
        type: Object,
        default: null
    }
})

const emit = defineEmits(['update:modelValue','success'])

const dialogVisible = computed({
    get(){
        return props.modelValue
    },
    set(val){
        emit('update:modelValue', val)
    }
})

const isEdit = computed(()=>{!!props.article?.id})

//监听编辑数据
watch(()=>props.article, (newVal)=>{
    if(newVal){
        nextTick(()=>{
             Object.assign(formData, newVal)
        //使用现有Id
        businessId.value = newVal.id

        imgUrl.value = fileBaseUrl + newVal.coverImage
           
        })  
    }
})

const handleClose =()=>{
    //重置表单
    formRef.value.resetFields()
    //重置业务Id
    businessId.value = null
    //重置封面图片
    handleRemove()
    //重置标签
    formData.tagArray = []
    emit('update:modelValue', false)
}


const formData = reactive({
    title:'',
   "content": "",
   "coverImage": "",
   "categoryId": 1,
   "summary": "",
   "tags": "",
   "tagArray": [],
   "id": ""
})

// 重置表单
const resetForm = () => {
    Object.assign(formData, {
        title: '',
        content: '',
        coverImage: '',
        categoryId: props.categories.length > 0 ? props.categories[0].value : '',
        summary: '',
        tags: '',
        tagArray: [],
        id: ''
    })
    imgUrl.value = ''
    if (editorInstance.value) {
        editorInstance.value.clear()
    }
}

// 设置表单数据（用于编辑）
const setFormData = (data) => {
    Object.assign(formData, {
        ...data,
        tagArray: data.tags ? data.tags.split(',') : []
    })
    imgUrl.value = data.coverImage ? (fileBaseUrl + data.coverImage) : ''
    if (editorInstance.value) {
        editorInstance.value.setHtml(data.content || '')
    }
}

defineExpose({
    resetForm,
    setFormData
})

const rules = reactive({
    title: [
        { required: true, message: '请输入文章标题', trigger: 'blur' },
        { max: 200, message: '文章标题最多200个字符', trigger: 'blur' }
    ],
    categoryId: [
        { required: true, message: '请选择分类', trigger: 'change' }
    ]
    
})
const commonTags = [ '情绪管理','焦虑','抑郁','压力','睡眠','冥想','正念','放松','心理健康','自我成长','人际关系','工作压力','学习方法','生活技巧' ]

// 上传封面图片前的校验函数
const imgUrl = ref('')
const beforeUpload = (file) => {
    const isLt5M = file.size / 1024 / 1024 < 5
    const isJPG = file.type === 'image/jpeg'
    const isPNG = file.type === 'image/png'
    if (!isJPG && !isPNG) {
        ElMessage.error('上传图片只能是 JPG/PNG 格式!')
        return false
}
if (!isLt5M) {
    ElMessage.error('上传图片大小不能超过 5MB!')
    return false
}
return true

}
const businessId = ref(null)
const handleUploadRequest = async ({file}) => {
   //UUID生成
   businessId.value = crypto.randomUUID()
   const res = await uploadFile(file,{
        id:businessId.value
    })

    //拼接完整图片
    imgUrl.value = fileBaseUrl + res.data.filePath
    formData.coverImage = res.data.filePath

}

// 移除封面图片
const handleRemove = () =>{
    imgUrl.value = ''
    formData.coverImage = ''
}


 // 富文本创建时调用
 const editorInstance = ref(null)
const handleEditorCreated = (editor) => {
    editorInstance.value = editor
    if(formData.content && editor){
        nextTick(()=>{
            editor.setHTML(formData.content)
        })
    }
}
// 富文本内容改变时调用
const handleCountChange = (data) => {
    formData.content = data.html
}

const btnPreview = ref(false)

//提交
const formRef = ref()
const loading = ref(false)
const handleSubmit = async () => {
   if (!formRef.value) return
   
   formRef.value.validate(async (valid) => {
    if(valid){
        if (!formData.content || formData.content === '<p><br></p>') {
            ElMessage.warning('文章内容不能为空')
            return
        }

        loading.value = true
        const submitData ={
            ...formData,
            tags: formData.tagArray.join(',')
        }
        delete submitData.tagArray
        
        if(!isEdit.value){
            submitData.id = businessId.value
            createArticle(submitData).then(res=>{
                loading.value=false
                emit('success')
            })
        }else{
            updateArticle(props.id,submitData).then(res=>{
                loading.value=false
                emit('success')
            })
        }
    }
   })
}


</script>

<style lang="scss" scoped>
.cover-plaseholder{
    width: 200px;
    height: 120px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;
    color:#8b949e;
    background-color: #f6f8fa;
}
.cover-image{
    width: 200px;
    height: 120px;
    display: block;
    object-fit: cover;
    border-radius: 6px;
}
.cover-remove {
    margin-top: 10px;
}
</style>
