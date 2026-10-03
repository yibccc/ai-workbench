/* FIXTURE_ONLY: real React/HTTP decoding, synthetic transport, no DB/storage acceptance. */
import { chromium, expect } from '../../../frontend/node_modules/@playwright/test/index.mjs'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'

const id = 'd9500000-0000-4000-8000-000000000001'
const postId = 'd9500000-0000-4000-8000-000000000002'
const now = '2026-10-03T00:00:00Z'
const author = { id, nickname: '审阅夹具作者', bio: '' }
const own = { postId, type: 'BLOG', status: 'DRAFT', version: 1, currentPublished: null, moderation: null, attachments: [], draft: { type: 'BLOG', businessDate: null, title: '审阅夹具', summary: '', bodyMarkdown: '本地正文', attachmentIds: [], attachments: [], savedAt: now } }
const uploads = new Map()
const calls = []
const writes = []
const errors = []
let mode = 'UNKNOWN'
let failNextOwnerRead = false
const payload = request => {
  const text = request.postDataBuffer().toString('utf8')
  return { version: /name="expectedVersion"\r\n\r\n([^\r]+)/.exec(text)[1], key: /name="requestId"\r\n\r\n([^\r]+)/.exec(text)[1], fileName: /filename="([^"]+)"/.exec(text)[1], content: /Content-Type: text\/markdown\r\n\r\n([\s\S]*?)\r\n--/.exec(text)[1] }
}
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1040 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' })
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/*', async route => {
    const request = route.request(); const url = new URL(request.url()); url.pathname = url.pathname.toLowerCase()
    if (!url.pathname.startsWith('/api/')) return route.continue()
    let data; let status = 200
    if (url.pathname === '/api/auth/me') data = { id, username: 'fixture_check', role: 'ADMIN', enabled: true }
    else if (url.pathname === '/api/auth/csrf') data = { token: 'fixture-only-csrf' }
    else if (url.pathname === '/api/auth/activity') data = {}
    else if (url.pathname === '/api/projects') data = []
    else if (url.pathname === '/api/focus/current') data = null
    else if (url.pathname === '/api/focus/routines/fill-today') data = { date: '2026-10-03', created: [], blocked: [] }
    else if (url.pathname === '/api/me/community/profile') data = { ...author, version: 0 }
    else if (url.pathname === `/api/me/posts/${postId}`) {
      if (request.method() === 'PUT') {
        const value = request.postDataJSON(); writes.push(value)
        if (value.version !== own.version) { status = 409; data = { code: 'VERSION_CONFLICT', detail: '稿件版本已变化', currentVersion: own.version } }
        else { own.version++; own.draft = { ...own.draft, ...value, savedAt: now }; data = { postId, version: own.version, savedAt: now } }
      } else if (failNextOwnerRead) { failNextOwnerRead = false; await route.abort('failed'); return }
      else data = own
    } else if (url.pathname === `/api/me/posts/${postId}/attachments`) {
      const value = payload(request); calls.push(value)
      let receipt = uploads.get(value.key)
      if (!receipt) {
        assert.equal(Number(value.version), own.version)
        const attachment = { id: `d9500000-0000-4000-8000-${String(100 + uploads.size).padStart(12, '0')}`, kind: 'MD', fileName: value.fileName, contentType: 'text/markdown; charset=UTF-8', size: Buffer.byteLength(value.content), state: mode === 'ACTIVE' ? 'UPLOADING' : mode === 'FAILED' ? 'FAILED' : 'READY' }
        own.version++
        receipt = { attachment, version: own.version }; uploads.set(value.key, receipt)
        own.attachments.push(attachment)
        if (attachment.state !== 'FAILED') own.draft.attachmentIds.push(attachment.id)
        if (mode === 'UNKNOWN') {
          own.version++; own.draft.bodyMarkdown = '另一个窗口的新正文'
          await route.abort('failed'); return
        }
      } else if (receipt.attachment.state === 'UPLOADING') receipt.attachment.state = 'READY'
      if (receipt.attachment.state === 'FAILED') { status = 503; data = { code: 'STORAGE_UNAVAILABLE', detail: '确认上传失败', currentVersion: receipt.version } }
      else { data = receipt; if (mode === 'READ_FAILURE') failNextOwnerRead = true }
    } else { status = 404; data = { detail: '审阅夹具未配置路径' } }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
  })
  await page.goto(`http://127.0.0.1:15173/#/publishing/posts/${postId}`)
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('本地正文')
  const pick = (name = 'probe.md') => page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles({ name, mimeType: 'text/markdown', buffer: Buffer.from('# Probe\n') })
  await pick()
  let failed = page.locator('.file-row').filter({ hasText: '上传失败' })
  await expect(failed).toContainText('probe.md')
  await failed.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('[data-attachment-id]')).toHaveCount(1)
  await expect(failed).toHaveCount(0)
  assert.deepEqual(calls[1], calls[0])
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('本地正文')
  await expect(page.getByText('有未保存修改', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click()
  await expect(page.getByText('已保存私有草稿', { exact: true })).toBeVisible()
  assert.equal(writes.at(-1).version, 3)
  assert.equal(own.draft.bodyMarkdown, '本地正文')

  own.draft.summary = '外部新摘要'; own.version++
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click()
  await expect(page.getByRole('alert')).toContainText('稿件版本已变化')
  await expect(page.getByText('已保存私有草稿', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '读取当前版本，保留输入' }).click()
  await expect(page.getByText('有未保存修改', { exact: true })).toBeVisible()
  await expect(page.getByLabel('摘要（可选）')).toHaveValue('')
  assert.equal(own.draft.summary, '外部新摘要')
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click()
  await expect(page.getByText('已保存私有草稿', { exact: true })).toBeVisible()

  mode = 'ACTIVE'; const activeStart = calls.length
  await pick('active.md')
  await expect(page.getByRole('button', { name: '查询上传结果', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查询上传结果', exact: true }).click()
  await expect(page.locator('[data-attachment-id]')).toHaveCount(2)
  assert.deepEqual(calls[activeStart + 1], calls[activeStart])

  mode = 'FAILED'; const failedStart = calls.length
  await pick('failed.md')
  await expect(failed).toContainText('确认上传失败')
  mode = 'READY'
  await failed.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('[data-attachment-id]')).toHaveCount(3)
  assert.notEqual(calls[failedStart + 1].key, calls[failedStart].key)
  assert.equal(calls[failedStart + 1].content, calls[failedStart].content)

  mode = 'READ_FAILURE'; const readFailureStart = calls.length
  await pick('read-failure.md')
  await expect(failed).toContainText('read-failure.md')
  mode = 'READY'
  await failed.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('[data-attachment-id]')).toHaveCount(4)
  assert.equal(calls.length, readFailureStart + 1)

  await page.getByLabel('正文', { exact: true }).fill('管理员菜单不能丢失本地输入')
  await page.locator('.sidebar .account-trigger').click()
  await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`/publishing/posts/${postId}$`))
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('管理员菜单不能丢失本地输入')
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click()
  await expect(page.getByText('已保存私有草稿', { exact: true })).toBeVisible()
  await page.goto(`http://127.0.0.1:15173/#/publishing/posts/${postId.toUpperCase()}`)
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('管理员菜单不能丢失本地输入')
  await page.evaluate(() => { window.location.hash = '/publishing' })
  await page.getByRole('button', { name: '编辑公开资料' }).click()
  await expect(page.getByLabel('公开昵称')).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: '编辑公开资料' })).toBeFocused()
  assert.deepEqual(errors, [])
  const result = { kind: 'FIXTURE_ONLY', checks: ['unknown upload same complete payload', 'old receipt authoritative owner version', 'refresh updates dirty baseline without overwrite', 'active upload same key status query', 'confirmed failed upload new key', 'ready receipt owner reread retry without new upload', 'ADMIN menu respects dirty guard', 'accepted uppercase UUID matches owner identity', 'async profile initial focus and trigger restoration'], uploadRequests: calls.length, writes: writes.length, pageErrors: errors }
  await fs.writeFile('.trellis/tasks/10-03-community-ui/check-upload-probe.json', JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} finally { await browser.close() }
