import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
const deferred=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject}}
const setup=(values={})=>{
    let unmount
    const context={ref:value=>({value}),reactive:value=>value,onMounted(){},onBeforeUnmount:fn=>{unmount=fn},
        ElMessage:{success(){},error(){}},ElMessageBox:{confirm:async()=>{}},
        listDocuments:async()=>({data:{records:[],total:0}}),createDocument:async()=>{},retryDocument:async()=>{},disableDocument:async()=>{},evictCategories:async()=>{},...values}
    const source=fs.readFileSync(new URL('../src/views/ragDocuments.vue',import.meta.url),'utf8')
        .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
    vm.createContext(context)
    vm.runInContext(`${source}\nglobalThis.view={form,create,busy,rows,load,page,total,error,retry,disable,clearCache}`,context)
    return {...context.view,unmount:()=>unmount()}
}
test('document creation preserves failed input and blocks duplicate submissions',async()=>{
    const pending=deferred();let calls=0
    const view=setup({createDocument:()=>{calls++;return pending.promise}})
    Object.assign(view.form,{title:'睡眠资料',content:'正文'})
    const request=view.create()
    await view.create()
    assert.equal(calls,1)
    pending.reject(Error('failed'));await request
    assert.equal(view.form.content,'正文')
    assert.equal(view.busy.value,false)
})
test('successful creation clears input only after saving and loads status list',async()=>{
    const writes=[]
    const view=setup({createDocument:async data=>writes.push(data),listDocuments:async()=>({data:{records:[{id:'doc',status:'QUEUED'}],total:1}})})
    Object.assign(view.form,{title:'  睡眠资料  ',content:'正文'})
    await view.create()
    assert.equal(writes[0].title,'睡眠资料')
    assert.equal(view.form.content,'')
    assert.equal(view.rows.value[0].status,'QUEUED')
})
test('cancelled disable does not write and leaving during confirmation prevents a later request',async()=>{
    const prompt=deferred()
    const view=setup({ElMessageBox:{confirm:()=>prompt.promise},disableDocument:async()=>assert.fail('must not write')})
    const disabling=view.disable({id:'doc'})
    view.unmount();prompt.resolve();await disabling
    const cancelled=setup({ElMessageBox:{confirm:async()=>{throw Error('cancel')}},disableDocument:async()=>assert.fail('must not write')})
    await cancelled.disable({id:'doc'})
    assert.equal(cancelled.busy.value,false)
})
test('newest document page wins and late results after unmount do not update the page',async()=>{
    const jobs=[]
    const view=setup({listDocuments:()=>{const job=deferred();jobs.push(job);return job.promise}})
    const first=view.load(),latest=view.load()
    jobs[1].resolve({data:{records:[{id:'new'}],total:1}});await latest
    jobs[0].resolve({data:{records:[{id:'old'}],total:2}});await first
    assert.equal(view.rows.value[0].id,'new')
    const pending=view.load();view.unmount();jobs[2].resolve({data:{records:[{id:'late'}],total:3}});await pending
    assert.equal(view.rows.value[0].id,'new')
})
