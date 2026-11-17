import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
const tick=async()=>{await Promise.resolve();await Promise.resolve()}
const setup=(overrides={})=>{
    let unmount
    const writes=[],messages=[]
    const context={ref:value=>({value}),reactive:value=>value,URL,
        dayjs:()=>({format:()=> '2026-11-17'}),onMounted(){},onBeforeUnmount:fn=>{unmount=fn},
        ElMessage:{success:value=>messages.push(value),error(){}},ElMessageBox:{confirm:async()=>{}},
        getMyEmotionDiaries:async()=>({data:{records:[],total:0}}),
        getMyEmotionDiary:async id=>({data:{id,diaryDate:'2026-11-15',moodScore:6,diaryContent:'原有记录'}}),
        addEmotionDiary:async data=>writes.push({type:'create',data}),
        updateMyEmotionDiary:async(id,data)=>writes.push({type:'update',id,data}),deleteMyEmotionDiary:async()=>{},...overrides}
    const source=fs.readFileSync(new URL('../src/views/emotionalDiary.vue',import.meta.url),'utf8')
        .split('<script setup>')[1].split('</script>')[0].replace(/^\s*import .*$/gm,'').replaceAll('import.meta.url',"'file:///component.vue'")
    vm.createContext(context)
    vm.runInContext(`${source}\nglobalThis.view={newDiary,resetEditor,openDiary,removeDiary,selectEmotion,submit,diaryForm,editingId,saving,operationLoading,detail,detailVisible,trendRef,loadDiaries,diaries}`,context)
    return {...context.view,writes,messages,unmount:()=>unmount()}
}

test('create and edit save only the intended record and normalize optional numeric values',async()=>{
    const view=setup()
    view.diaryForm.moodScore=7
    view.diaryForm.sleepQuality='4'
    view.diaryForm.stressLevel=''
    await view.submit()
    assert.equal(view.writes[0].type,'create')
    assert.equal(view.writes[0].data.sleepQuality,4)
    assert.equal(view.writes[0].data.stressLevel,null)
    await view.openDiary(11,true)
    view.diaryForm.diaryContent='修改内容'
    await view.submit()
    assert.equal(view.writes[1].type,'update')
    assert.equal(view.writes[1].id,11)
    assert.equal(view.writes[1].data.diaryContent,'修改内容')
    assert.equal(view.editingId.value,null)
    assert.equal(view.saving.value,false)
})

test('pending save blocks reset, new diary, emotion selection, duplicate saves and record switching',async()=>{
    const pending=deferred()
    let writes=0
    const view=setup({updateMyEmotionDiary:()=>{writes++;return pending.promise}})
    await view.openDiary(11,true)
    view.diaryForm.dominantEmotion='平静'
    const saving=view.submit()
    view.resetEditor();view.newDiary();view.selectEmotion('焦虑')
    await view.submit();await view.openDiary(12,true)
    assert.equal(writes,1)
    assert.equal(view.editingId.value,11)
    assert.equal(view.diaryForm.dominantEmotion,'平静')
    pending.resolve()
    await saving
    assert.equal(view.editingId.value,null)
})

test('failed save preserves input and editing target for retry',async()=>{
    let fail=true
    const view=setup({updateMyEmotionDiary:async()=>{if(fail)throw Error('failed')}})
    await view.openDiary(11,true)
    view.diaryForm.diaryContent='保留修改'
    await view.submit()
    assert.equal(view.editingId.value,11)
    assert.equal(view.diaryForm.diaryContent,'保留修改')
    assert.equal(view.saving.value,false)
    fail=false
    await view.submit()
    assert.equal(view.editingId.value,null)
})

test('opening a record locks editor actions and late responses after unmount cannot fill the form',async()=>{
    const pending=deferred()
    const view=setup({getMyEmotionDiary:()=>pending.promise})
    view.diaryForm.diaryContent='当前草稿'
    const opening=view.openDiary(11,true)
    view.resetEditor();view.newDiary();view.selectEmotion('焦虑')
    assert.equal(view.diaryForm.diaryContent,'当前草稿')
    view.unmount()
    pending.resolve({data:{id:11,moodScore:8,diaryContent:'迟到记录'}})
    await opening
    assert.equal(view.diaryForm.diaryContent,'当前草稿')
    assert.equal(view.editingId.value,null)
    assert.equal(view.detailVisible.value,false)
})

test('leaving during deletion confirmation prevents a subsequent delete request',async()=>{
    const pending=deferred()
    const view=setup({ElMessageBox:{confirm:()=>pending.promise},deleteMyEmotionDiary:async()=>assert.fail('must not delete')})
    const removing=view.removeDiary({id:11,diaryDate:'2026-11-15'})
    view.unmount()
    pending.resolve()
    await removing
})

test('late save completion does not reset the old form or refresh the detached trend',async()=>{
    const pending=deferred()
    const view=setup({addEmotionDiary:()=>pending.promise})
    view.trendRef.value={refresh:()=>assert.fail('detached trend')}
    view.diaryForm.moodScore=7
    view.diaryForm.diaryContent='已发送内容'
    const saving=view.submit()
    view.unmount()
    pending.resolve()
    await saving
    assert.equal(view.diaryForm.diaryContent,'已发送内容')
    assert.equal(view.messages.length,0)
})

test('pending list requests cannot update an unmounted page',async()=>{
    const pending=deferred()
    const view=setup({getMyEmotionDiaries:()=>pending.promise})
    const loading=view.loadDiaries()
    view.unmount()
    pending.resolve({data:{records:[{id:'late'}],total:1}})
    await loading
    assert.equal(view.diaries.value.length,0)
})
