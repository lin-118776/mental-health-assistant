import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const deferred = () => {
    let resolve, reject
    const promise = new Promise((yes,no) => {resolve=yes;reject=no})
    return {promise,resolve,reject}
}
const setup = (overrides={}) => {
    let unmount
    const context = {ref:value => ({value}),onMounted(){},onBeforeUnmount:fn => {unmount=fn},
        ElMessage:{error(){},success(){}}, ElMessageBox:{confirm:async()=>{}},
        getEmotionalDiary:async()=>({data:{records:[],total:0}}), deleteEmotional:async()=>{},
        getEmotionalDiaryDetail:async id => ({data:{id,diaryContent:'最新内容'}}), ...overrides}
    const script = fs.readFileSync(new URL('../src/views/emotional.vue',import.meta.url),'utf8')
        .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
    vm.createContext(context)
    vm.runInContext(`${script}\nglobalThis.view={viewSessionDetail,closeDetail,currentDetail,aiData,analysisNotice,detailLoading,detailError,detailDialogVisable,handleSearch,handlePageChange,loadPage,handleDelete,pagination,tableData,listLoading,listError,deletingId}`,context)
    return {...context.view,unmount:()=>unmount()}
}

test('admin detail fetches the latest content rather than displaying the old list row',async()=>{
    const view=setup()
    await view.viewSessionDetail({id:11,diaryContent:'旧内容',aiEmotionAnalysis:'{}'})
    assert.equal(view.currentDetail.value.diaryContent,'最新内容')
    assert.equal(view.detailLoading.value,false)
    assert.equal(view.aiData.value,null)
    assert.equal(view.analysisNotice.value,'尚无AI分析结果')
})

test('empty, corrupt and incomplete analysis do not invent emotion or risk results',async()=>{
    for(const raw of [null,'','{broken','null','[]','{}','{"isNegative":false}',JSON.stringify({primaryEmotion:'快乐',emotionScore:101,riskLevel:0,isNegative:false})]) {
        const view=setup({getEmotionalDiaryDetail:async()=>({data:{id:11,aiEmotionAnalysis:raw}})})
        await view.viewSessionDetail({id:11})
        assert.equal(view.aiData.value,null)
        assert.equal(view.analysisNotice.value,raw ? '已有分析数据无法读取' : '尚无AI分析结果')
    }
})

test('valid analysis preserves zero risk and false negativity while filtering malformed suggestions',async()=>{
    const analysis={primaryEmotion:'平静',emotionScore:80,riskLevel:0,isNegative:false,suggestion:12,
        riskDescription:'暂无明显风险',improvementSuggestions:['休息',{},null]}
    const view=setup({getEmotionalDiaryDetail:async()=>({data:{id:11,aiEmotionAnalysis:JSON.stringify(analysis)}})})
    await view.viewSessionDetail({id:11})
    assert.equal(view.aiData.value.riskLevel,0)
    assert.equal(view.aiData.value.isNegative,false)
    assert.equal(view.aiData.value.emotionScore,80)
    assert.equal(view.aiData.value.suggestion,'')
    assert.deepEqual(Array.from(view.aiData.value.improvementSuggestions),['休息'])
})

test('rapid selection ignores an old response and failure clears previous private detail',async()=>{
    const pending=[]
    const view=setup({getEmotionalDiaryDetail:()=>{const job=deferred();pending.push(job);return job.promise}})
    const old=view.viewSessionDetail({id:11}),latest=view.viewSessionDetail({id:12})
    pending[1].resolve({data:{id:12,diaryContent:'当前记录'}})
    await latest
    pending[0].resolve({data:{id:11,diaryContent:'过期记录'}})
    await old
    assert.equal(view.currentDetail.value.id,12)
    const missing=view.viewSessionDetail({id:13})
    assert.equal(view.currentDetail.value,null)
    pending[2].reject(Error('deleted'))
    await missing
    assert.equal(view.detailError.value,true)
    assert.equal(view.currentDetail.value,null)
    assert.equal(view.aiData.value,null)
    assert.equal(view.detailLoading.value,false)
})

test('failed detail can be retried, and closing or unmounting discards pending data',async()=>{
    let fail=true
    const view=setup({getEmotionalDiaryDetail:async()=>{if(fail)throw Error('failed');return {data:{id:11}}}})
    await view.viewSessionDetail({id:11})
    fail=false
    await view.viewSessionDetail({id:11})
    assert.equal(view.detailError.value,false)
    assert.equal(view.currentDetail.value.id,11)
    for(const action of ['closeDetail','unmount']) {
        const pending=deferred()
        const another=setup({getEmotionalDiaryDetail:()=>pending.promise})
        const request=another.viewSessionDetail({id:11})
        another[action]()
        pending.resolve({data:{id:11,diaryContent:'迟到'}})
        await request
        assert.equal(another.detailDialogVisable.value,false)
        assert.equal(another.currentDetail.value,null)
    }
})

test('admin list keeps a snapshot of filters across pages and resets page on a new search',async()=>{
    const calls=[]
    const view=setup({getEmotionalDiary:async params=>{calls.push({...params});return {data:{records:[{id:1}],total:25}}}})
    const form={userId:' 7 ',moodScreRange:'1-3'}
    view.pagination.value.currentPage=3
    await view.handleSearch(form)
    form.userId='8'
    await view.handlePageChange(2)
    assert.deepEqual(calls,[{page:1,size:10,userId:'7',moodScreRange:'1-3'},{page:2,size:10,userId:'7',moodScreRange:'1-3'}])
    await view.handleSearch({})
    assert.deepEqual(calls[2],{page:1,size:10})
})

test('invalid user IDs never silently broaden the list query',async()=>{
    let calls=0
    const view=setup({getEmotionalDiary:async()=>{calls++;return {data:{records:[],total:0}}}})
    for(const userId of ['-1','0','1.5','abc','9223372036854775808']) await view.handleSearch({userId})
    assert.equal(calls,0)
})

test('list ignores late responses, shows errors and retries with the submitted filters',async()=>{
    const pending=[],calls=[]
    const view=setup({getEmotionalDiary:params=>{calls.push({...params});const job=deferred();pending.push(job);return job.promise}})
    const old=view.handleSearch({userId:'7'}),newer=view.handleSearch({userId:'8'})
    pending[0].resolve({data:{records:[{id:'old'}],total:1}})
    await old
    assert.equal(view.listLoading.value,true)
    assert.equal(view.tableData.value.length,0)
    pending[1].reject(Error('failed'))
    await newer
    assert.equal(view.listError.value,true)
    const retry=view.loadPage()
    assert.equal(calls[2].userId,'8')
    pending[2].resolve({data:{records:[{id:'latest'}],total:1}})
    await retry
    assert.equal(view.listError.value,false)
    assert.equal(view.tableData.value[0].id,'latest')
})

test('deleting last filtered row moves to valid page without losing filters',async()=>{
    const calls=[]
    let total=11
    const view=setup({deleteEmotional:async()=>{total--},getEmotionalDiary:async params=>{
        calls.push({...params});return {data:{records:params.page===1?[{id:1}]:[],total}}
    }})
    await view.handleSearch({userId:'7',moodScreRange:'7-10'})
    view.pagination.value.currentPage=2
    await view.handleDelete({id:11})
    assert.deepEqual(calls.map(item=>item.page),[1,2,1])
    assert.ok(calls.every(item=>item.userId==='7'&&item.moodScreRange==='7-10'))
    assert.equal(view.pagination.value.currentPage,1)
    assert.equal(view.pagination.value.total,10)
    assert.equal(view.deletingId.value,null)
})

test('cancelled or failed deletion preserves rows; duplicate deletion is blocked',async()=>{
    let calls=0
    const pending=deferred()
    const view=setup({ElMessageBox:{confirm:()=>pending.promise},deleteEmotional:async()=>{calls++;throw Error('failed')}})
    view.tableData.value=[{id:1}]
    const first=view.handleDelete({id:1})
    await view.handleDelete({id:1})
    assert.equal(view.deletingId.value,1)
    pending.resolve()
    await first
    assert.equal(calls,1)
    assert.equal(view.tableData.value[0].id,1)
    assert.equal(view.deletingId.value,null)
    const cancelled=setup({ElMessageBox:{confirm:async()=>{throw Error('cancel')}},deleteEmotional:async()=>assert.fail('must not delete')})
    await cancelled.handleDelete({id:1})
    assert.equal(cancelled.deletingId.value,null)
})
