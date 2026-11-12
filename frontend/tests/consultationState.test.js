import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const deferred = () => {
    let resolve, reject
    const promise = new Promise((yes, no) => { resolve = yes; reject = no })
    return {promise, resolve, reject}
}
const tick = async () => { await Promise.resolve(); await Promise.resolve() }
const setup = (overrides = {}) => {
    let unmount
    const streams = []
    const errors = []
    let id = 0
    const context = {
        ref: value => ({value}), onMounted() {}, onBeforeUnmount: callback => {unmount = callback},
        URL, AbortController, crypto:{randomUUID:() => `message-${++id}`},
        localStorage:{getItem:() => 'token'}, ElMessage:{error:value => errors.push(value), success() {}},
        getSessionsList:async () => ({data:{records:[]}}),
        getSessionDetail:async () => ({data:[]}), getSessionEmotion:async () => ({data:{available:false}}),
        startSession:async () => ({data:1}), deleteSession:async () => {},
        fetchEventSource: (url, options) => {
            const pending = deferred()
            options.signal.addEventListener('abort', () => pending.resolve())
            streams.push({url, options, ...pending})
            return pending.promise
        }, ...overrides
    }
    const source = fs.readFileSync(new URL('../src/views/consultation.vue', import.meta.url),'utf8')
        .split('<script setup>')[1].split('</script>')[0]
        .replace(/^import .*$/gm,'').replaceAll('import.meta.url', "'file:///component.vue'")
    vm.createContext(context)
    vm.runInContext(`${source}\nglobalThis.view = {creatNewfrontendSession, selectSessionClick, sendMessage, handkeKeyDown, handleDeleteSession, currentSession, messages, userMessage, isAiTyping, historyLoading, historyError}`, context)
    context.view.creatNewfrontendSession()
    return {...context.view, streams, errors, unmount:() => unmount()}
}
const chunk = content => ({event:'', data:JSON.stringify({sessionId:1,content})})

test('new conversation clears previous messages and aborts the stream without late writes', async () => {
    const view = setup()
    view.userMessage.value = '你好'
    const sending = view.sendMessage()
    await tick()
    const stream = view.streams[0]
    stream.options.onmessage(chunk('部分回答'))
    assert.equal(view.messages.value[1].content, '部分回答')
    view.creatNewfrontendSession()
    assert.equal(stream.options.signal.aborted, true)
    assert.equal(view.messages.value.length, 0)
    stream.options.onmessage(chunk('过期回答'))
    await sending
    assert.equal(view.messages.value.length, 0)
    assert.equal(view.isAiTyping.value, false)
    assert.equal(view.errors.length, 0)
})

test('rapid selection keeps the latest history and blocks sending until it is loaded', async () => {
    const first = deferred(), second = deferred()
    const view = setup({getSessionDetail:id => id === 1 ? first.promise : second.promise})
    const older = view.selectSessionClick({id:1})
    const newer = view.selectSessionClick({id:2})
    view.userMessage.value = '等待记录'
    await view.sendMessage()
    assert.equal(view.streams.length, 0)
    second.resolve({data:[{id:'newer', content:'新会话'}]})
    await newer
    first.resolve({data:[{id:'older', content:'旧会话'}]})
    await older
    assert.equal(view.messages.value[0].id, 'newer')
    assert.equal(view.currentSession.value.sessionId, 2)
    assert.equal(view.historyLoading.value, false)
})

test('old stream completion cannot unlock or change a newer active response', async () => {
    const view = setup()
    view.userMessage.value = '旧问题'
    const older = view.sendMessage()
    await tick()
    const oldStream = view.streams[0]
    view.creatNewfrontendSession()
    view.userMessage.value = '新问题'
    const newer = view.sendMessage()
    await tick()
    await older
    oldStream.options.onmessage(chunk('旧回答'))
    oldStream.options.onmessage({event:'done',data:''})
    assert.equal(view.isAiTyping.value, true)
    assert.equal(view.messages.value[0].content, '新问题')
    assert.equal(view.messages.value[1].content, '')
    view.streams[1].options.onmessage(chunk('新回答'))
    view.streams[1].options.onmessage({event:'done',data:''})
    await newer
    assert.equal(view.messages.value[1].content, '新回答')
    assert.equal(view.isAiTyping.value, false)
})

test('failed history cannot receive new messages and can be retried', async () => {
    let fail = true
    const view = setup({getSessionDetail:async () => {if(fail) throw Error('failed'); return {data:[]}}})
    await view.selectSessionClick({id:1})
    view.userMessage.value = '不会误发'
    await view.sendMessage()
    assert.equal(view.historyError.value, true)
    assert.equal(view.streams.length, 0)
    fail = false
    await view.selectSessionClick({id:1})
    assert.equal(view.historyError.value, false)
})

test('session creation is locked before the first response and failure restores the input', async () => {
    const create = deferred()
    let count = 0
    const view = setup({startSession:() => {count++; return create.promise}})
    view.userMessage.value = '尚未发送'
    const sending = view.sendMessage()
    view.userMessage.value = '重复发送'
    await view.sendMessage()
    assert.equal(count, 1)
    create.reject(Error('failed'))
    await sending
    assert.equal(view.userMessage.value, '尚未发送')
    assert.equal(view.isAiTyping.value, false)
    assert.equal(view.messages.value.length, 0)
})

test('late session creation does not replace a newly selected conversation', async () => {
    const create = deferred()
    const view = setup({startSession:() => create.promise})
    view.userMessage.value = '旧问题'
    const sending = view.sendMessage()
    await view.selectSessionClick({id:2})
    create.resolve({data:1})
    await sending
    assert.equal(view.currentSession.value.sessionId, 2)
    assert.equal(view.messages.value.length, 0)
    assert.equal(view.streams.length, 0)
})

test('stream accepts a charset, appends chunks to its own message and ends on done', async () => {
    const view = setup()
    view.userMessage.value = '你好'
    const sending = view.sendMessage()
    await tick()
    const stream = view.streams[0]
    await stream.options.onopen({ok:true, headers:{get:() => 'text/event-stream;charset=UTF-8'}})
    assert.equal(stream.options.openWhenHidden, true)
    stream.options.onmessage(chunk('第一段'))
    stream.options.onmessage(chunk('第二段'))
    stream.options.onmessage({event:'done',data:''})
    stream.options.onmessage(chunk('迟到段'))
    await sending
    assert.equal(view.messages.value[1].content, '第一段第二段')
    assert.equal(view.isAiTyping.value, false)
    assert.equal(stream.options.signal.aborted, true)
})

test('stream failure keeps partial text, unlocks input and never requests automatic retry', async () => {
    const view = setup()
    view.userMessage.value = '你好'
    const sending = view.sendMessage()
    await tick()
    const stream = view.streams[0]
    stream.options.onmessage(chunk('已收到的回答'))
    assert.throws(() => stream.options.onerror(Error('network')), /network/)
    assert.throws(() => stream.options.onclose(), /中断/)
    stream.reject(Error('network'))
    await sending
    assert.match(view.messages.value[1].content, /^已收到的回答/)
    assert.match(view.messages.value[1].content, /回复中断/)
    assert.equal(view.isAiTyping.value, false)
    assert.equal(view.errors.length, 1)
})

test('invalid HTTP or non-SSE responses terminate instead of showing a stuck typing state', async () => {
    for (const response of [{ok:false,headers:{get:()=>'text/event-stream'}}, {ok:true,headers:{get:()=>'application/json'}}]) {
        const view = setup({fetchEventSource:async (url, options) => options.onopen(response)})
        view.userMessage.value = '你好'
        await view.sendMessage()
        assert.equal(view.isAiTyping.value, false)
        assert.match(view.messages.value[1].content, /回复失败/)
    }
})

test('deleting the active session clears it; deleting another or a failed deletion preserves it', async () => {
    let fail = false
    const view = setup({deleteSession:async () => {if(fail) throw Error('failed')}})
    await view.selectSessionClick({id:1})
    view.messages.value = [{id:1,content:'记录'}]
    await view.handleDeleteSession(2)
    assert.equal(view.currentSession.value.sessionId, 1)
    fail = true
    await view.handleDeleteSession(1)
    assert.equal(view.messages.value.length, 1)
    fail = false
    await view.handleDeleteSession(1)
    assert.equal(view.currentSession.value.status, 'TEMP')
    assert.equal(view.messages.value.length, 0)
})

test('unmount aborts active work and ignores pending history', async () => {
    const view = setup()
    view.userMessage.value = '你好'
    const sending = view.sendMessage()
    await tick()
    const stream = view.streams[0]
    view.unmount()
    assert.equal(stream.options.signal.aborted, true)
    stream.options.onmessage(chunk('迟到'))
    await sending
    assert.equal(view.messages.value.length, 0)
    const pending = deferred()
    const another = setup({getSessionDetail:() => pending.promise})
    const loading = another.selectSessionClick({id:1})
    another.unmount()
    pending.resolve({data:[{id:'late'}]})
    await loading
    assert.equal(another.messages.value.length, 0)
})

test('Shift+Enter and input method confirmation do not send; oversized input is guarded in code', async () => {
    const view = setup()
    view.userMessage.value = '输入中'
    for (const event of [{shiftKey:true}, {isComposing:true}, {keyCode:229}]) {
        view.handkeKeyDown({...event, preventDefault:() => assert.fail('should keep native input')})
    }
    assert.equal(view.streams.length, 0)
    assert.equal(view.userMessage.value, '输入中')
    view.userMessage.value = '字'.repeat(501)
    await view.sendMessage()
    assert.equal(view.streams.length, 0)
    assert.equal(view.userMessage.value.length, 501)
})
