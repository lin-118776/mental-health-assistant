import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const script = fs.readFileSync(new URL('../src/views/register.vue', import.meta.url), 'utf8')
    .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const setup = (api = async () => ({ code: '200', data: null })) => {
    const state = { calls: [], routes: [], messages: [] }
    const context = {
        ref: value => ({ value }), reactive: value => value, computed: getter => ({ get value() { return getter() } }), TextEncoder,
        useRoute: () => ({ query: { redirect: '/emotion-diary' } }),
        useRouter: () => ({ replace: async target => state.routes.push(target) }),
        ElMessage: { success: message => state.messages.push(message) },
        register: async data => { state.calls.push(data); return api(data) }
    }
    vm.createContext(context)
    vm.runInContext(`${script}\nglobalThis.test = {submitForm, formData, rules, submitting};`, context)
    const view = context.test
    Object.assign(view.formData, { username: ' user_name ', phone: ' 13800138000 ', nickname: ' 小明 ',
        email: ' ', password: ' new-pass ', confirmPassword: ' new-pass ' })
    return { ...view, state }
}

test('registration stops when validation returns false or rejects', async () => {
    const view = setup()
    await view.submitForm({ validate: async () => false })
    await view.submitForm({ validate: async () => { throw Error('invalid') } })
    assert.equal(view.state.calls.length, 0)
    assert.equal(view.submitting.value, false)
    assert.equal(view.formData.password, ' new-pass ')
})

test('successful empty response preserves intended destination and submits only registration fields', async () => {
    const view = setup()
    view.formData.userType = 2
    view.formData.status = 0
    await view.submitForm({ validate: async () => true })
    assert.equal(view.state.calls.length, 1)
    const data = view.state.calls[0]
    assert.equal(data.username, 'user_name')
    assert.equal(data.phone, '13800138000')
    assert.equal(data.nickname, '小明')
    assert.equal(data.email, null)
    assert.equal(data.password, ' new-pass ')
    assert.deepEqual(Object.keys(data).sort(), ['email', 'nickname', 'password', 'phone', 'username'])
    assert.equal(view.state.routes[0].path, '/auth/login')
    assert.equal(view.state.routes[0].query.redirect, '/emotion-diary')
    assert.equal(view.formData.password, '')
    assert.equal(view.formData.confirmPassword, '')
})

test('password confirmation and multibyte size are validated against current input', () => {
    const view = setup()
    const confirmation = view.rules.confirmPassword.find(rule => rule.validator).validator
    let failure
    confirmation({}, 'different', error => { failure = error })
    assert.equal(failure.message, '两次输入的密码不一致')
    confirmation({}, view.formData.password, error => { failure = error })
    assert.equal(failure, undefined)
    const size = view.rules.password.find(rule => rule.validator).validator
    size({}, '密'.repeat(25), error => { failure = error })
    assert.ok(failure)
    size({}, '密'.repeat(24), error => { failure = error })
    assert.equal(failure, undefined)
})

test('request failure keeps password and confirmation for correction without navigating', async () => {
    const view = setup(async () => { throw Error('duplicate') })
    await view.submitForm({ validate: async () => true })
    assert.equal(view.state.routes.length, 0)
    assert.equal(view.formData.password, ' new-pass ')
    assert.equal(view.formData.confirmPassword, ' new-pass ')
    assert.equal(view.submitting.value, false)
})

test('double submit makes only one registration request', async () => {
    let complete
    const view = setup(() => new Promise(resolve => { complete = resolve }))
    const first = view.submitForm({ validate: async () => true })
    await view.submitForm({ validate: async () => true })
    assert.equal(view.state.calls.length, 1)
    complete({ code: '200', data: null })
    await first
    assert.equal(view.state.routes.length, 1)
})
