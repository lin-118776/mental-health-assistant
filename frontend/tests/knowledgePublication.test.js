import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { resolveFileUrl } from '../src/utils/fileUrl.js'
import { coverFileError } from '../src/utils/coverUpload.js'

const ref = value => ({ value })
const computed = source => ({ get value() { return typeof source === 'function' ? source() : source.get() }, set value(value) { source.set(value) } })
const tick = async () => { await Promise.resolve(); await Promise.resolve() }
const component = (file, values, expose) => {
    const source = fs.readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8')
    const script = source.split('<script setup>')[1].split('</script>')[0]
        .replace(/^\s*import .*$/gm, '').replaceAll('import.meta.url', "'file:///component.vue'")
    const watchers = []
    const context = { ref, reactive: value => value, computed, URL, console, resolveFileUrl, coverFileError,
        nextTick: fn => Promise.resolve().then(() => fn?.()), watch: (get, callback) => watchers.push(callback),
        onMounted() {}, onBeforeUnmount() {}, defineExpose() {}, ElMessage: { warning() {}, success() {}, error() {} }, ...values }
    vm.createContext(context)
    vm.runInContext(`${script}\nglobalThis.test = {${expose}}`, context)
    return { ...context.test, watchers }
}

test('editing updates the existing article, new form creates, and failure preserves input for retry', async () => {
    const props = { modelValue: true, article: null, categories: [{ value: 1 }] }
    const calls = []
    let fail = false
    const view = component('components/ArticleDialog.vue', {
        defineProps: () => props, defineEmits: () => () => {},
        createArticle: async data => calls.push({ type: 'create', data }),
        updateArticle: async (id, data) => { if (fail) throw Error('failed'); calls.push({ type: 'update', id, data }) }
    }, 'isEdit, formData, formRef, handleSubmit, loading, handleEditorCreated')
    const record = { id: 'draft-id', title: '已有标题', content: '<p>已有正文</p>', categoryId: 1, tags: '压力,生活' }
    props.article = record
    view.watchers[0](record)
    await tick()
    view.formRef.value = { validate: async () => true }
    assert.equal(view.isEdit.value, true)
    let editorContent
    view.handleEditorCreated({ setHtml: value => { editorContent = value } })
    await tick()
    assert.equal(editorContent, record.content)
    await view.handleSubmit()
    assert.equal(calls.length, 1)
    assert.equal(calls[0].type, 'update')
    assert.equal(calls[0].id, 'draft-id')
    assert.equal(calls[0].data.tags, '压力,生活')
    fail = true
    view.formData.content = '<p>失败时保留</p>'
    await view.handleSubmit()
    assert.equal(view.loading.value, false)
    assert.equal(view.formData.content, '<p>失败时保留</p>')
    assert.equal(calls.length, 1)
    props.article = null
    view.watchers[0](null)
    await tick()
    assert.equal(view.isEdit.value, false)
    assert.equal(view.formData.content, '')
    view.formData.title = '新文章'
    view.formData.content = '<p>新正文</p>'
    await view.handleSubmit()
    assert.equal(calls.at(-1).type, 'create')
})

test('unavailable article clears previous content and old detail responses cannot overwrite a new page', async () => {
    const props = { id: 'published-id' }
    const pending = []
    const view = component('views/articleDetail.vue', {
        defineProps: () => props,
        getKnowledgeDetail: id => new Promise((resolve, reject) => pending.push({ id, resolve, reject }))
    }, 'loadArticle, articleDetail, loading, loadError')
    const first = view.loadArticle()
    pending[0].resolve({ data: { id: 'published-id', content: '公开正文' } })
    await first
    assert.equal(view.articleDetail.value.content, '公开正文')
    props.id = 'offline-id'
    const hidden = view.loadArticle()
    assert.equal(view.articleDetail.value.content, undefined)
    pending[1].reject(Error('offline'))
    await hidden
    assert.equal(view.loadError.value, true)
    assert.equal(view.articleDetail.value.content, undefined)
    props.id = 'older'
    const older = view.loadArticle()
    props.id = 'newer'
    const newer = view.loadArticle()
    pending[3].resolve({ data: { id: 'newer', content: '新正文' } })
    await newer
    pending[2].resolve({ data: { id: 'older', content: '过期正文' } })
    await older
    assert.equal(view.articleDetail.value.id, 'newer')
})

test('admin editing opens only the requested successfully loaded detail', async () => {
    const pending = []
    const view = component('views/knowledge.vue', {
        getArticleDetail: id => new Promise((resolve, reject) => pending.push({ id, resolve, reject }))
    }, 'handleEdit, currentArticle, dialogVisible')
    const older = view.handleEdit({ id: 'older' })
    assert.equal(view.dialogVisible.value, false)
    const newer = view.handleEdit({ id: 'newer' })
    pending[1].resolve({ data: { id: 'newer' } })
    await newer
    pending[0].resolve({ data: { id: 'older' } })
    await older
    assert.equal(view.currentArticle.value.id, 'newer')
    const missing = view.handleEdit({ id: 'missing' })
    pending[2].reject(Error('deleted'))
    await missing
    assert.equal(view.dialogVisible.value, false)
    assert.equal(view.currentArticle.value, null)
})

test('admin article reads use protected preview routes and updates use the explicit target id', () => {
    const calls = []
    const context = { request: {
        get: (...args) => calls.push(args), put: (...args) => calls.push(args)
    } }
    vm.createContext(context)
    const script = fs.readFileSync(new URL('../src/api/admin.js', import.meta.url), 'utf8')
        .replace(/^import .*$/gm, '').replace(/^export /gm, '')
    vm.runInContext(`${script}\narticlePage({status:'0'}); getArticleDetail('draft-id'); updateArticle('correct-id', {id:'wrong-id'});`, context)
    assert.equal(calls[0][0], '/knowledge/admin/article/page')
    assert.equal(calls[1][0], '/knowledge/admin/article/draft-id')
    assert.equal(calls[2][0], '/knowledge/article/correct-id')
})

test('cover preview keeps saved upload paths and resets failed preview after a new upload', async () => {
    const props = { modelValue: true, article: null, categories: [{ value: 1 }] }
    const view = component('components/ArticleDialog.vue', {
        defineProps: () => props, defineEmits: () => () => {}, crypto: { randomUUID: () => 'business-id' },
        uploadFile: async () => ({ data: { filePath: '/uploads/new.png' } })
    }, 'imgUrl, previewError, handleUploadRequest, handleRemove, formData, setPreview')
    view.watchers[0]({ id: 'article', coverImage: '/uploads/existing.png' })
    await tick()
    assert.equal(view.imgUrl.value, '/uploads/existing.png')
    view.previewError.value = true
    await view.handleUploadRequest({ file: {} })
    assert.equal(view.imgUrl.value, '/uploads/new.png')
    assert.equal(view.formData.coverImage, '/uploads/new.png')
    assert.equal(view.previewError.value, false)
    view.setPreview('https://cdn.example.com/cover.png')
    assert.equal(view.imgUrl.value, 'https://cdn.example.com/cover.png')
    view.handleRemove()
    assert.equal(view.imgUrl.value, '')
    assert.equal(view.formData.coverImage, '')
})

test('knowledge list uses same-origin paths and local placeholder without an old server dependency', () => {
    const view = component('views/frontendKnowledge.vue', { useRouter: () => ({ push() {} }) }, 'getImage, iconUrl')
    assert.equal(view.getImage('/uploads/cover.png'), '/uploads/cover.png')
    assert.equal(view.getImage('https://cdn.example.com/cover.png'), 'https://cdn.example.com/cover.png')
    assert.equal(view.getImage(''), view.iconUrl)
    assert.equal(view.getImage('javascript:invalid'), view.iconUrl)
})

test('pending cover upload blocks saves and duplicate uploads; closing ignores a late result', async () => {
    let finish
    let uploads = 0
    let saves = 0
    const props = { modelValue: true, article: {id:'article-id'}, categories: [] }
    const view = component('components/ArticleDialog.vue', {
        defineProps: () => props, defineEmits: () => () => {},
        uploadFile: (file, info) => { uploads++; assert.equal(info.id, 'article-id'); return new Promise(resolve => { finish = resolve }) },
        updateArticle: async () => { saves++ }
    }, 'handleUploadRequest, handleSubmit, handleClose, formRef, formData, uploading, imgUrl')
    view.formRef.value = {validate: async () => true, resetFields() {}}
    view.formData.content = '<p>正文</p>'
    const upload = view.handleUploadRequest({file:{}})
    assert.equal(view.uploading.value, true)
    await view.handleSubmit()
    assert.equal(saves, 0)
    await assert.rejects(view.handleUploadRequest({file:{}}))
    assert.equal(uploads, 1)
    view.handleClose()
    finish({data:{filePath:'/uploads/late.png'}})
    await upload
    assert.equal(view.uploading.value, false)
    assert.equal(view.formData.coverImage, '')
    assert.equal(view.imgUrl.value, '')
})

test('failed cover upload preserves the previous cover and permits retry', async () => {
    const props = {modelValue:true, article:{id:'article-id'}, categories:[]}
    let fail = true
    const view = component('components/ArticleDialog.vue', {
        defineProps: () => props, defineEmits: () => () => {},
        uploadFile: async () => { if (fail) throw Error('failed'); return {data:{filePath:'/uploads/retry.png'}} }
    }, 'handleUploadRequest, formData, setPreview, imgUrl, uploading')
    view.formData.coverImage = '/uploads/previous.png'
    view.setPreview(view.formData.coverImage)
    await assert.rejects(view.handleUploadRequest({file:{}}))
    assert.equal(view.uploading.value, false)
    assert.equal(view.formData.coverImage, '/uploads/previous.png')
    assert.equal(view.imgUrl.value, '/uploads/previous.png')
    fail = false
    await view.handleUploadRequest({file:{}})
    assert.equal(view.formData.coverImage, '/uploads/retry.png')
})

test('admin article pagination preserves submitted filters and saving keeps them', async () => {
    const calls=[]
    const view=component('views/knowledge.vue',{
        articlePage:async params=>{calls.push({...params});return {data:{records:[{id:'one'}],total:20}}}
    },'handleSearch,handleChange,handleSuccess,pagintion')
    const filters={title:'  睡眠  ',categoryId:3,status:'0'}
    await view.handleSearch(filters)
    filters.title='尚未提交'
    await view.handleChange(2)
    assert.deepEqual(calls[1],{currentPage:2,pageSize:10,title:'睡眠',categoryId:3,status:'0'})
    await view.handleSuccess()
    assert.deepEqual(calls[2],calls[1])
    await view.handleSearch({})
    assert.deepEqual(calls[3],{currentPage:1,pageSize:10})
})

test('admin article list rejects stale responses and retries the current query after failure',async()=>{
    const pending=[],calls=[]
    const view=component('views/knowledge.vue',{
        articlePage:params=>{calls.push({...params});return new Promise((resolve,reject)=>pending.push({resolve,reject}))}
    },'handleSearch,loadArticles,tableData,listLoading,listError')
    const old=view.handleSearch({status:'0'}), newer=view.handleSearch({status:'2'})
    pending[0].resolve({data:{records:[{id:'stale'}],total:1}})
    await old
    assert.equal(view.listLoading.value,true)
    assert.equal(view.tableData.value.length,0)
    pending[1].reject(Error('failed'))
    await newer
    assert.equal(view.listError.value,true)
    const retry=view.loadArticles()
    assert.equal(calls[2].status,'2')
    pending[2].resolve({data:{records:[{id:'latest'}],total:1}})
    await retry
    assert.equal(view.tableData.value[0].id,'latest')
    assert.equal(view.listError.value,false)
})

test('publish, unpublish and delete preserve filters and return to a valid page',async()=>{
    for(const [action,status,next] of [['handlePublish',0,'1'],['handleUnpublish',1,'2'],['handleDelete',2,null]]) {
        let total=11
        const requests=[],writes=[]
        const view=component('views/knowledge.vue',{
            ElMessageBox:{confirm:async()=>{}},
            articlePage:async params=>{requests.push({...params});return {data:{records:[],total}}},
            changeArticleStatus:async(id,body)=>{writes.push({id,status:body.status});total--},
            deleteArticle:async id=>{writes.push({id});total--}
        },'handleSearch,handlePublish,handleUnpublish,handleDelete,pagintion,busyId')
        await view.handleSearch({title:'标题',status:String(status),categoryId:3})
        view.pagintion.currentPage=2
        await view[action]({id:'target',title:'标题',status})
        assert.equal(writes.length,1)
        assert.equal(writes[0].id,'target')
        if(next) assert.equal(writes[0].status,next)
        assert.deepEqual(requests.map(item=>item.currentPage),[1,2,1])
        assert.ok(requests.every(item=>item.title==='标题'&&item.status===String(status)&&item.categoryId===3))
        assert.equal(view.busyId.value,null)
    }
})

test('article actions do not run twice or clear rows on cancellation or write failure',async()=>{
    let confirm, count=0
    const view=component('views/knowledge.vue',{
        ElMessageBox:{confirm:()=>new Promise(resolve=>{confirm=resolve})},
        changeArticleStatus:async()=>{count++;throw Error('failed')}
    },'handlePublish,handleDelete,busyId,tableData')
    view.tableData.value=[{id:'original'}]
    const pending=view.handlePublish({id:'target',title:'文章',status:0})
    await view.handlePublish({id:'target',title:'文章',status:0})
    assert.equal(view.busyId.value,'target')
    confirm()
    await pending
    assert.equal(count,1)
    assert.equal(view.tableData.value[0].id,'original')
    assert.equal(view.busyId.value,null)
    await view.handleDelete({id:'published',status:1})
    const cancelled=component('views/knowledge.vue',{
        ElMessageBox:{confirm:async()=>{throw Error('cancel')}},deleteArticle:async()=>assert.fail('cancelled')
    },'handleDelete,busyId')
    await cancelled.handleDelete({id:'draft',status:0})
    assert.equal(cancelled.busyId.value,null)
})

test('category failures do not prevent list loading and category retry refreshes search choices',async()=>{
    let fail=true
    const view=component('views/knowledge.vue',{
        categoryTree:async()=>{if(fail)throw Error('failed');return {data:[{id:3,categoryName:'睡眠'}]}},
        articlePage:async()=>({data:{records:[{id:'article'}],total:1}})
    },'loadCategories,loadArticles,categoryError,formItem,tableData,categoryMap')
    await Promise.all([view.loadCategories(),view.loadArticles()])
    assert.equal(view.categoryError.value,true)
    assert.equal(view.tableData.value[0].id,'article')
    fail=false
    await view.loadCategories()
    assert.equal(view.categoryError.value,false)
    assert.equal(view.formItem[1].options[0].value,3)
    assert.equal(view.categoryMap[3],'睡眠')
})
