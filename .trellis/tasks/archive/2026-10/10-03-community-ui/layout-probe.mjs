/* FIXTURE-ONLY UI probe: no backend, DB or storage acceptance is claimed. */
import { chromium } from '../../../frontend/node_modules/@playwright/test/index.mjs'
import fs from 'node:fs/promises'
import path from 'node:path'
const directory = '.trellis/tasks/10-03-community-ui/validation/fixture-layout'
await fs.mkdir(directory, { recursive: true })
const id = 'd9500000-0000-4000-8000-000000000001'
const postId = 'd9500000-0000-4000-8000-000000000002'
const author = { id, nickname: '布局测试作者', bio: '仅布局夹具，不代表业务验收。' }
const body = '## 一个具体的方法\n\n这段文字仅验证 R2 正文布局。\n\n'.repeat(30)
const published = { id: postId, type: 'BLOG', businessDate: null, title: '布局测试博客', summary: '这里用于核对真实 React 组件的区域顺序。', bodyMarkdown: body, firstPublishedAt: '2026-10-03T00:00:00Z', publishedAt: '2026-10-03T00:00:00Z', revisionId: 'd9500000-0000-4000-8000-000000000003', revisionNo: 1, author, attachments: [] }
const draft = { type: 'BLOG', businessDate: null, title: '布局测试博客', summary: '布局摘要', bodyMarkdown: body, attachmentIds: [], attachments: [], savedAt: '2026-10-03T00:00:00Z' }
const own = { postId, type: 'BLOG', status: 'PUBLISHED', version: 2, draft, attachments: [], currentPublished: published, moderation: null }
const materials = Array.from({ length: 7 }, (_, index) => ({ id: `d9500000-0000-4000-8000-00000000001${index}`, source: 'MANUAL', content: `布局素材 ${index + 1} 完整内容`, completionResult: '', progress: '', focusMs: null, projectName: null, occurredAt: '2026-10-03T00:00:00Z' }))
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1040 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' })
const errors = []; page.on('pageerror', error => errors.push(error.message))
let feedError = false
await page.route('**/*', async route => {
  const url = new URL(route.request().url()); if (!url.pathname.startsWith('/api/')) return route.continue(); let data; let status = 200
  if (url.pathname === '/api/auth/me') data = { id, username: 'fixture_ui', role: 'ADMIN', enabled: true }
  else if (url.pathname === '/api/auth/csrf') data = { token: 'fixture-only-token' }
  else if (url.pathname === '/api/auth/activity') data = {}
  else if (url.pathname === '/api/projects') data = []
  else if (url.pathname === '/api/focus/current') data = null
  else if (url.pathname === '/api/focus/routines/fill-today') data = { date: '2026-10-03', created: [], blocked: [] }
  else if (url.pathname === '/api/me/community/profile') data = { nickname: author.nickname, bio: author.bio, version: 0 }
  else if (url.pathname === '/api/me/posts/page') data = { items: [{ postId, ...own, title: draft.title, summary: draft.summary, updatedAt: draft.savedAt, firstPublishedAt: published.firstPublishedAt, currentRevisionId: published.revisionId, currentRevisionNo: 1, hasUnpublishedChanges: false }], page: 0, size: 5, totalPages: 1, totalElements: 1 }
  else if (url.pathname === `/api/me/posts/${postId}`) data = own
  else if (url.pathname === `/api/community/posts/${postId}`) data = published
  else if (url.pathname === `/api/community/authors/${id}`) data = author
  else if (url.pathname === '/api/community/posts/page') {
    if (feedError) { status = 503; data = { detail: '布局夹具网络失败状态' } }
    else data = { items: [published], page: 0, size: 5, totalPages: 1, totalElements: 1 }
  }
  else if (url.pathname === '/api/me/community/sources/page') { const page = Number(url.searchParams.get('page')); data = { items: materials.slice(page * 5, page * 5 + 5), page, size: 5, totalPages: 2, totalElements: 7 } }
  else { status = 404; data = { detail: '布局夹具未配置路径' } }
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
})
const measurements = []
const capture = async (route, name, width = 1440, height = 1040, bottom = false) => {
  await page.setViewportSize({ width, height }); await page.goto(`http://127.0.0.1:15173/#${route}`)
  await page.locator('.community-scroll').waitFor().catch(async error => { await page.screenshot({ path: path.join(directory, 'debug-failure.png') }); console.log(JSON.stringify({ errors, html: await page.locator('body').innerText() })); await browser.close(); throw error }); await page.locator('[role="status"]').count().then(async count => { if (count) await page.locator('[role="status"]').first().waitFor({ state: 'hidden' }).catch(() => undefined) })
  if (bottom) await page.locator('.community-scroll').evaluate(element => { element.scrollTop = element.scrollHeight })
  const geometry = await page.evaluate(() => ({ viewportWidth: innerWidth, viewportHeight: innerHeight, documentWidth: document.documentElement.scrollWidth, documentHeight: document.documentElement.scrollHeight, sidebarWidth: document.querySelector('.sidebar')?.getBoundingClientRect().width, scrollHeight: document.querySelector('.community-scroll')?.getBoundingClientRect().height, scrollTop: document.querySelector('.community-scroll')?.scrollTop, visibleConfirmationCount: [...document.querySelectorAll('.check-confirm')].filter(element => element.getBoundingClientRect().width > 0).length }))
  measurements.push({ name, route, ...geometry }); await page.screenshot({ path: path.join(directory, `${name}.png`), animations: 'disabled' })
}
for (const [route, name] of [['/community', 'community-desktop'], ['/publishing/new', 'compose-desktop'], ['/publishing/sources', 'sources-desktop'], [`/publishing/posts/${postId}`, 'editor-desktop'], [`/publishing/posts/${postId}/preview`, 'preview-desktop'], [`/community/posts/${postId}`, 'detail-desktop'], ['/publishing', 'mine-desktop'], [`/community/authors/${id}`, 'author-desktop'], ['invalid-route', 'unavailable-desktop']]) await capture(route, name)
for (const [route, name] of [['/community', 'community-mobile'], ['/publishing/sources', 'sources-mobile'], [`/publishing/posts/${postId}`, 'editor-mobile'], [`/publishing/posts/${postId}/preview`, 'preview-mobile'], [`/community/posts/${postId}`, 'detail-mobile'], ['/publishing', 'mine-mobile']]) await capture(route, name, 390, 844)
await capture(`/publishing/posts/${postId}`, 'attachments-mobile-bottom', 390, 844, true)
await capture(`/publishing/posts/${postId}/preview`, 'preview-mobile-bottom', 390, 844, true)
await capture('/community', 'community-320', 320, 740)
await capture('/community', 'community-1024', 1024, 768)
feedError = true; await capture('/community', 'feed-error-fixture')
await fs.writeFile(path.join(directory, 'measurements.json'), JSON.stringify({ kind: 'FIXTURE_ONLY', measurements, pageErrors: errors }, null, 2))
await browser.close()
console.log(JSON.stringify({ kind: 'FIXTURE_ONLY', pages: measurements.length, pageErrors: errors, overflow: measurements.filter(item => item.documentWidth > item.viewportWidth || item.documentHeight > item.viewportHeight) }))
