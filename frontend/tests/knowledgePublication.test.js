import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const ref = value => ({ value })
const computed = source => ({ get value() { return typeof source === 'function' ? source() : source.get() }, set value(value) { source.set(value) } })
const tick = async () => { await Promise.resolve(); await Promise.resolve() }
const component = (file, values, expose) => {
    const source = fs.readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8')
    const script = source.split('<script setup>')[1].split('</script>')[0]
        .replace(/^\s*import .*$/gm, '').replaceAll('import.meta.url', "'file:///component.vue'")
    const watchers = []
    const context = { ref, reactive: value => value, computed, URL, console,
        nextTick: fn => Promise.resolve().then(() => fn?.()), watch: (get, callback) => watchers.push(callback),
        onMounted() {}, onBeforeUnmount() {}, defineExpose() {}, ElMessage: { warning() {}, success() {} }, ...values }
    vm.createContext(context)
    vm.runInContext(`${script}\nglobalThis.test = {${expose}}`, context)
    return { ...context.test, watchers }
}

test('editing updates the existing article, new form creates, and failure preserves input for retry', async () => {
    const props = { modelValue: true, article: null, categories: [{ value: 1 }] }
    const calls = []
    let fail = false
    const view = component('components/ArticleDialog.vue', {
        defineProps: () => props, defineEmits: () => () => {}, fileBaseUrl: '',
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
