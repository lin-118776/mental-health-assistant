import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveFileUrl } from '../src/utils/fileUrl.js'

test('uploaded paths are same-origin and do not depend on the deployment hostname', () => {
    assert.equal(resolveFileUrl('/uploads/2026/11/cover.png'), '/uploads/2026/11/cover.png')
    assert.equal(resolveFileUrl('uploads/cover.png'), '/uploads/cover.png')
    assert.equal(resolveFileUrl(' /uploads/cover.png '), '/uploads/cover.png')
})

test('absolute HTTP(S) image addresses and signed query strings are not prefixed twice', () => {
    assert.equal(resolveFileUrl('https://cdn.example.com/cover.png?version=1'), 'https://cdn.example.com/cover.png?version=1')
    assert.equal(resolveFileUrl('http://localhost:8080/uploads/cover.png'), 'http://localhost:8080/uploads/cover.png')
})

test('missing and unsupported file addresses produce no broken URL', () => {
    for (const value of [undefined, null, '', ' ', {}, 'https://', 'javascript:alert(1)', 'file:///tmp/a.png', '//example.com/a.png', '/uploads/a\nb.png', '\\example.com\\a.png']) {
        assert.equal(resolveFileUrl(value), '')
    }
})
