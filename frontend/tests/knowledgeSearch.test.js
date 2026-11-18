import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { resolveFileUrl } from '../src/utils/fileUrl.js'

const deferred = () => {
    let resolve, reject
    const promise = new Promise((yes, no) => {resolve = yes; reject = no})
    return {promise, resolve, reject}
}
const setup = (overrides = {}) => {
    const calls = []
    let mounted, unmount
    const context = {
        ref:value => ({value}), reactive:value => value, URL, resolveFileUrl,
        onMounted:fn => {mounted = fn}, onBeforeUnmount:fn => {unmount = fn},
        useRouter:() => ({push() {}}),
        getKnowledgeList:async params => {calls.push({...params}); return {data:{records:[],total:0}}},
        getKnowledgeCategories:async () => ({data:[]}), ...overrides
    }
    const source = fs.readFileSync(new URL('../src/views/frontendKnowledge.vue',import.meta.url),'utf8')
        .split('<script setup>')[1].split('</script>')[0]
        .replace(/^\s*import .*$/gm,'').replaceAll('import.meta.url',"'file:///component.vue'")
    vm.createContext(context)
    vm.runInContext(`${source}\nglobalThis.view = {filters, pagination, articleList, loading, loadError, categories, categoriesLoading, categoriesError, recommendList, recommendLoading, recommendError, getPageList, handleSearch, resetSearch, handleChange, loadCategories, loadRecommendations,findSources,sourceQuery,sources,sourceError,sourceLoading}`,context)
    return {...context.view,calls,mount:() => mounted(), unmount:() => unmount()}
}

test('search trims the title, applies category and sort, and starts on page one', async () => {
    const view = setup()
    view.pagination.currentPage = 4
    Object.assign(view.filters,{title:'  睡眠  ', categoryId:2, sortField:'readCount'})
    await view.handleSearch()
    assert.deepEqual(view.calls[0],{title:'睡眠',categoryId:2,sortField:'readCount',sortDirection:'desc',page:1,size:10})
    assert.equal(view.pagination.currentPage,1)
})

test('pagination preserves submitted filters instead of unsubmitted edits; reset removes filters', async () => {
    const view = setup()
    Object.assign(view.filters,{title:'压力',categoryId:3})
    await view.handleSearch()
    Object.assign(view.filters,{title:'尚未搜索',categoryId:4,sortField:'readCount'})
    await view.handleChange(2)
    assert.equal(view.calls[1].title,'压力')
    assert.equal(view.calls[1].categoryId,3)
    assert.equal(view.calls[1].page,2)
    assert.equal(view.calls[1].sortField,'publishedAt')
    await view.handleChange(2)
    assert.equal(view.calls.length,2)
    await view.resetSearch()
    assert.deepEqual(view.calls[2],{sortField:'publishedAt',sortDirection:'desc',page:1,size:10})
    assert.equal(view.filters.title,'')
    assert.equal(view.filters.categoryId,undefined)
    assert.equal(view.pagination.currentPage,1)
})

test('rapid searches discard stale results and stale failures without unlocking the current request', async () => {
    const pending = []
    const view = setup({getKnowledgeList:() => {const job=deferred(); pending.push(job); return job.promise}})
    const older = view.handleSearch()
    view.filters.title = '新搜索'
    const newer = view.handleSearch()
    pending[0].reject(Error('old error'))
    await older
    assert.equal(view.loading.value,true)
    assert.equal(view.loadError.value,false)
    pending[1].resolve({data:{records:[{id:'new'}],total:1}})
    await newer
    assert.equal(view.articleList.value[0].id,'new')
    const first = view.handleSearch(), second = view.handleSearch()
    pending[3].resolve({data:{records:[{id:'latest'}],total:1}})
    await second
    pending[2].resolve({data:{records:[{id:'stale'}],total:99}})
    await first
    assert.equal(view.articleList.value[0].id,'latest')
    assert.equal(view.pagination.total,1)
})

test('loading clears old articles but keeps pagination stable; errors and empty results are distinct and retryable', async () => {
    let pending = deferred()
    const view = setup({getKnowledgeList:() => pending.promise})
    view.articleList.value = [{id:'old'}]
    view.pagination.total = 30
    view.pagination.currentPage = 2
    const request = view.getPageList()
    assert.equal(view.loading.value,true)
    assert.equal(view.articleList.value.length,0)
    assert.equal(view.pagination.total,30)
    pending.reject(Error('failed'))
    await request
    assert.equal(view.loading.value,false)
    assert.equal(view.loadError.value,true)
    assert.equal(view.pagination.total,30)
    pending = deferred()
    const retry = view.getPageList()
    pending.resolve({data:{records:[],total:0}})
    await retry
    assert.equal(view.loadError.value,false)
    assert.equal(view.articleList.value.length,0)
})

test('categories and recommendations load independently and retry without changing the article query', async () => {
    let fail = true
    const view = setup({getKnowledgeCategories:async () => {if(fail) throw Error('failed'); return {data:[{id:7,categoryName:'睡眠'}]}}})
    await view.loadCategories()
    assert.equal(view.categoriesError.value,true)
    assert.equal(view.categoriesLoading.value,false)
    fail = false
    await view.loadCategories()
    assert.equal(view.categoriesError.value,false)
    assert.equal(view.categories.value[0].id,7)
    Object.assign(view.filters,{title:'睡眠',categoryId:7})
    await view.handleSearch()
    await view.loadRecommendations()
    assert.deepEqual(view.calls[1],{sortField:'readCount',sortDirection:'desc',page:1,size:5})
    await view.handleChange(2)
    assert.equal(view.calls[2].categoryId,7)
    assert.equal(view.calls[2].title,'睡眠')
})

test('recommendation failure can be retried without marking the main list as failed', async () => {
    let fail = true
    const view = setup({getKnowledgeList:async () => {if(fail) throw Error('failed'); return {data:{records:[{id:'recommended'}]}}}})
    await view.loadRecommendations()
    assert.equal(view.recommendError.value,true)
    assert.equal(view.recommendLoading.value,false)
    assert.equal(view.loadError.value,false)
    fail = false
    await view.loadRecommendations()
    assert.equal(view.recommendError.value,false)
    assert.equal(view.recommendList.value[0].id,'recommended')
})

test('unmount prevents pending articles, categories and recommendations from changing state', async () => {
    const articles=deferred(), categories=deferred(), recommendations=deferred()
    let call = 0
    const view = setup({getKnowledgeList:() => ++call === 1 ? articles.promise : recommendations.promise,
        getKnowledgeCategories:() => categories.promise})
    const jobs = [view.getPageList(),view.loadCategories(),view.loadRecommendations()]
    view.unmount()
    articles.resolve({data:{records:[{id:'late'}],total:1}})
    categories.resolve({data:[{id:1}]})
    recommendations.resolve({data:{records:[{id:'late'}]}})
    await Promise.all(jobs)
    assert.equal(view.articleList.value.length,0)
    assert.equal(view.categories.value.length,0)
    assert.equal(view.recommendList.value.length,0)
})

test('public category and article API wrappers use reader endpoints', () => {
    const calls = []
    const context = {request:{get:(...args) => calls.push(args)}}
    vm.createContext(context)
    const source = fs.readFileSync(new URL('../src/api/frontend.js',import.meta.url),'utf8')
        .replace(/^import .*$/gm,'').replace(/^export /gm,'')
    vm.runInContext(`${source}\ngetKnowledgeCategories(); getKnowledgeList({title:'睡眠',categoryId:2,page:1,size:10})`,context)
    assert.equal(calls[0][0],'/knowledge/category/tree')
    assert.equal(calls[1][0],'/knowledge/article/page')
    assert.equal(calls[1][1].params.categoryId,2)
})

test('RAG source search shows actual fragments and ignores late responses',async()=>{
    const pending=[]
    const view=setup({searchSources:()=>{const job=deferred();pending.push(job);return job.promise}})
    view.sourceQuery.value='睡眠'
    const old=view.findSources()
    view.sourceQuery.value='压力'
    const latest=view.findSources()
    pending[1].resolve({data:[{documentId:'doc2',title:'压力调节',text:'参考正文',chunkIndex:0}]})
    await latest
    pending[0].resolve({data:[{documentId:'old'}]})
    await old
    assert.equal(view.sources.value[0].documentId,'doc2')
    assert.equal(view.sourceLoading.value,false)
    const failed=view.findSources()
    pending[2].reject(Error('failed'))
    await failed
    assert.equal(view.sources.value.length,0)
    assert.equal(view.sourceError.value,true)
})
