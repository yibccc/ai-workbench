import { expect, test as base, type APIRequestContext, type Page, type Request } from '@playwright/test'
import { createHash } from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import type { OwnerPost, PublishInput } from '../src/api/publishing'
import type { PostDetail } from '../src/api/community'
import { deflateSync } from 'node:zlib'

const apiBase = 'http://127.0.0.1:18080'
const admin = { username: 'e2e_admin', password: 'E2eOnly-Synthetic-9384!' }
const evidence = process.env.COMMUNITY_EVIDENCE_DIR ? path.resolve(process.env.COMMUNITY_EVIDENCE_DIR) : path.resolve('test-results/community-evidence')
const test = base.extend<{ adminApi: APIRequestContext }>({
  adminApi: async ({ playwright }, applyFixture) => {
    const setup = await playwright.request.newContext({ baseURL: apiBase })
    const { token } = await (await setup.get('/api/auth/csrf')).json() as { token: string }
    expect((await setup.post('/api/auth/login', { headers: { 'X-XSRF-TOKEN': token }, data: admin })).status()).toBe(200)
    const current = await (await setup.get('/api/auth/csrf')).json() as { token: string }
    const request = await playwright.request.newContext({ baseURL: apiBase, storageState: await setup.storageState(), extraHTTPHeaders: { 'X-XSRF-TOKEN': current.token } })
    await setup.dispose(); await applyFixture(request); await request.dispose()
  },
})
test.beforeEach(async ({ adminApi, page }) => { await page.setViewportSize({ width: 1440, height: 1040 }); expect((await adminApi.post('/api/e2e/reset')).status()).toBe(200); await fs.mkdir(evidence, { recursive: true }) })

async function account(request: APIRequestContext, suffix: string) {
  const value = { username: `community_${suffix}_${crypto.randomUUID().slice(0, 8)}`, password: 'Community-E2e-Only-9384!', role: 'USER' }
  const response = await request.post('/api/admin/users', { data: value }); expect(response.status()).toBe(201)
  return { ...value, id: (await response.json() as { id: string }).id }
}
async function signIn(page: Page, credentials = admin, target = '/#/community') {
  await page.goto(target); await page.getByLabel('用户名').fill(credentials.username); await page.getByLabel('密码', { exact: true }).fill(credentials.password)
  await page.getByRole('button', { name: '登录', exact: true }).click(); await expect(page.locator('.account-trigger:visible').first()).toBeVisible()
}
async function go(page: Page, target: string) { await page.evaluate(value => { window.location.hash = value }, target) }
async function save(page: Page) {
  const response = page.waitForResponse(value => /\/api\/me\/posts\/[0-9a-f-]+$/.test(new URL(value.url()).pathname) && value.request().method() === 'PUT')
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click(); expect((await response).status()).toBe(200)
  await expect(page.getByText('已保存私有草稿', { exact: true })).toBeVisible()
}
async function preview(page: Page) { await page.getByRole('button', { name: '预览并发布', exact: true }).first().click(); await expect(page.getByText('这是读者将看到的内容')).toBeVisible() }
async function publish(page: Page) {
  await preview(page); await page.locator('.check-confirm:visible input').check()
  const response = page.waitForResponse(value => value.url().endsWith('/publish') && value.request().method() === 'POST')
  await page.getByRole('button', { name: /确认发布(?:更新|到广场)/ }).filter({ visible: true }).click(); expect((await response).status()).toBe(200)
  const { postId } = await (await response).json() as { postId: string }
  await expect(page).toHaveURL(new RegExp(`/community/posts/${postId}$`))
  await expect(page.locator('.article-card')).toBeVisible()
  return postId
}
async function screenshot(page: Page, name: string) {
  if (name.includes('-desktop')) await page.setViewportSize({ width: 1440, height: 1040 })
  else if (name.includes('-mobile')) await page.setViewportSize({ width: 390, height: 844 })
  await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
  await expect(page.locator('.community-scroll [role="status"]')).toHaveCount(0)
  const measurement = await page.evaluate(() => ({ url: location.href, width: innerWidth, height: innerHeight, documentWidth: document.documentElement.scrollWidth, documentHeight: document.documentElement.scrollHeight, scrollTop: document.querySelector('.community-scroll')?.scrollTop ?? 0 }))
  await page.screenshot({ path: path.join(evidence, name), animations: 'disabled' })
  await fs.writeFile(path.join(evidence, name.replace(/\.png$/, '.json')), JSON.stringify({ kind: name.startsWith('fixture-') ? 'HTTP_RESPONSE_FIXTURE' : 'REAL_BROWSER', ...measurement }, null, 2))
}
async function geometry(page: Page, mobile = false) {
  const measured = await page.evaluate(() => ({ width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth, scrollHeight: document.documentElement.scrollHeight, scroll: document.querySelector('.community-scroll')?.getBoundingClientRect().height ?? 0, sidebar: document.querySelector('.sidebar')?.getBoundingClientRect().width ?? 0 }))
  expect(measured.scrollWidth).toBeLessThanOrEqual(measured.width); expect(measured.scrollHeight).toBeLessThanOrEqual(measured.height); expect(measured.scroll).toBeGreaterThan(0)
  if (!mobile) expect(measured.sidebar).toBe(measured.width <= 1190 ? 192 : 218)
}

function imageFixture() {
  const crc32 = (value: Buffer) => { let crc = 0xffffffff; for (const byte of value) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0) } return (crc ^ 0xffffffff) >>> 0 }
  const chunk = (name: string, data: Buffer) => { const type = Buffer.from(name); const length = Buffer.alloc(4); length.writeUInt32BE(data.length); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([type, data]))); return Buffer.concat([length, type, data, crc]) }
  const header = Buffer.alloc(13); header.writeUInt32BE(2, 0); header.writeUInt32BE(2, 4); header[8] = 8; header[9] = 2
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), chunk('IDAT', deflateSync(Buffer.from([0, 170, 155, 233, 103, 84, 206, 0, 103, 84, 206, 170, 155, 233]))), chunk('IEND', Buffer.alloc(0))])
}
function pdfFixture() {
  let value = '%PDF-1.4\n'; const offsets = [0]
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>', '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>']
  objects.forEach((body, index) => { offsets.push(Buffer.byteLength(value)); value += `${index + 1} 0 obj\n${body}\nendobj\n` })
  const xref = Buffer.byteLength(value); value += `xref\n0 4\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(value)
}

test('real BLOG manual save, latest preview publication, member deep-link return and safe Markdown', async ({ page, adminApi, browser }) => {
  const reader = await account(adminApi, 'reader')
  await signIn(page); await screenshot(page, '01-community-desktop.png'); await geometry(page)
  await go(page, '/publishing/new'); await screenshot(page, 'extra-compose-desktop.png')
  await page.getByRole('button', { name: '写一篇博客', exact: true }).click()
  let creations = 0; page.on('request', request => { if (new URL(request.url()).pathname === '/api/me/posts' && request.method() === 'POST') creations++ })
  await page.getByLabel('标题', { exact: true }).fill('真实博客')
  await page.getByLabel('摘要（可选）').fill('第一份摘要')
  await page.getByLabel('正文', { exact: true }).fill('## 方法\n\n只公开这段正文。\n\n<script>window.communityExecuted=true</script>\n\n![外链](https://example.invalid/tracking.png)\n\n[危险](javascript:alert(1))')
  await screenshot(page, '03-editor-desktop.png'); expect(creations).toBe(0)
  await preview(page); expect(creations).toBe(0); await expect(page.locator('.prose script, .prose img')).toHaveCount(0)
  await expect(page.locator('.prose a[href^="javascript:"]')).toHaveCount(0)
  await page.getByRole('button', { name: '返回修改', exact: true }).filter({ visible: true }).click()
  await save(page); expect(creations).toBe(1)
  const id = new URL(page.url()).hash.split('/')[3]
  expect((await adminApi.get(`/api/community/posts/${id}`)).status()).toBe(404)
  await page.reload(); await expect(page.getByLabel('摘要（可选）')).toHaveValue('第一份摘要')
  await page.getByLabel('摘要（可选）').fill('尚未手动保存的最新摘要')
  await preview(page); await expect(page.locator('.article-summary')).toHaveText('尚未手动保存的最新摘要'); await screenshot(page, '04-preview-desktop.png')
  await page.locator('.check-confirm:visible input').check(); await page.getByRole('button', { name: '确认发布到广场' }).filter({ visible: true }).click()
  await expect(page).toHaveURL(new RegExp(`/community/posts/${id}$`))
  await expect(page.locator('.article-summary')).toHaveText('尚未手动保存的最新摘要'); await screenshot(page, '05-detail-desktop.png')
  const detail = await (await adminApi.get(`/api/community/posts/${id}`)).json() as PostDetail
  expect(detail.revisionNo).toBe(1); expect(detail.summary).toBe('尚未手动保存的最新摘要')
  const readerContext = await browser.newContext({ viewport: { width: 1440, height: 1040 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' }); const readerPage = await readerContext.newPage()
  await readerPage.goto(`http://127.0.0.1:15173/#/community/posts/${id}`); await expect(readerPage.locator('.article-card')).toHaveCount(0); await expect(readerPage.getByLabel('用户名')).toBeFocused(); await expect(readerPage.getByLabel('密码', { exact: true })).toHaveAttribute('type', 'password'); await expect(readerPage.getByRole('button', { name: '显示密码', exact: true })).toBeVisible(); await screenshot(readerPage, '09-login-desktop.png')
  await readerPage.getByLabel('用户名').fill(reader.username); await readerPage.getByLabel('密码', { exact: true }).fill(reader.password); await readerPage.getByRole('button', { name: '登录', exact: true }).click()
  await expect(readerPage).toHaveURL(new RegExp(`/community/posts/${id}$`)); await expect(readerPage.locator('.article-summary')).toHaveText(detail.summary)
  await expect(readerPage.getByRole('link', { name: '编辑这篇内容' })).toHaveCount(0)
  await page.locator('.article-card .author-name').click(); await screenshot(page, '08-author-desktop.png')
  await go(page, '/publishing'); await expect(page.locator('.manage-row')).toHaveCount(1); await screenshot(page, '06-my-publications-desktop.png')
  await page.getByRole('button', { name: '撤回', exact: true }).click(); await screenshot(page, '07-withdraw-dialog-desktop.png'); await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '撤回', exact: true }).click(); await page.getByRole('button', { name: '确认撤回', exact: true }).click()
  await readerPage.reload(); await expect(readerPage.getByRole('heading', { name: '内容暂不可访问' })).toBeVisible(); await expect(readerPage.locator('.article-card')).toHaveCount(0)
  await readerContext.close()
})

test('source IDs survive two pages, defaults stay private, date guard cancels and composition is private', async ({ page, adminApi }) => {
  const created: string[] = []
  for (let index = 0; index < 7; index++) {
    const response = await adminApi.post('/api/records', { data: { content: `素材 ${index} 完整公开内容`, projectId: null, occurredAt: `2026-10-02T0${index}:00:00Z` } }); expect(response.status()).toBe(200); created.push((await response.json() as { id: string }).id)
  }
  await signIn(page, admin, '/#/publishing/sources'); await page.getByLabel('素材日期').fill('2026-10-02')
  await expect(page.locator('.source-row')).toHaveCount(5); await expect(page.locator('.source-row input:checked')).toHaveCount(0)
  await page.locator('.source-row input').first().check(); const firstId = await page.locator('.source-entry').first().getAttribute('data-source-id')
  const pageRequest = page.waitForRequest(request => request.url().includes('/sources/page?') && request.url().includes('page=1'))
  await page.getByRole('button', { name: '下一页', exact: true }).click(); expect(new URL((await pageRequest).url()).searchParams.get('size')).toBe('5')
  await expect(page.locator('.source-row')).toHaveCount(2); await page.locator('.source-row input').last().check(); const secondId = await page.locator('.source-entry').last().getAttribute('data-source-id')
  await expect(page.locator('.counter-em')).toHaveText('2'); await screenshot(page, '02-source-selection-desktop.png'); await page.setViewportSize({ width: 390, height: 844 }); await screenshot(page, '12-source-selection-mobile.png'); await page.setViewportSize({ width: 1440, height: 1040 })
  await page.getByLabel('素材日期').fill('2026-10-03'); await page.getByRole('button', { name: '取消', exact: true }).click(); await expect(page.getByLabel('素材日期')).toHaveValue('2026-10-02'); await expect(page.locator('.counter-em')).toHaveText('2')
  const generated = page.waitForResponse(response => response.url().endsWith('/share-drafts'))
  await page.getByRole('button', { name: '整理成分享草稿' }).click()
  const response = await generated; expect(response.status()).toBe(201)
  const payload = response.request().postDataJSON() as { selections: { recordId: string; fields: string[] }[]; includeFocus: boolean }
  expect(payload.selections.map(item => item.recordId)).toEqual([firstId, secondId]); expect(payload.includeFocus).toBe(false); expect(payload.selections.every(item => item.fields.join(',') === 'CONTENT')).toBe(true)
  expect(created).toContain(firstId); expect(created).toContain(secondId)
  const draft = await response.json() as OwnerPost; expect((await adminApi.get(`/api/community/posts/${draft.postId}`)).status()).toBe(404)
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue(draft.draft.bodyMarkdown)
  await publish(page); const detail = await (await adminApi.get(`/api/community/posts/${draft.postId}`)).json() as PostDetail; expect(detail.type).toBe('DAILY')
})

test('dirty editor cancel/save leave, concurrent save acknowledgement and explicit 409 retry preserve input', async ({ page, adminApi }) => {
  await signIn(page, admin, '/#/publishing/new/BLOG'); await page.getByLabel('标题', { exact: true }).fill('并发摘要'); await page.getByLabel('正文', { exact: true }).fill('保存期间仍可继续输入'); await page.getByLabel('摘要（可选）').fill('提交时的摘要')
  await save(page); const id = new URL(page.url()).hash.split('/')[3]
  let release!: () => void; const barrier = new Promise<void>(resolve => { release = resolve })
  await page.route(`**/api/me/posts/${id}`, async route => { if (route.request().method() !== 'PUT') return route.continue(); const response = await route.fetch(); await barrier; await route.fulfill({ response }) })
  await page.getByLabel('摘要（可选）').fill('第二份摘要'); await page.getByRole('button', { name: '保存草稿', exact: true }).first().click(); await page.getByLabel('摘要（可选）').fill('响应晚到后的更新摘要'); release()
  await expect(page.getByText('有未保存修改', { exact: true })).toBeVisible(); await expect(page.getByLabel('摘要（可选）')).toHaveValue('响应晚到后的更新摘要'); await page.unroute(`**/api/me/posts/${id}`)
  await page.locator('.sidebar .account-trigger').click(); await page.getByRole('menuitem', { name: '用户管理', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible(); await expect(page).toHaveURL(new RegExp(`/publishing/posts/${id}$`))
  await page.getByRole('button', { name: '继续编辑', exact: true }).click(); await expect(page.getByLabel('摘要（可选）')).toHaveValue('响应晚到后的更新摘要')
  await go(page, '/community'); await expect(page.getByRole('dialog')).toBeVisible(); await page.getByRole('button', { name: '继续编辑', exact: true }).click(); await expect(page).toHaveURL(new RegExp(`/publishing/posts/${id}$`))
  await go(page, '/community'); await page.getByRole('button', { name: '保存并离开', exact: true }).click(); await expect(page.getByRole('heading', { name: '让每一点进展，被看见' })).toBeVisible()
  await go(page, `/publishing/posts/${id.toUpperCase()}`); await expect(page.getByLabel('摘要（可选）')).toHaveValue('响应晚到后的更新摘要')
  const own = await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost
  expect((await adminApi.put(`/api/me/posts/${id}`, { data: { ...own.draft, version: own.version, summary: '另一个窗口保存的摘要' } })).status()).toBe(200)
  await page.getByRole('button', { name: '保存草稿', exact: true }).first().click(); await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByLabel('摘要（可选）')).toHaveValue('响应晚到后的更新摘要')
  await expect(page.getByText('已保存私有草稿', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '读取当前版本，保留输入' }).click()
  await expect(page.getByText('有未保存修改', { exact: true })).toBeVisible()
  await expect(page.getByLabel('摘要（可选）')).toHaveValue('响应晚到后的更新摘要')
  expect((await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost).draft.summary).toBe('另一个窗口保存的摘要')
  await page.getByLabel('摘要（可选）').fill('过期窗口仍保留摘要'); await save(page)
  expect((await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost).draft.summary).toBe('过期窗口仍保留摘要')
})

test('lost publish response repeats exactly the immutable payload and creates one revision', async ({ page, adminApi }) => {
  await signIn(page, admin, '/#/publishing/new/MOMENT'); await page.getByLabel('正文', { exact: true }).fill('动态发布幂等正文')
  let first: PublishInput | null = null; let attempts = 0
  await page.route('**/api/me/posts/*/publish', async route => { attempts++; const payload = route.request().postDataJSON() as PublishInput; if (!first) { first = payload; await route.fetch(); await route.abort('failed') } else { expect(payload).toEqual(first); await route.continue() } })
  await preview(page); await page.locator('.check-confirm:visible input').check(); await page.getByRole('button', { name: '确认发布到广场' }).filter({ visible: true }).click(); await expect(page.getByRole('alert')).toBeVisible()
  const repeated = page.waitForResponse(response => response.url().endsWith('/publish') && response.request().method() === 'POST')
  await page.getByRole('button', { name: '确认发布到广场' }).filter({ visible: true }).click(); const receipt = await repeated; expect(receipt.status()).toBe(200)
  const { postId: id } = await receipt.json() as { postId: string }; await expect(page).toHaveURL(new RegExp(`/community/posts/${id}$`)); await expect(page.locator('.article-card')).toBeVisible(); expect(attempts).toBe(2)
  const detail = await adminApi.get(`/api/community/posts/${id}`); expect(detail.status()).toBe(200); expect((await detail.json() as PostDetail).revisionNo).toBe(1)
})

function uploadPayload(request: Request) {
  const body = request.postDataBuffer()!
  const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(request.headers()['content-type'])
  expect(boundary).not.toBeNull()
  const marker = Buffer.from(`--${boundary![1] ?? boundary![2]}`)
  const values: Record<string, string> = {}
  let file: { name: string; contentType: string; size: number; sha256: string } | undefined
  let start = body.indexOf(marker)
  while (start >= 0) {
    const next = body.indexOf(marker, start + marker.length)
    if (next < 0) break
    const part = body.subarray(start + marker.length + 2, next - 2)
    const separator = part.indexOf(Buffer.from('\r\n\r\n'))
    if (separator >= 0) {
      const headers = part.subarray(0, separator).toString('utf8')
      const name = /name="([^"]+)"/.exec(headers)?.[1]
      const content = part.subarray(separator + 4)
      if (name === 'file') file = { name: /filename="([^"]+)"/.exec(headers)![1], contentType: /Content-Type: ([^\r\n]+)/i.exec(headers)![1], size: content.length, sha256: createHash('sha256').update(content).digest('hex') }
      else if (name) values[name] = content.toString('utf8')
    }
    start = next
  }
  return { expectedVersion: values.expectedVersion, requestId: values.requestId, file }
}

test('lost real upload acknowledgement replays its exact file/key/version without duplicate refs or quota', async ({ page, adminApi }) => {
  await signIn(page, admin, '/#/publishing/new/BLOG')
  await page.getByLabel('标题', { exact: true }).fill('上传响应丢失')
  await page.getByLabel('正文', { exact: true }).fill('本地正文必须保留')
  await save(page)
  const id = new URL(page.url()).hash.split('/')[3]
  const file = { name: 'same-name.md', mimeType: 'text/markdown', buffer: Buffer.from('# Identical name and bytes\n', 'utf8') }
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles(file)
  await expect(page.locator('.file-row').filter({ hasText: file.name })).toContainText('可用')
  const before = await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost
  let first: ReturnType<typeof uploadPayload> | undefined
  let receipt: { attachment: { id: string }; version: number } | undefined
  let attempts = 0
  await page.route(`**/api/me/posts/${id}/attachments`, async route => {
    attempts++
    const payload = uploadPayload(route.request())
    if (!first) {
      first = payload
      const response = await route.fetch(); expect(response.status()).toBe(201)
      receipt = await response.json() as typeof receipt
      const afterUpload = await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost
      expect((await adminApi.put(`/api/me/posts/${id}`, { data: { ...afterUpload.draft, version: afterUpload.version, bodyMarkdown: '另一个窗口的新正文' } })).status()).toBe(200)
      await route.abort('failed')
    } else {
      expect(payload).toEqual(first)
      await route.continue()
    }
  })
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles(file)
  const failed = page.locator('.file-row').filter({ hasText: '上传失败' })
  await expect(failed).toContainText(file.name)
  await failed.getByRole('button', { name: '重试', exact: true }).click()
  await expect(page.locator('.file-row').filter({ hasText: file.name })).toHaveCount(2)
  await expect(page.locator('.file-row').filter({ hasText: '上传失败' })).toHaveCount(0)
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('本地正文必须保留')
  await expect(page.getByText('有未保存修改', { exact: true })).toBeVisible()
  expect(attempts).toBe(2)
  const current = await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost
  expect(current.version).toBeGreaterThan(receipt!.version)
  expect(current.attachments).toHaveLength(2)
  expect(new Set(current.draft.attachmentIds)).toEqual(new Set([before.attachments[0].id, receipt!.attachment.id]))
  expect(current.draft.bodyMarkdown).toBe('另一个窗口的新正文')
  // Saving now must use the authoritative owner version, not the replayed old receipt.
  await save(page)
  const saved = await (await adminApi.get(`/api/me/posts/${id}`)).json() as OwnerPost
  expect(saved.draft.bodyMarkdown).toBe('本地正文必须保留')
  expect(saved.attachments).toHaveLength(2)
  expect(saved.draft.attachmentIds).toHaveLength(2)
})

test('real attachment downloads, Markdown not imported, image lightbox and immutable F1/F2 publication', async ({ page, adminApi, browser }) => {
  await signIn(page, admin, '/#/publishing/new/BLOG'); await page.getByLabel('标题', { exact: true }).fill('文件版本'); await page.getByLabel('正文', { exact: true }).fill('正文不会被 MD 文件替换')
  const f1 = Buffer.from('# F1 原始附件\nOnly downloaded, never imported.\n', 'utf8')
  const upload = page.waitForResponse(response => response.url().endsWith('/attachments') && response.request().method() === 'POST')
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles({ name: 'F1.md', mimeType: 'text/markdown', buffer: f1 }); expect((await upload).status()).toBe(201)
  await expect(page.getByLabel('正文', { exact: true })).toHaveValue('正文不会被 MD 文件替换')
  const id = await publish(page); const first = await (await adminApi.get(`/api/community/posts/${id}`)).json() as PostDetail
  const f1Id = first.attachments[0].id; const file = page.waitForEvent('download'); await page.getByRole('button', { name: '下载 F1.md', exact: true }).click(); const downloaded = await file
  expect(downloaded.suggestedFilename()).toBe('F1.md'); const downloadPath = await downloaded.path(); expect(downloadPath).not.toBeNull(); expect(createHash('sha256').update(await fs.readFile(downloadPath!)).digest('hex')).toBe(createHash('sha256').update(f1).digest('hex'))
  await go(page, `/publishing/posts/${id}`); await expect(page.getByLabel('正文', { exact: true })).toBeVisible(); await page.getByRole('button', { name: '移除 F1.md', exact: true }).click()
  const f2 = Buffer.from('# F2 替换附件\n', 'utf8'); const secondUpload = page.waitForResponse(response => response.url().endsWith('/attachments') && response.request().method() === 'POST')
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles({ name: 'F2.md', mimeType: 'text/markdown', buffer: f2 }); expect((await secondUpload).status()).toBe(201); await save(page)
  expect((await (await adminApi.get(`/api/community/posts/${id}`)).json() as PostDetail).attachments.map(item => item.id)).toEqual([f1Id])
  await publish(page); const second = await (await adminApi.get(`/api/community/posts/${id}`)).json() as PostDetail
  expect(second.revisionNo).toBe(2); expect(second.firstPublishedAt).toBe(first.firstPublishedAt); expect(second.attachments[0].fileName).toBe('F2.md'); expect((await adminApi.get(`/api/community/posts/${id}/attachments/${f1Id}`)).status()).toBe(404)
  const reader = await account(adminApi, 'files'); const context = await browser.newContext(); const readerPage = await context.newPage(); await signIn(readerPage, reader, `/#/community/posts/${id}`)
  const readerDownload = readerPage.waitForEvent('download'); await readerPage.getByRole('button', { name: '下载 F2.md', exact: true }).click(); expect((await readerDownload).suggestedFilename()).toBe('F2.md')
  await go(page, '/publishing'); await page.getByRole('button', { name: '撤回', exact: true }).click(); await page.getByRole('button', { name: '确认撤回', exact: true }).click(); await readerPage.reload(); await expect(readerPage.getByRole('heading', { name: '内容暂不可访问' })).toBeVisible(); await context.close()
})

test('mobile R2 scroll geometry, workbench draft retention and profile controls', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await signIn(page)
  await geometry(page, true); await screenshot(page, '11-community-mobile.png'); await page.getByRole('navigation', { name: '移动端导航' }).getByRole('link', { name: '发布', exact: true }).click(); await screenshot(page, 'extra-compose-mobile.png')
  await page.getByRole('button', { name: '写一篇博客', exact: true }).click(); await page.getByLabel('标题', { exact: true }).fill('移动端博客'); await page.getByLabel('摘要（可选）').fill('移动端摘要'); await page.getByLabel('正文', { exact: true }).fill('长正文\n\n'.repeat(100)); await screenshot(page, '13-editor-mobile.png')
  await page.getByRole('button', { name: '保存草稿', exact: true }).filter({ visible: true }).click(); await expect(page.getByText('已保存私有草稿')).toBeVisible()
  await preview(page); await expect(page.locator('.check-confirm:visible')).toHaveCount(1); await page.locator('.check-confirm:visible').scrollIntoViewIfNeeded(); await screenshot(page, 'extra-preview-mobile.png')
  await page.locator('.check-confirm:visible input').check(); await page.getByRole('button', { name: '确认发布到广场' }).filter({ visible: true }).click(); await expect(page).toHaveURL(/\/community\/posts\/[0-9a-f-]+$/); await expect(page.locator('.article-card')).toBeVisible(); await expect.poll(() => page.locator('.community-scroll').evaluate(element => element.scrollTop)).toBe(0); await screenshot(page, '15-detail-mobile.png')
  await page.getByRole('navigation', { name: '移动端导航' }).getByRole('link', { name: '我的发布', exact: true }).click(); await screenshot(page, '17-my-publications-mobile.png')
  await page.getByRole('button', { name: '编辑公开资料' }).click(); await expect(page.getByLabel('公开昵称')).toBeFocused(); await page.getByLabel('公开昵称').fill('移动昵称'); await page.getByLabel('简介', { exact: true }).fill('只公开简介'); await page.getByRole('button', { name: '保存公开资料' }).click(); await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('link', { name: '工作台', exact: true }).filter({ visible: true }).click(); await page.getByTestId('capture-content').fill('工作台保留的输入'); await go(page, '/community'); await go(page, 'records'); await expect(page.getByTestId('capture-content')).toHaveValue('工作台保留的输入')
})

test('real image and PDF rendering, upload failure recovery, download retry and mobile lightbox', async ({ page }) => {
  await signIn(page, admin, '/#/publishing/new/BLOG'); await page.getByLabel('标题', { exact: true }).fill('图片与文件'); await page.getByLabel('正文', { exact: true }).fill('输入在失败后保留')
  const invalid = page.waitForResponse(response => response.url().endsWith('/attachments') && response.request().method() === 'POST')
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles({ name: 'invalid.png', mimeType: 'image/png', buffer: Buffer.from('not a PNG') }); expect((await invalid).status()).toBe(400)
  await expect(page.locator('.file-row').filter({ hasText: 'invalid.png' })).toContainText('上传失败'); await page.locator('.attachments').scrollIntoViewIfNeeded(); await screenshot(page, '10-upload-error-desktop.png'); await expect(page.getByLabel('正文', { exact: true })).toHaveValue('输入在失败后保留')
  await page.getByRole('button', { name: '移除 invalid.png', exact: true }).click()
  const image = imageFixture(); const pdf = pdfFixture()
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles([{ name: 'image.png', mimeType: 'image/png', buffer: image }, { name: 'document.pdf', mimeType: 'application/pdf', buffer: pdf }])
  await expect(page.locator('.file-row').filter({ hasText: 'image.png' })).toContainText('可用'); await expect(page.locator('.file-row').filter({ hasText: 'document.pdf' })).toContainText('可用')
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.attachments').scrollIntoViewIfNeeded(); await screenshot(page, '14-attachments-mobile.png'); await page.setViewportSize({ width: 1440, height: 1040 }); await page.getByRole('button', { name: '插入', exact: true }).click(); await publish(page)
  await expect(page.locator('.article-image img')).toBeVisible(); await expect(page.locator('iframe, embed, object')).toHaveCount(0)
  const download = page.waitForEvent('download'); await page.getByRole('button', { name: '下载 document.pdf', exact: true }).click(); const result = await download; expect(createHash('sha256').update(await fs.readFile((await result.path())!)).digest('hex')).toBe(createHash('sha256').update(pdf).digest('hex'))
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.article-files').scrollIntoViewIfNeeded(); await screenshot(page, '16-downloads-mobile.png'); await page.getByRole('button', { name: '查看大图 image.png', exact: true }).click(); await expect(page.getByRole('dialog').locator('img')).toBeVisible(); await screenshot(page, 'extra-image-lightbox-mobile.png'); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0)
  let failed = false; await page.route('**/api/community/posts/*/attachments/*', async route => { if (!failed) { failed = true; await route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ detail: '下载暂不可用' }) }) } else await route.continue() })
  await page.getByRole('button', { name: '下载 document.pdf', exact: true }).click(); await expect(page.locator('.download-error')).toContainText('下载暂不可用'); await expect(page.locator('.prose')).toContainText('输入在失败后保留')
  const retry = page.waitForEvent('download'); await page.getByRole('button', { name: '下载 document.pdf', exact: true }).click(); expect((await retry).suggestedFilename()).toBe('document.pdf')
})

test('ADMIN hide requires reason, unavailable projection clears and author cannot republish', async ({ page, adminApi, browser }) => {
  const writer = await account(adminApi, 'writer'); const context = await browser.newContext(); const authorPage = await context.newPage(); await signIn(authorPage, writer, '/#/publishing/new/MOMENT'); await authorPage.getByLabel('正文', { exact: true }).fill('可下架的动态'); const id = await publish(authorPage)
  await signIn(page, admin, `/#/community/posts/${id}`); await page.getByRole('button', { name: '管理员下架' }).click(); await expect(page.getByRole('button', { name: '确认下架', exact: true })).toBeDisabled(); await page.getByLabel('下架理由').fill('真实 E2E 审计理由'); await page.getByRole('button', { name: '确认下架', exact: true }).click()
  await expect(page.getByRole('heading', { name: '内容暂不可访问' })).toBeVisible(); await expect(page.locator('.article-card')).toHaveCount(0)
  await go(authorPage, `/publishing/posts/${id}`); await authorPage.getByRole('button', { name: '预览并发布' }).first().click(); await authorPage.locator('.check-confirm:visible input').check(); await expect(authorPage.getByRole('button', { name: '确认发布到广场' }).filter({ visible: true })).toBeDisabled(); await expect(authorPage.getByText(/内容已下架，不能再次发布/).filter({ visible: true })).toBeVisible(); await context.close()
})

test('real expiry and A→B late private Blob/upload acknowledgements stay outside the next account', async ({ page, adminApi }) => {
  const nextAccount = await account(adminApi, 'epoch')
  await page.addInitScript(() => {
    const urls: string[] = []; Reflect.set(window, 'communityObjectUrls', urls)
    const original = URL.createObjectURL.bind(URL)
    URL.createObjectURL = value => { const url = original(value); urls.push(url); return url }
  })
  await signIn(page, admin, '/#/publishing/new/BLOG'); await page.getByLabel('标题', { exact: true }).fill('A 私有稿'); await page.getByLabel('正文', { exact: true }).fill('A 私有正文')
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles({ name: 'A-private.png', mimeType: 'image/png', buffer: imageFixture() }); await expect(page.locator('.file-row').filter({ hasText: 'A-private.png' })).toContainText('可用'); await page.getByRole('button', { name: '插入', exact: true }).click(); await save(page)
  const postId = new URL(page.url()).hash.split('/')[3]
  let seen!: () => void; let release!: () => void; const observed = new Promise<void>(resolve => { seen = resolve }); const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(`**/api/me/posts/${postId}/attachments/*`, async route => { const response = await route.fetch(); seen(); await gate; await route.fulfill({ response }).catch(() => undefined) })
  await preview(page); await observed
  const csrf = await (await page.context().request.get('/api/auth/csrf')).json() as { token: string }; expect((await page.context().request.post('/api/auth/logout', { headers: { 'X-XSRF-TOKEN': csrf.token } })).status()).toBe(200)
  await go(page, '/community'); await expect(page.getByRole('heading', { name: '登录后，继续阅读' })).toBeVisible(); await expect(page.locator('.article-card')).toHaveCount(0)
  await page.getByLabel('用户名').fill(nextAccount.username); await page.getByLabel('密码', { exact: true }).fill(nextAccount.password); await page.getByRole('button', { name: '登录', exact: true }).click(); release()
  await expect(page.getByRole('heading', { name: '让每一点进展，被看见' })).toBeVisible(); await expect.poll(() => page.evaluate(() => (Reflect.get(window, 'communityObjectUrls') as string[]).length)).toBe(0)
  await page.unroute(`**/api/me/posts/${postId}/attachments/*`)
  await go(page, '/publishing/new/BLOG'); await page.getByLabel('标题', { exact: true }).fill('B 私有稿'); await page.getByLabel('正文', { exact: true }).fill('B 私有正文')
  let uploadSeen!: () => void; let uploadRelease!: () => void; const uploadObserved = new Promise<void>(resolve => { uploadSeen = resolve }); const uploadGate = new Promise<void>(resolve => { uploadRelease = resolve })
  await page.route('**/api/me/posts/*/attachments', async route => { const response = await route.fetch(); uploadSeen(); await uploadGate; await route.fulfill({ response }).catch(() => undefined) })
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles({ name: 'B-late.md', mimeType: 'text/markdown', buffer: Buffer.from('# B only') }); await uploadObserved
  await page.locator('.account-trigger:visible').first().click(); await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录后，继续阅读' })).toBeVisible(); await go(page, '/community'); await page.getByLabel('用户名').fill(admin.username); await page.getByLabel('密码', { exact: true }).fill(admin.password); await page.getByRole('button', { name: '登录', exact: true }).click(); uploadRelease()
  await expect(page.getByRole('heading', { name: '让每一点进展，被看见' })).toBeVisible(); await go(page, '/publishing'); await expect(page.locator('.manage-row')).toHaveCount(1); await expect(page.locator('.manage-list')).toContainText('A 私有稿'); await expect(page.locator('.manage-list')).not.toContainText('B 私有稿'); await expect(page.locator('.file-row')).toHaveCount(0)
})

test('HTTP response fixtures retain 403/404/network meaning, loading/empty states and strict invalid target', async ({ page }) => {
  await signIn(page)
  for (const status of [403, 503]) {
    await page.route('**/api/community/posts/page?*', route => route.fulfill({ status, contentType: 'application/problem+json', body: JSON.stringify({ detail: `UI response fixture ${status}` }) }))
    await go(page, '/publishing'); await go(page, '/community'); await expect(page.getByRole('heading', { name: '内容暂时没有加载出来' })).toBeVisible(); await expect(page.locator('.account-trigger').first()).toBeVisible(); await screenshot(page, `fixture-http-${status}-desktop.png`); await page.unroute('**/api/community/posts/page?*')
  }
  await go(page, '/publishing'); await go(page, '/community'); await expect(page.getByRole('heading', { name: '让第一份分享，从你开始' })).toBeVisible(); await screenshot(page, 'extra-empty-feed-desktop.png')
  await go(page, '/publishing/sources'); await page.getByLabel('素材日期').fill('2000-01-01'); await expect(page.getByRole('heading', { name: '这一天还没有记录' })).toBeVisible(); await screenshot(page, 'extra-empty-sources-desktop.png')
  await go(page, '//evil.example/redirect'); await expect(page.getByRole('heading', { name: '内容暂不可访问' })).toBeVisible(); expect(new URL(page.url()).origin).toBe('http://127.0.0.1:15173'); await expect(page.locator('.account-trigger').first()).toBeVisible()
})

test('type-change saves the old immutable type, dirty cancel keeps URL and copy fallback restores focus', async ({ page, adminApi }) => {
  await signIn(page, admin, '/#/publishing/new/BLOG'); await page.getByLabel('标题', { exact: true }).fill('原类型博客'); await page.getByLabel('摘要（可选）').fill('原类型摘要'); await page.getByLabel('正文', { exact: true }).fill('原稿正文')
  await page.locator('.type-tabs').getByRole('button', { name: '动态', exact: true }).click(); await expect(page.getByRole('heading', { name: '新建另一种内容？' })).toBeVisible(); await page.getByRole('button', { name: '继续编辑', exact: true }).click(); await expect(page).toHaveURL(/publishing\/new\/BLOG$/)
  await page.locator('.type-tabs').getByRole('button', { name: '动态', exact: true }).click(); await page.getByRole('button', { name: '保存并新建', exact: true }).click(); await expect(page).toHaveURL(/publishing\/new\/MOMENT$/); await expect(page.getByLabel('正文', { exact: true })).toHaveValue('')
  const list = await (await adminApi.get('/api/me/posts/page?page=0&size=5')).json() as { items: { postId: string; type: string; title: string }[] }
  expect(list.items).toHaveLength(1); expect(list.items[0].type).toBe('BLOG'); expect(list.items[0].title).toBe('原类型博客')
  const previous = await (await adminApi.get(`/api/me/posts/${list.items[0].postId}`)).json() as OwnerPost; expect(previous.draft.summary).toBe('原类型摘要')
  await page.getByLabel('正文', { exact: true }).fill('新动态输入'); await go(page, '/community'); await expect(page.getByRole('dialog')).toHaveCount(1); await go(page, '/publishing'); await page.getByRole('button', { name: '继续编辑', exact: true }).click(); await expect(page).toHaveURL(/publishing\/new\/MOMENT$/); await expect(page.getByLabel('正文', { exact: true })).toHaveValue('新动态输入')
  const id = await publish(page)
  await page.evaluate(() => { Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async () => { throw new Error('fixture clipboard denial') } } }) })
  const copy = page.getByRole('button', { name: '复制站内链接', exact: true }); await copy.click(); await expect(page.getByRole('dialog')).toBeVisible(); await expect(page.getByLabel('站内链接', { exact: true })).toHaveValue(`http://127.0.0.1:15173/#/community/posts/${id}`); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0); await expect(copy).toBeFocused()
})

test('real R2 populated feed, saved UUID editor and PNG PDF MD visual evidence', async ({ page, adminApi }) => {
  const profile = await (await adminApi.get('/api/me/community/profile')).json() as { nickname: string; bio: string; version: number }
  expect((await adminApi.put('/api/me/community/profile', { data: { ...profile, nickname: '视觉验收成员', bio: '通过真实业务接口保存和发布，仅用于本地隔离验收。' } })).status()).toBe(200)
  await signIn(page, admin, '/#/publishing/new/BLOG')
  await page.getByLabel('标题', { exact: true }).fill('把记录整理成可复用的方法')
  await page.getByLabel('摘要（可选）').fill('保留一份私有草稿，发布由作者明确确认。')
  await page.getByLabel('正文', { exact: true }).fill('先记下发生了什么，再留下判断和下一步。\n\n## 一份可继续打磨的草稿\n\n正文、图片、PDF 与 MD 都保留在私有草稿中。')
  const image = imageFixture(); const pdf = pdfFixture(); const markdown = Buffer.from('# 复盘清单\n\n- 核对真实输入\n- 保留原始字节\n', 'utf8')
  await page.getByLabel('选择图片、PDF 或 MD 文件', { exact: true }).setInputFiles([
    { name: '复盘图片.png', mimeType: 'image/png', buffer: image },
    { name: '复盘笔记.pdf', mimeType: 'application/pdf', buffer: pdf },
    { name: '复盘清单.md', mimeType: 'text/markdown', buffer: markdown },
  ])
  for (const fileName of ['复盘图片.png', '复盘笔记.pdf', '复盘清单.md']) await expect(page.locator('.file-row').filter({ hasText: fileName })).toContainText('可用')
  await save(page)
  const privateId = new URL(page.url()).hash.split('/')[3]
  expect((await adminApi.get(`/api/community/posts/${privateId}`)).status()).toBe(404)
  await page.locator('.community-scroll').evaluate(element => { element.scrollTop = 0 })
  await screenshot(page, 'saved-editor-desktop-top.png')
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.community-scroll').evaluate(element => { element.scrollTop = 0 })
  await expect(page.getByRole('link', { name: '返回我的发布', exact: true })).toBeInViewport()
  await screenshot(page, '13-editor-mobile.png')
  await page.locator('.attachments').scrollIntoViewIfNeeded(); await screenshot(page, '14-attachments-mobile.png')
  await expect(page.locator('.file-row')).toHaveCount(3)
  await expect(page.locator('.mobile-publish')).toBeInViewport()
  const publishApi = async (type: 'BLOG' | 'MOMENT' | 'DAILY', title: string, bodyMarkdown: string, summary = '') => {
    const input = { type, businessDate: type === 'DAILY' ? '2026-10-03' : null, title, summary, bodyMarkdown }
    const created = await adminApi.post('/api/me/posts', { data: input }); expect(created.status()).toBe(201)
    const own = await created.json() as OwnerPost
    let version = own.version; const attachmentIds: string[] = []
    if (type === 'BLOG') {
      const uploaded = await adminApi.post(`/api/me/posts/${own.postId}/attachments`, { multipart: { file: { name: '公开图片.png', mimeType: 'image/png', buffer: image }, expectedVersion: String(version), requestId: crypto.randomUUID() } })
      expect(uploaded.status()).toBe(201); const result = await uploaded.json() as { attachment: { id: string; state: string }; version: number }; expect(result.attachment.state).toBe('READY'); version = result.version; attachmentIds.push(result.attachment.id)
    }
    const published = await adminApi.post(`/api/me/posts/${own.postId}/publish`, { data: { ...input, attachmentIds, version, requestId: crypto.randomUUID(), visibility: 'MEMBERS' } }); expect(published.status()).toBe(200)
    return own.postId
  }
  const blogId = await publishApi('BLOG', '让一份记录留下下一次可用的方法', '## 一个具体的方法\n\n记下做过的事，也留下为什么这样判断。', '从一条记录开始，整理可以复用的经验。')
  const momentId = await publishApi('MOMENT', '', '今天的一点发现：把关键判断写下来，下一次就少走一步弯路。')
  const dailyId = await publishApi('DAILY', '今天的小步进展', '- 完成一项接口核对\n- 验证私有附件原始字节\n\n分享的是明确确认的内容。')
  await page.setViewportSize({ width: 1440, height: 1040 })
  await go(page, '/community'); await expect(page.locator('.post-card')).toHaveCount(3)
  for (const id of [blogId, momentId, dailyId]) await expect(page.locator(`[data-post-id="${id}"]`)).toBeVisible()
  await expect(page.getByRole('link', { name: '继续编辑', exact: true })).toHaveAttribute('href', `#/publishing/posts/${privateId}`)
  await page.locator('.community-scroll').evaluate(element => { element.scrollTop = 0 }); await screenshot(page, 'populated-feed-desktop.png'); await geometry(page)
  await page.locator('.community-scroll').evaluate(element => { element.scrollTop = element.scrollHeight }); await screenshot(page, 'populated-feed-desktop-bottom.png')
  await page.setViewportSize({ width: 390, height: 844 }); await page.locator('.community-scroll').evaluate(element => { element.scrollTop = 0 }); await screenshot(page, 'populated-feed-mobile.png'); await geometry(page, true)
  await page.locator('.community-scroll').evaluate(element => { element.scrollTop = element.scrollHeight }); await screenshot(page, 'populated-feed-mobile-bottom.png')
  await go(page, `/publishing/posts/${privateId}`); await expect(page.getByLabel('标题', { exact: true })).toHaveValue('把记录整理成可复用的方法')
  const publishedId = await publish(page); expect(publishedId).toBe(privateId)
  await expect(page.locator('.article-files .file-row')).toHaveCount(2)
  await expect(page.getByRole('button', { name: '下载 复盘笔记.pdf', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '下载 复盘清单.md', exact: true })).toBeVisible()
  await page.locator('.article-files').scrollIntoViewIfNeeded(); await screenshot(page, '16-downloads-mobile.png')
  for (const [fileName, bytes] of [['复盘笔记.pdf', pdf], ['复盘清单.md', markdown]] as const) {
    const received = page.waitForEvent('download'); await page.getByRole('button', { name: `下载 ${fileName}`, exact: true }).click(); const file = await received
    expect(file.suggestedFilename()).toBe(fileName); expect(createHash('sha256').update(await fs.readFile((await file.path())!)).digest('hex')).toBe(createHash('sha256').update(bytes).digest('hex'))
  }
})
