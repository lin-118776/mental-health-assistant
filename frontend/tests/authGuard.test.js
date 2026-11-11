import test from 'node:test'
import assert from 'node:assert/strict'
import { createAuthGuard } from '../src/utils/authGuard.js'
import { safeReturnPath } from '../src/utils/authNavigation.js'

const ordinary = { id: 7, userType: 1, status: 1 }
const admin = { id: 8, userType: 2, status: 1 }
const resolve = value => {
    const path = value.split(/[?#]/)[0]
    const known = ['/', '/profile', '/knowledge', '/consultation', '/emotion-diary', '/back/dashboard', '/back/users', '/auth/login', '/auth/register'].includes(path)
    return { path, fullPath: value, matched: known ? [{}] : [], meta: { requiresAdmin: path.startsWith('/back/') } }
}
const route = (path, meta = {}, query = {}) => ({ ...resolve(path), meta: { ...resolve(path).meta, ...meta }, query })
const setup = ({ token = 'valid', user = ordinary, error } = {}) => {
    const state = { token, user: null, reads: 0, notices: [] }
    const guard = createAuthGuard({
        getToken: () => state.token,
        fetchCurrentUser: async () => { state.reads++; if (error) throw error; return user },
        setUser: value => { state.user = value },
        clearSession: () => { state.token = null; state.user = null },
        resolve,
        notify: value => state.notices.push(value)
    })
    return { guard, state }
}

test('anonymous protected routes preserve the requested path for login', async () => {
    for (const path of ['/profile', '/consultation', '/emotion-diary', '/knowledge', '/back/dashboard']) {
        const { guard, state } = setup({ token: null })
        assert.deepEqual(await guard(route(path, { requiresAuth: true })), {
            path: '/auth/login', query: { redirect: path }, replace: true
        })
        assert.equal(state.reads, 0)
    }
})

test('anonymous home and login pages remain accessible', async () => {
    const { guard } = setup({ token: null })
    assert.equal(await guard(route('/')), true)
    assert.equal(await guard(route('/auth/login')), true)
})

test('ordinary user is kept out of every backend page using server identity', async () => {
    const { guard, state } = setup()
    assert.equal(await guard(route('/back/users')), '/')
    assert.equal(state.user.userType, 1)
    assert.equal(state.notices.length, 1)
})

test('administrator can enter backend and identities refresh on navigation', async () => {
    const { guard, state } = setup({ user: admin })
    assert.equal(await guard(route('/back/dashboard')), true)
    assert.equal(await guard(route('/back/users')), true)
    assert.equal(state.reads, 2)
})

test('login and unknown-route redirects use the verified role', async () => {
    assert.equal(await setup().guard(route('/auth/login')), '/')
    assert.equal(await setup({ user: admin }).guard(route('/auth/login')), '/back/dashboard')
    assert.equal(await setup().guard(route('/missing')), '/')
    assert.equal(await setup({ user: admin }).guard(route('/missing')), '/back/dashboard')
})

test('already authenticated user can return to intended permitted route', async () => {
    assert.equal(await setup().guard(route('/auth/login', {}, { redirect: '/profile?tab=info' })), '/profile?tab=info')
    assert.equal(await setup().guard(route('/auth/login', {}, { redirect: '/back/users' })), '/')
})

test('expired and disabled credentials are cleared before login redirect', async () => {
    for (const error of [{ response: { status: 401 } }, { code: 'A0230' }, { code: '6004' }]) {
        const { guard, state } = setup({ error })
        assert.equal((await guard(route('/profile', { requiresAuth: true }))).path, '/auth/login')
        assert.equal(state.token, null)
    }
    const disabled = setup({ user: { ...ordinary, status: 0 } })
    assert.equal((await disabled.guard(route('/profile', { requiresAuth: true }))).path, '/auth/login')
})

test('temporary network failures preserve token and do not authorize protected navigation', async () => {
    const { guard, state } = setup({ error: Error('network') })
    assert.equal(await guard(route('/profile', { requiresAuth: true }), route('/')), false)
    assert.equal(await guard(route('/profile', { requiresAuth: true })), '/')
    assert.equal(state.token, 'valid')
    assert.equal(await guard(route('/auth/login')), true)
})

test('identity result from a token changed during validation is discarded', async () => {
    let token = 'old'
    let set = false
    const guard = createAuthGuard({ getToken: () => token, fetchCurrentUser: async () => { token = 'new'; return admin },
        setUser: () => { set = true }, clearSession() {}, resolve, notify() {} })
    assert.equal(await guard(route('/back/users')), false)
    assert.equal(set, false)
})

test('return paths reject external addresses, auth loops, unknown routes and role escalation', () => {
    for (const value of ['https://example.com', '//example.com', '/\\example.com', '/auth/login', '/missing', '/back/users', ['/', '/profile'], '/profile\n']) {
        assert.equal(safeReturnPath(value, ordinary, resolve), '/')
    }
    assert.equal(safeReturnPath('/profile?tab=info#name', ordinary, resolve), '/profile?tab=info#name')
    assert.equal(safeReturnPath('/back/users', admin, resolve), '/back/users')
})
