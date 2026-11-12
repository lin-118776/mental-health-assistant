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
        getEmotionalDiaryDetail:async id => ({data:{id,diaryContent:'最新内容'}}), ...overrides}
    const script = fs.readFileSync(new URL('../src/views/emotional.vue',import.meta.url),'utf8')
        .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
    vm.createContext(context)
    vm.runInContext(`${script}\nglobalThis.view={viewSessionDetail,closeDetail,currentDetail,aiData,analysisNotice,detailLoading,detailError,detailDialogVisable}`,context)
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
