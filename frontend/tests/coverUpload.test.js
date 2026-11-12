import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { coverFileError, MAX_COVER_BYTES } from '../src/utils/coverUpload.js'

test('cover validation accepts supported images including the exact size limit', () => {
    for (const [name, type] of [['cover.PNG', 'image/png'], ['cover.jpg', 'image/jpeg'], ['cover.jpeg', 'image/jpeg'], ['cover.png', ''], ['cover.jpg', 'application/octet-stream']]) {
        assert.equal(coverFileError({ name, type, size: MAX_COVER_BYTES }), '')
    }
})

test('cover validation rejects empty, oversized, unsupported and mismatched uploads', () => {
    for (const file of [null, {name:'a.png', size:0}, {name:'a.png', size:MAX_COVER_BYTES+1},
        {name:'a.gif', size:1, type:'image/gif'}, {name:'a.png', size:1, type:'text/html'},
        {name:'a.jpg', size:1, type:'image/png'}, {name:'png', size:1}]) {
        assert.ok(coverFileError(file))
    }
})

test('upload API sends cover metadata and lets the browser supply the multipart boundary', () => {
    const calls = []
    const context = { FormData: class { fields = []; append(...args) { this.fields.push(args) } },
        request: { post: (...args) => calls.push(args) } }
    vm.createContext(context)
    const source = fs.readFileSync(new URL('../src/api/admin.js', import.meta.url), 'utf8')
        .replace(/^import .*$/gm, '').replace(/^export /gm, '')
    vm.runInContext(`${source}\nuploadFile('image-bytes', {id:'article-id'})`, context)
    const [url, body, options] = calls[0]
    assert.equal(url, '/file/upload')
    assert.deepEqual(JSON.parse(JSON.stringify(body.fields)), [['file','image-bytes'], ['businessType','ARTICLE'], ['businessId','article-id'], ['businessField','cover']])
    assert.equal(options.timeout, 60000)
    assert.equal(options.headers, undefined)
})
