import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

const script = fs.readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replace('export default service', '')
const setup = () => {
    const state = { token: 'current', cleared: 0, errors: [], location: { pathname: '/profile', search: '?tab=info', hash: '#name' } }
    const context = { axios: { create: () => ({ interceptors: {
        request: { use: fn => { state.request = fn } },
        response: { use: (success, failure) => { state.success = success; state.failure = failure } }
    } }) }, ElMessage: { error: value => state.errors.push(value) },
        clearSession: () => { state.token = null; state.cleared++ },
        localStorage: { getItem: () => state.token }, window: { location: state.location } }
    vm.createContext(context)
    vm.runInContext(script, context)
    return state
}

test('login and register calls do not carry stale bearer credentials', () => {
    const s = setup()
    for (const url of ['/user/login', '/user/add', '/user/register']) {
        assert.equal(s.request({ url, headers: { Authorization: 'Bearer stale' } }).headers.Authorization, undefined)
    }
    assert.equal(s.request({ url: '/user/me' }).headers.Authorization, 'Bearer current')
})

test('authentication business errors always reject and preserve return location', async () => {
    const s = setup()
    await assert.rejects(s.success({ data: { code: '401', msg: 'expired' }, config: { url: '/user/me' } }))
    assert.equal(s.token, null)
    assert.equal(s.location.href, '/auth/login?redirect=%2Fprofile%3Ftab%3Dinfo%23name')
})

test('guard identity checks clear expired state without triggering a competing browser redirect', async () => {
    const s = setup()
    await assert.rejects(s.failure({ response: { status: 401 }, config: { url: '/user/me', skipAuthRedirect: true } }))
    assert.equal(s.token, null)
    assert.equal(s.location.href, undefined)
})

test('permission denied and network failures do not discard login state', async () => {
    const s = setup()
    await assert.rejects(s.failure({ response: { status: 403 }, config: {} }))
    await assert.rejects(s.failure({ config: {} }))
    assert.equal(s.token, 'current')
    assert.equal(s.cleared, 0)
})

test('old request cannot expire a newly logged in account', async () => {
    const s = setup()
    await assert.rejects(s.failure({ response: { status: 401 }, config: { headers: { Authorization: 'Bearer old' } } }))
    assert.equal(s.token, 'current')
    assert.equal(s.cleared, 0)
})

test('successful response remains compatible and login failures do not redirect', async () => {
    const s = setup()
    assert.equal(s.success({ data: { code: '200', data: 7 } }).data, 7)
    await assert.rejects(s.success({ data: { code: '6004', msg: 'disabled' }, config: { url: '/user/login' } }))
    assert.equal(s.location.href, undefined)
})
