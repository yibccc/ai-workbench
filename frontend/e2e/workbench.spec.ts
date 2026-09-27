import { expect, test as base, type APIRequestContext, type Locator, type Page } from '@playwright/test'

const apiBase = 'http://127.0.0.1:18080'
const e2eUsername = 'e2e_admin'
const e2ePassword = 'E2eOnly-Synthetic-9384!'
const test = base.extend<{ request: APIRequestContext }>({
  request: async ({ playwright }, applyFixture) => {
    const setup = await playwright.request.newContext({ baseURL: apiBase })
    const csrfResponse = await setup.get('/api/auth/csrf')
    expect(csrfResponse.ok()).toBeTruthy()
    const { token } = await csrfResponse.json() as { token: string }
    const loginResponse = await setup.post('/api/auth/login', {
      headers: { 'X-XSRF-TOKEN': token }, data: { username: e2eUsername, password: e2ePassword },
    })
    expect(loginResponse.status()).toBe(200)
    const currentCsrf = await (await setup.get('/api/auth/csrf')).json() as { token: string }
    const request = await playwright.request.newContext({
      baseURL: apiBase, storageState: await setup.storageState(), extraHTTPHeaders: { 'X-XSRF-TOKEN': currentCsrf.token },
    })
    await setup.dispose()
    await applyFixture(request)
    await request.dispose()
  },
})

async function reset(request: APIRequestContext) {
  const response = await request.post(`${apiBase}/api/e2e/reset`)
  expect(response.ok()).toBeTruthy()
}

async function createProject(request: APIRequestContext, name = 'E2E 项目') {
  const response = await request.post(`${apiBase}/api/projects`, { data: { name } })
  expect(response.status()).toBe(200)
  return response.json() as Promise<{ id: string; name: string }>
}

async function openWorkbench(page: Page) {
  await page.goto('/')
  await page.getByLabel('用户名').fill(e2eUsername)
  await page.getByLabel('密码', { exact: true }).fill(e2ePassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await expect(page.getByText(/backend ·|postgres ·|redis ·|deepseek ·/)).toHaveCount(0)
}

function mockFocusSession(phase: 'RUNNING' | 'MICRO_BREAK' | 'PAUSED') {
  const now = new Date().toISOString()
  return {
    id: 'd9500000-0000-4000-8000-000000000001', requestId: 'd9500000-0000-4000-8000-000000000002', title: '模拟专注', taskId: null, projectId: null,
    targetMs: 1_500_000, intervalMs: 600_000, zoneId: 'Asia/Shanghai', phase, version: 1, startedAt: now,
    anchorAt: now, endedAt: null, focusMs: 600_000, breakMs: 0, pauseMs: 0,
    resumePhase: phase === 'PAUSED' ? 'RUNNING' : null, breakRemainingMs: 15_000,
    nextBreakAtMs: 1_200_000, remindersDismissed: false, reminderOrdinal: 1,
    controllerId: null, controllerGeneration: 0, controllerExpiresAt: null, progress: '',
  }
}

async function pendingStorageKey(page: Page) {
  const response = await page.context().request.get('/api/auth/me')
  expect(response.status()).toBe(200)
  const account = await response.json() as { id: string }
  return `ai-workbench.pending.v2.${account.id}`
}

async function openSyntheticAdminManagement(page: Page, request: APIRequestContext) {
  const username = `e2e_self_${crypto.randomUUID().slice(0, 8)}`
  const password = 'E2eOnly-SelfAdmin-9384!'
  const created = await request.post(`${apiBase}/api/admin/users`, { data: { username, password, role: 'ADMIN' } })
  expect(created.status()).toBe(201)
  const account = await created.json() as { id: string }
  await page.goto('/')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await page.getByRole('button', { name: /^账号菜单/ }).click()
  await page.getByRole('menuitem', { name: '用户管理' }).click()
  const row = page.locator('.user-row').filter({ hasText: username })
  await expect(row).toContainText('当前账号')
  return { account, row, username }
}

async function openSection(page: Page, id: string) {
  await navigate(page, '工作汇报')
  await page.getByRole('group', { name: '报告类型' }).getByRole('button', { name: id === 'daily' ? /^日报/ : /^周报/ }).click()
}

async function navigate(page: Page, name: string) {
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: new RegExp(`^${name}`) }).click()
}

async function assertIndependentReportPanes(page: Page, kind: 'daily' | 'weekly') {
  const report = page.getByTestId(`${kind}-report`)
  const documentPane = report.getByRole('region', { name: kind === 'daily' ? '日报正文' : '周报正文' })
  const evidencePane = report.getByRole('region', { name: kind === 'daily' ? '日报来源数据' : '周报来源数据' })
  const evidencePager = report.locator('.report-sources > .pagination')
  await expect.poll(() => documentPane.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
  await expect.poll(() => evidencePane.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
  await evidencePager.scrollIntoViewIfNeeded()
  await documentPane.evaluate(element => { element.scrollTop = 0 })
  await evidencePane.evaluate(element => { element.scrollTop = 0 })
  const pagerPosition = await evidencePager.boundingBox()
  await evidencePane.evaluate(element => { element.scrollTop = element.scrollHeight })
  await expect.poll(() => evidencePane.evaluate(element => element.scrollTop > 0)).toBe(true)
  expect(await documentPane.evaluate(element => element.scrollTop)).toBe(0)
  await expect(evidencePager).toBeInViewport()
  expect(Math.round((await evidencePager.boundingBox())?.y ?? -1)).toBe(Math.round(pagerPosition?.y ?? -2))
  const evidencePosition = await evidencePane.evaluate(element => element.scrollTop)
  await documentPane.evaluate(element => { element.scrollTop = element.scrollHeight })
  await expect.poll(() => documentPane.evaluate(element => element.scrollTop > 0)).toBe(true)
  expect(await evidencePane.evaluate(element => element.scrollTop)).toBe(evidencePosition)
}

async function assertPinnedPager(rows: Locator, pager: Locator, lastRow?: Locator) {
  await pager.scrollIntoViewIfNeeded()
  await rows.evaluate(element => { element.scrollTop = 0 })
  const before = await pager.boundingBox()
  await rows.evaluate(element => { element.scrollTop = element.scrollHeight })
  await expect.poll(() => rows.evaluate(element => element.scrollTop > 0)).toBe(true)
  const after = await pager.boundingBox()
  expect(Math.abs((after?.y ?? -1) - (before?.y ?? -2))).toBeLessThanOrEqual(2)
  await expect(pager).toBeInViewport()
  if (lastRow) {
    await lastRow.scrollIntoViewIfNeeded()
    await expect(lastRow).toBeInViewport({ ratio: 0.1 })
    await expect(pager).toBeInViewport()
  }
  const next = pager.getByRole('button', { name: '下一页' })
  if (await next.isEnabled()) {
    await next.click()
    await expect(pager).toContainText('第 2 /')
    await pager.getByRole('button', { name: '上一页' }).click()
    await expect(pager).toContainText('第 1 /')
  }
}

test.beforeEach(async ({ request }) => reset(request))

test('专注独立页从待办带入但不自动开工，跨页可暂停并保留记录草稿', async ({ page, request }) => {
  const created = await request.post(`${apiBase}/api/tasks`, { data: { title: '整理专注验收材料', notes: '', priority: 'MEDIUM' } })
  expect(created.ok()).toBeTruthy()
  const task = await created.json() as { id: string }
  expect((await request.post(`${apiBase}/api/tasks`, { data: { title: '准备下一项任务', notes: '', priority: 'LOW' } })).ok()).toBeTruthy()
  await openWorkbench(page)
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await page.getByTestId('record-content').fill('专注前的未保存草稿')
  await navigate(page, '待办任务')
  await page.getByTestId('task-item').filter({ hasText: '整理专注验收材料' }).getByRole('button', { name: '带入专注' }).click()
  await expect(page.getByTestId('focus-page')).toBeVisible()
  await expect(page.getByLabel('目标', { exact: true })).toHaveValue('整理专注验收材料')
  const beforeStart = await (await request.get(`${apiBase}/api/focus/current`)).text()
  expect(beforeStart === '' || beforeStart === 'null').toBe(true)
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('button', { name: '暂停', exact: true })).toBeVisible()
  const current = await (await request.get(`${apiBase}/api/focus/current`)).json() as { id: string; taskId: string }
  expect(current.taskId).toBe(task.id)
  await navigate(page, '待办任务')
  await page.getByTestId('task-item').filter({ hasText: '准备下一项任务' }).getByRole('button', { name: '带入专注' }).click()
  await expect(page.getByText(/当前仍在进行“整理专注验收材料”/)).toBeVisible()
  expect((await (await request.get(`${apiBase}/api/focus/current`)).json()).id).toBe(current.id)
  await navigate(page, '工作记录')
  await expect(page.getByTestId('record-content')).toHaveValue('专注前的未保存草稿')
  await expect(page.getByTestId('record-list').getByText('今日专注汇总')).toHaveCount(0)
  await page.getByRole('button', { name: '暂停', exact: true }).click()
  await expect(page.getByRole('button', { name: '继续', exact: true })).toBeVisible()
  await page.getByRole('link', { name: /^返回专注：/ }).click()
  await expect(page.getByTestId('focus-page')).toBeVisible()
  await expect(page.getByText('已暂停')).toBeVisible()
  await page.goBack()
  await expect(page.getByTestId('record-content')).toHaveValue('专注前的未保存草稿')
  expect((await (await request.get(`${apiBase}/api/focus/current`)).json()).id).toBe(current.id)
  await page.goForward()
  await expect(page.getByTestId('focus-page').getByText('已暂停')).toBeVisible()
  expect((await (await request.get(`${apiBase}/api/focus/current`)).json()).id).toBe(current.id)
  await page.reload()
  await expect(page.getByTestId('focus-page').getByText('已暂停')).toBeVisible()
  expect((await (await request.get(`${apiBase}/api/focus/current`)).json()).id).toBe(current.id)
})

test('专注五项导航及规则和汇总在窄屏保持可达', async ({ page }) => {
  await openWorkbench(page)
  for (const [width, height, choice] of [[320, 520, 15], [390, 520, 25], [760, 700, 45], [1440, 900, 60]]) {
    await page.setViewportSize({ width, height })
    await navigate(page, '专注')
    await expect(page.getByRole('navigation', { name: '主导航' }).getByRole('link')).toHaveCount(5)
    await expect(page.getByTestId('focus-page').getByRole('heading', { name: '专注', exact: true })).toBeVisible()
    await page.getByRole('tab', { name: '重复规则' }).click()
    await expect(page.getByRole('heading', { name: '每日重复任务' })).toBeVisible()
    await page.getByRole('tab', { name: '今日汇总' }).click()
    await expect(page.getByRole('heading', { name: '今日专注汇总' })).toBeVisible()
    await page.getByRole('tab', { name: '计时' }).click()
    const duration = page.getByLabel('目标净时长（分钟）')
    await duration.click()
    const option = page.getByRole('listbox', { name: '快捷时长' }).getByRole('option', { name: `${choice} 分钟` })
    await option.scrollIntoViewIfNeeded()
    await expect(option).toBeInViewport({ ratio: 0.9 })
    await option.click()
    await expect(duration).toHaveValue(String(choice))
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight + 1)).toBe(true)
  }
})

test('默认 45 分钟与输入框快捷下拉，开始手势启用声音且刷新可试听', async ({ page, request }) => {
  await page.addInitScript(() => {
    const state = { resumes: 0, tones: 0 }
    Object.assign(window, { __focusSound: state })
    Object.defineProperty(window, 'AudioContext', { configurable: true, value: class {
      state = 'running'; currentTime = 0; destination = {}
      resume() { state.resumes++; return Promise.resolve() }
      close() { return Promise.resolve() }
      createOscillator() { return { frequency: { value: 0 }, connect: (next: unknown) => next, start: () => { state.tones++ }, stop: () => undefined } }
      createGain() { return { gain: { value: 0 }, connect: () => this.destination } }
    } })
  })
  await openWorkbench(page)
  await navigate(page, '专注')
  await expect(page.getByRole('heading', { name: '声音与提示' })).toHaveCount(0)
  await expect(page.locator('.focus-timer-grid .focus-side')).toHaveCount(0)
  const duration = page.getByLabel('目标净时长（分钟）')
  await expect(duration).toHaveValue('45')
  await expect(page.locator('.focus-form > .focus-actions').getByRole('button', { name: '45 分钟' })).toHaveCount(0)
  for (const minutes of [15, 25, 45, 60]) {
    await duration.click()
    await expect(page.getByRole('listbox', { name: '快捷时长' }).getByRole('option')).toHaveCount(4)
    await page.getByRole('listbox', { name: '快捷时长' }).getByRole('option', { name: `${minutes} 分钟` }).click()
    await expect(duration).toHaveValue(String(minutes))
  }
  await duration.fill('37')
  await duration.press('Escape')
  await expect(duration).toHaveValue('37')
  await expect(duration).toHaveAttribute('min', '1')
  await expect(duration).toHaveAttribute('max', '480')
  await duration.press('ArrowDown')
  await expect(page.getByRole('listbox', { name: '快捷时长' })).toBeVisible()
  await duration.press('Escape')
  await expect(page.getByRole('listbox', { name: '快捷时长' })).toHaveCount(0)
  await duration.click()
  await duration.press('Tab')
  await expect(page.getByRole('option', { name: '15 分钟' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(duration).toHaveValue('15')
  await duration.click()
  await page.getByRole('option', { name: '45 分钟' }).click()
  await expect(duration).toHaveValue('45')
  await page.getByLabel('目标', { exact: true }).fill('试听与快捷时长')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '试听与快捷时长' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => (window as Window & { __focusSound?: { resumes: number; tones: number } }).__focusSound?.resumes)).toBeGreaterThanOrEqual(1)
  await expect.poll(() => page.evaluate(() => (window as Window & { __focusSound?: { resumes: number; tones: number } }).__focusSound?.tones)).toBeGreaterThanOrEqual(1)
  await expect(page.getByText('声音已启用')).toBeVisible()
  const session = await (await request.get(`${apiBase}/api/focus/current`)).json() as { id: string; targetMs: number }
  expect(session.targetMs).toBe(45 * 60_000)
  await page.reload()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '试听与快捷时长' })).toBeVisible()
  await expect(page.getByRole('button', { name: '启用并试听声音' })).toBeVisible()
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await expect(page.getByText('声音已启用')).toBeVisible()
  await expect.poll(() => page.evaluate(() => (window as Window & { __focusSound?: { resumes: number; tones: number } }).__focusSound?.tones)).toBeGreaterThanOrEqual(1)
  expect((await (await request.get(`${apiBase}/api/focus/current`)).json()).id).toBe(session.id)
})

test('开始时声音被拒仍保留会话与主面板视觉反馈', async ({ page }) => {
  await page.addInitScript(() => { Object.defineProperty(window, 'AudioContext', { configurable: true, value: class { constructor() { throw new Error('audio denied') } } }) })
  await openWorkbench(page)
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('声音拒绝降级')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '声音拒绝降级' })).toBeVisible()
  await expect(page.locator('.focus-main').getByRole('alert')).toContainText('声音未启用')
  await expect(page.getByRole('button', { name: '启用并试听声音' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '声音与提示' })).toHaveCount(0)
})

test('跨页声音失败可见且微休息时嵌入引导，重试后清除', async ({ page, request }) => {
  await page.addInitScript(() => {
    Object.assign(window, { __focusSoundBlocked: true })
    Object.defineProperty(window, 'AudioContext', { configurable: true, value: class {
      state = 'running'; currentTime = 0; destination = {}
      constructor() { if ((window as Window & { __focusSoundBlocked?: boolean }).__focusSoundBlocked) throw new Error('audio denied') }
      resume() { return Promise.resolve() }
      close() { return Promise.resolve() }
      createOscillator() { return { frequency: { value: 0 }, connect: (next: unknown) => next, start: () => undefined, stop: () => undefined } }
      createGain() { return { gain: { value: 0 }, connect: () => this.destination } }
    } })
  })
  await openWorkbench(page)
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('跨页声音失败')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.locator('.focus-main').getByRole('alert')).toContainText('声音未启用')
  await navigate(page, '工作汇报')
  const alert = page.getByTestId('focus-sound-alert')
  await expect(alert).toBeVisible()
  await expect(alert.getByRole('link', { name: '返回专注' })).toHaveAttribute('href', '#focus')
  await page.setViewportSize({ width: 320, height: 700 })
  await expect(alert).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true)
  const current = await (await request.get(`${apiBase}/api/focus/current`)).json() as ReturnType<typeof mockFocusSession>
  await page.route('**/api/focus/sessions/*/checkpoint', route => route.fulfill({ json: {
    ...current, phase: 'MICRO_BREAK', version: current.version + 1, anchorAt: new Date().toISOString(),
    focusMs: 600_000, breakRemainingMs: 15_000, reminderOrdinal: 1,
  } }))
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  const guidance = page.getByRole('dialog', { name: '微休息引导' })
  await expect(guidance).toBeVisible()
  await expect(guidance.getByTestId('focus-sound-alert')).toBeVisible()
  await expect(page.getByTestId('focus-sound-alert')).toHaveCount(1)
  const cardBounds = await guidance.locator('.focus-break-card').boundingBox()
  const alertBounds = await guidance.getByTestId('focus-sound-alert').boundingBox()
  expect(cardBounds && alertBounds && alertBounds.y >= cardBounds.y
    && alertBounds.y + alertBounds.height <= cardBounds.y + cardBounds.height).toBeTruthy()
  await guidance.getByRole('link', { name: '返回专注' }).click()
  await expect(page.getByTestId('focus-page')).toBeVisible()
  await expect(guidance.getByTestId('focus-sound-alert')).toBeVisible()
  await page.evaluate(() => Object.assign(window, { __focusSoundBlocked: false }))
  await guidance.getByRole('button', { name: '重新启声' }).click()
  await expect(page.getByTestId('focus-sound-alert')).toHaveCount(0)
  await expect(guidance).toBeVisible()
})

test('专注写入无需发布开关即可创建规则和会话', async ({ page }) => {
  let capabilityCalls = 0
  let fillCalls = 0
  page.on('request', request => {
    const path = new URL(request.url()).pathname
    if (path === '/api/focus/capabilities') capabilityCalls++
    if (path === '/api/focus/routines/fill-today') fillCalls++
  })
  await openWorkbench(page)
  await expect.poll(() => fillCalls).toBeGreaterThan(0)
  await navigate(page, '专注')
  await expect(page.getByText(/新专注和重复规则写入尚未开放/)).toHaveCount(0)
  await page.getByRole('tab', { name: '重复规则' }).click()
  const rules = page.getByRole('region', { name: '专注内容' })
  await rules.getByLabel('名称').fill('无需开关的每日规则')
  await rules.getByRole('button', { name: '保存规则' }).click()
  await expect(page.locator('.focus-routine').filter({ hasText: '无需开关的每日规则' })).toBeVisible()
  await page.getByRole('tab', { name: '计时' }).click()
  await page.getByLabel('目标', { exact: true }).fill('无需开关的专注')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '无需开关的专注' })).toBeVisible()
  expect(capabilityCalls).toBe(0)
})

test('重复规则显式补齐今天且修改后不替换已生成待办', async ({ page, request }) => {
  await openWorkbench(page)
  await navigate(page, '专注')
  await page.getByRole('tab', { name: '重复规则' }).click()
  await page.getByRole('region', { name: '专注内容' }).getByLabel('名称').fill('每天整理计划')
  await page.getByLabel('默认专注时长（分钟）').fill('30')
  await page.getByRole('button', { name: '保存规则' }).click()
  await expect(page.locator('.focus-routine').filter({ hasText: '每天整理计划' })).toBeVisible()
  await page.getByRole('button', { name: '检查并补齐今天' }).click()
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/tasks`)).json() as Array<{ title: string }>).filter(task => task.title === '每天整理计划').length).toBe(1)
  await navigate(page, '待办任务')
  await page.getByTestId('task-item').filter({ hasText: '每天整理计划' }).getByRole('button', { name: '带入专注' }).click()
  await expect(page.getByLabel('目标净时长（分钟）')).toHaveValue('30')
  await page.getByRole('tab', { name: '重复规则' }).click()
  await page.locator('.focus-routine').filter({ hasText: '每天整理计划' }).getByRole('button', { name: '编辑' }).click()
  await page.getByRole('region', { name: '专注内容' }).getByLabel('名称').fill('以后整理计划')
  await page.getByRole('button', { name: '保存规则' }).click()
  await page.getByRole('button', { name: '检查并补齐今天' }).click()
  const tasks = await (await request.get(`${apiBase}/api/tasks`)).json() as Array<{ title: string; defaultFocusDurationMinutes: number }>
  expect(tasks.filter(task => task.title === '每天整理计划')).toHaveLength(1)
  expect(tasks.filter(task => task.title === '以后整理计划')).toHaveLength(0)
  expect(tasks.find(task => task.title === '每天整理计划')?.defaultFocusDurationMinutes).toBe(30)
  await page.locator('.focus-routine').filter({ hasText: '以后整理计划' }).getByRole('button', { name: '停用' }).click()
  await expect(page.locator('.focus-routine').filter({ hasText: '以后整理计划' })).toContainText('已停用')
})

test('带入待办时长优先，新建无关联专注恢复默认 45 分钟', async ({ page, request }) => {
  expect((await request.post(`${apiBase}/api/tasks`, { data: { title: '自带待办时长', notes: '', priority: 'MEDIUM' } })).ok()).toBeTruthy()
  await openWorkbench(page)
  await navigate(page, '待办任务')
  await page.getByTestId('task-item').filter({ hasText: '自带待办时长' }).getByRole('button', { name: '带入专注' }).click()
  await expect(page.getByLabel('目标净时长（分钟）')).toHaveValue('25')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByRole('button', { name: '提前结束并保存投入' })).toBeEnabled()
  await page.getByRole('button', { name: '提前结束并保存投入' }).click()
  await expect(page.getByRole('heading', { name: '本次会话已保存' })).toBeVisible()
  await page.getByRole('button', { name: '开始新专注' }).click()
  await expect(page.getByLabel('目标净时长（分钟）')).toHaveValue('45')
  await expect(page.getByLabel('目标', { exact: true })).toHaveValue('')
  await expect(page.getByText('已关联待办')).toHaveCount(0)
})

test('切换账号会清除旧专注状态与顶栏控制', async ({ page, request }) => {
  const username = `e2e_focus_b_${crypto.randomUUID().slice(0, 8)}`
  const password = 'E2eOnly-FocusB-9384!'
  expect((await request.post(`${apiBase}/api/admin/users`, { data: { username, password, role: 'USER' } })).status()).toBe(201)
  await openWorkbench(page)
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('仅 A 的专注')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeVisible()
  await navigate(page, '工作记录')
  await expect(page.getByRole('link', { name: /仅 A 的专注/ })).toBeVisible()
  await page.locator('.sidebar-footer').getByRole('button', { name: /^账号菜单/ }).click()
  await page.locator('.sidebar-footer').getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '登录工作台' })).toBeVisible()
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await expect(page.getByRole('link', { name: /仅 A 的专注/ })).toHaveCount(0)
  await navigate(page, '专注')
  await expect(page.getByRole('button', { name: '开始专注' })).toBeVisible()
  expect((await page.context().request.get('/api/focus/current')).status()).toBe(200)
})

test('开始响应丢失后恢复旧会话，结算后新目标使用新的请求键', async ({ page }) => {
  let starts = 0
  await page.route('**/api/focus/sessions', async route => {
    if (route.request().method() !== 'POST') { await route.continue(); return }
    starts++
    const response = await route.fetch()
    if (starts === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  await openWorkbench(page)
  const account = await (await page.context().request.get('/api/auth/me')).json() as { id: string }
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('响应丢失的旧会话')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '响应丢失的旧会话' })).toBeVisible()
  const first = await (await page.context().request.get('/api/focus/current')).json() as { id: string }
  expect(await page.evaluate(key => sessionStorage.getItem(key), `ai-workbench.focus-start.${account.id}`)).toBeNull()
  await page.getByRole('button', { name: '提前结束并保存投入' }).click()
  await expect(page.getByRole('heading', { name: '本次会话已保存' })).toBeVisible()
  await page.getByRole('button', { name: '开始新专注' }).click()
  await page.getByLabel('目标', { exact: true }).fill('新的独立目标')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '新的独立目标' })).toBeVisible()
  const second = await (await page.context().request.get('/api/focus/current')).json() as { id: string }
  expect(second.id).not.toBe(first.id)
  expect(starts).toBe(2)
})

test('旧请求已在别处结束时，新专注自动换请求键', async ({ page, request }) => {
  const oldRequestId = crypto.randomUUID()
  const started = await request.post(`${apiBase}/api/focus/sessions`, { data: {
    requestId: oldRequestId, title: '已结算的旧目标', taskId: null, projectId: null, targetMinutes: 25, intervalMinutes: 10,
  } })
  expect(started.status()).toBe(201)
  const old = await started.json() as { id: string; version: number }
  expect((await request.post(`${apiBase}/api/focus/sessions/${old.id}/end`, { data: { version: old.version } })).ok()).toBeTruthy()
  await openWorkbench(page)
  const account = await (await page.context().request.get('/api/auth/me')).json() as { id: string }
  await page.evaluate(({ key, value }) => sessionStorage.setItem(key, value), { key: `ai-workbench.focus-start.${account.id}`, value: oldRequestId })
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('真正的新目标')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '真正的新目标' })).toBeVisible()
  const current = await (await page.context().request.get('/api/focus/current')).json() as { id: string; requestId: string }
  expect(current.id).not.toBe(old.id)
  expect(current.requestId).not.toBe(oldRequestId)
  expect(await page.evaluate(key => sessionStorage.getItem(key), `ai-workbench.focus-start.${account.id}`)).toBeNull()
})

test('微休息在其他工作区显示唯一全局引导，跳过和关闭提醒保持可操作', async ({ page }) => {
  let session = mockFocusSession('MICRO_BREAK')
  const actions: string[] = []
  await page.route('**/api/focus/current', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/checkpoint', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/transition', async route => {
    const body = route.request().postDataJSON() as { action: string }
    actions.push(body.action)
    session = { ...session, version: session.version + 1, phase: 'RUNNING', breakRemainingMs: 0, remindersDismissed: body.action === 'DISMISS_REMINDERS' }
    await route.fulfill({ json: session })
  })
  await openWorkbench(page)
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(1)
  await page.evaluate(() => { window.location.hash = 'reports' })
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(1)
  await page.getByRole('dialog', { name: '微休息引导' }).getByRole('button', { name: '跳过本次' }).click()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(0)
  expect(actions).toContain('SKIP_BREAK')
  session = { ...mockFocusSession('MICRO_BREAK'), version: session.version + 1, reminderOrdinal: 2 }
  await page.reload()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(1)
  await page.getByRole('dialog', { name: '微休息引导' }).getByRole('button', { name: '关闭本段提醒' }).click()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(0)
  expect(actions).toContain('DISMISS_REMINDERS')
})

test('恢复已有会话时声音拒绝有可见反馈且无需确认失联时间', async ({ page }) => {
  const session = mockFocusSession('RUNNING')
  await page.addInitScript(() => { Object.defineProperty(window, 'AudioContext', { configurable: true, value: class { constructor() { throw new Error('audio denied') } } }) })
  await page.route('**/api/focus/current', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/checkpoint', route => route.fulfill({ json: session }))
  await openWorkbench(page)
  await navigate(page, '专注')
  await expect(page.locator('.focus-recovery')).toHaveCount(0)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await expect(page.getByText('声音未启用。请检查浏览器声音权限；视觉提示仍可使用。')).toBeVisible()
  await page.reload()
  await expect(page.getByTestId('focus-page').getByRole('heading', { name: '模拟专注' })).toBeVisible()
  await expect(page.locator('.focus-recovery')).toHaveCount(0)
})

test('本机时钟跳变时冻结本地投影并同步服务端权威时长', async ({ page }) => {
  let session = { ...mockFocusSession('RUNNING'), nextBreakAtMs: 690_000 }
  let checkpointCount = 0
  const transitions: string[] = []
  let releaseCheckpoint: () => void = () => undefined
  const held = new Promise<void>(resolve => { releaseCheckpoint = resolve })
  await page.route('**/api/focus/current', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/checkpoint', async route => {
    checkpointCount++
    if (checkpointCount > 1) {
      await held
      session = { ...session, version: session.version + 1, anchorAt: new Date().toISOString() }
    }
    await route.fulfill({ json: session })
  })
  await page.route('**/api/focus/sessions/*/transition', async route => { transitions.push(route.request().postDataJSON().action); await route.fulfill({ json: session }) })
  await openWorkbench(page)
  await navigate(page, '专注')
  await expect.poll(() => checkpointCount).toBeGreaterThanOrEqual(1)
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    const clock = Date.now.bind(Date)
    Date.now = () => clock() + 120_000
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect.poll(() => checkpointCount).toBeGreaterThanOrEqual(2)
  await expect(page.locator('.focus-clock')).toHaveText('15:00')
  expect(transitions).toEqual([])
  releaseCheckpoint()
  await expect(page.getByTestId('focus-page').getByText('专注中')).toBeVisible()
  await expect(page.locator('.focus-recovery')).toHaveCount(0)
})

test('隐藏与睡眠式长空档继续净计时，回前台只开启一次当前微休息', async ({ page }) => {
  let session = { ...mockFocusSession('RUNNING'), targetMs: 1_500_000, focusMs: 600_000, nextBreakAtMs: 690_000, reminderOrdinal: 0 }
  let hidden = false
  let hiddenCheckpoints = 0
  let activitySignals = 0
  const actions: string[] = []
  page.on('request', outgoing => { if (new URL(outgoing.url()).pathname === '/api/auth/activity') activitySignals++ })
  await page.route('**/api/focus/current', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/checkpoint', async route => {
    const body = route.request().postDataJSON() as { controllerId: string | null }
    if (hidden) {
      hiddenCheckpoints++
      expect(body.controllerId).toBeNull()
      session = { ...session, version: session.version + 1, focusMs: 750_000, breakMs: 0, phase: 'RUNNING', anchorAt: new Date().toISOString() }
    } else session = { ...session, version: session.version + 1, anchorAt: new Date().toISOString() }
    await route.fulfill({ json: session })
  })
  await page.route('**/api/focus/sessions/*/transition', async route => {
    const body = route.request().postDataJSON() as { action: string }
    actions.push(body.action)
    session = { ...session, version: session.version + 1, phase: 'MICRO_BREAK', reminderOrdinal: 1, breakRemainingMs: 15_000, anchorAt: new Date().toISOString() }
    await route.fulfill({ json: session })
  })
  await page.clock.install()
  await openWorkbench(page)
  await navigate(page, '专注')
  await expect(page.getByTestId('focus-page').getByText('专注中')).toBeVisible()
  activitySignals = 0
  hidden = true
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.clock.fastForward(90_000)
  await expect.poll(() => hiddenCheckpoints).toBeGreaterThanOrEqual(1)
  expect(activitySignals).toBe(0)
  expect(actions).toEqual([])
  expect(session.phase).toBe('RUNNING')
  expect(session.focusMs).toBe(750_000)
  expect(session.breakMs).toBe(0)
  expect(session.reminderOrdinal).toBe(0)
  hidden = false
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(1)
  expect(actions).toEqual(['BREAK_DUE'])
  await expect(page.locator('.focus-recovery')).toHaveCount(0)
})

test('真实服务器经过超过60秒的隐藏时段仍累计净专注且不在后台开休息', async ({ page, request }) => {
  test.setTimeout(100_000)
  let hidden = false
  let hiddenCheckpoints = 0
  let activitySignals = 0
  const breaks: string[] = []
  page.on('request', outgoing => {
    const url = new URL(outgoing.url())
    if (url.pathname === '/api/auth/activity') activitySignals++
    if (/^\/api\/focus\/sessions\/[^/]+\/checkpoint$/.test(url.pathname) && hidden) hiddenCheckpoints++
    if (/^\/api\/focus\/sessions\/[^/]+\/transition$/.test(url.pathname) && outgoing.postDataJSON()?.action === 'BREAK_DUE') breaks.push('BREAK_DUE')
  })
  await openWorkbench(page)
  await navigate(page, '专注')
  await expect(page.getByText('切换页面或设备睡眠时仍会计时；停工时请手动暂停或结束。净时长排除暂停与微休息。')).toBeVisible()
  await page.getByLabel('目标', { exact: true }).fill('后台真实经过时间')
  await page.getByLabel('提醒间隔（分钟）').fill('1')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeEnabled()
  const started = await (await request.get(`${apiBase}/api/focus/current`)).json() as { id: string }
  activitySignals = 0
  hidden = true
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.waitForTimeout(62_000)
  expect(hiddenCheckpoints).toBeGreaterThanOrEqual(1)
  expect(activitySignals).toBe(0)
  expect(breaks).toEqual([])
  const beforeVisible = await (await request.get(`${apiBase}/api/focus/sessions/${started.id}`)).json() as { phase: string; focusMs: number; breakMs: number }
  expect(beforeVisible.phase).toBe('RUNNING')
  expect(beforeVisible.focusMs).toBeGreaterThan(0)
  expect(beforeVisible.breakMs).toBe(0)
  hidden = false
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toBeVisible({ timeout: 15_000 })
  expect(breaks).toEqual(['BREAK_DUE'])
  const afterVisible = await (await request.get(`${apiBase}/api/focus/sessions/${started.id}`)).json() as { focusMs: number }
  expect(afterVisible.focusMs).toBeGreaterThanOrEqual(60_000)
  await expect(page.locator('.focus-recovery')).toHaveCount(0)
})

test('暂停期间的长空档不增加净专注也不补播提醒', async ({ page }) => {
  let session = { ...mockFocusSession('PAUSED'), focusMs: 600_000, pauseMs: 0, nextBreakAtMs: 690_000, reminderOrdinal: 0 }
  let hidden = false
  let hiddenCheckpoints = 0
  const actions: string[] = []
  await page.route('**/api/focus/current', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/checkpoint', async route => {
    if (hidden) { hiddenCheckpoints++; session = { ...session, version: session.version + 1, pauseMs: 90_000, focusMs: 600_000, anchorAt: new Date().toISOString() } }
    await route.fulfill({ json: session })
  })
  await page.route('**/api/focus/sessions/*/transition', async route => { actions.push((route.request().postDataJSON() as { action: string }).action); await route.fulfill({ json: session }) })
  await page.clock.install()
  await openWorkbench(page)
  await navigate(page, '专注')
  hidden = true
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.clock.fastForward(90_000)
  await expect.poll(() => hiddenCheckpoints).toBeGreaterThanOrEqual(1)
  hidden = false
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect(page.getByTestId('focus-page').getByText('已暂停')).toBeVisible()
  expect(session.focusMs).toBe(600_000)
  expect(session.pauseMs).toBe(90_000)
  expect(actions).toEqual([])
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(0)
})

test('前台目标25分钟按10分钟间隔两次休息后净1500秒总1530秒', async ({ page }) => {
  let session = { ...mockFocusSession('RUNNING'), targetMs: 1_500_000, focusMs: 0, breakMs: 0, nextBreakAtMs: 600_000, reminderOrdinal: 0, breakRemainingMs: 0 }
  const actions: string[] = []
  await page.route('**/api/focus/current', route => route.fulfill({ json: session }))
  await page.route('**/api/focus/sessions/*/checkpoint', async route => {
    session = { ...session, version: session.version + 1 }
    await route.fulfill({ json: session })
  })
  await page.route('**/api/focus/sessions/*/transition', async route => {
    const action = (route.request().postDataJSON() as { action: string }).action
    actions.push(action)
    if (action === 'BREAK_DUE') session = { ...session, phase: 'MICRO_BREAK', version: session.version + 1, reminderOrdinal: session.reminderOrdinal + 1, breakRemainingMs: 15_000, anchorAt: new Date().toISOString() }
    await route.fulfill({ json: session })
  })
  await openWorkbench(page)
  await navigate(page, '专注')
  const sync = () => page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
  session = { ...session, focusMs: 600_000, anchorAt: new Date().toISOString() }
  await sync()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(1)
  expect(actions).toEqual(['BREAK_DUE'])
  session = { ...session, phase: 'RUNNING', version: session.version + 1, breakMs: 15_000, breakRemainingMs: 0, nextBreakAtMs: 1_200_000, anchorAt: new Date().toISOString() }
  await sync()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(0)
  session = { ...session, focusMs: 1_200_000, anchorAt: new Date().toISOString() }
  await sync()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(1)
  expect(actions).toEqual(['BREAK_DUE', 'BREAK_DUE'])
  session = { ...session, phase: 'RUNNING', version: session.version + 1, breakMs: 30_000, breakRemainingMs: 0, nextBreakAtMs: 1_800_000, anchorAt: new Date().toISOString() }
  await sync()
  await expect(page.getByRole('dialog', { name: '微休息引导' })).toHaveCount(0)
  session = { ...session, phase: 'ENDED', version: session.version + 1, focusMs: 1_500_000, endedAt: new Date().toISOString(), anchorAt: new Date().toISOString() }
  await sync()
  await expect(page.getByRole('heading', { name: '本次会话已保存' })).toBeVisible()
  expect(actions).toEqual(['BREAK_DUE', 'BREAK_DUE'])
  expect(session.focusMs).toBe(1_500_000)
  expect(session.breakMs).toBe(30_000)
  expect(session.focusMs + session.breakMs).toBe(1_530_000)
})

test('结束专注只在记录列表显示投入，日报冻结来源明确标记净时长', async ({ page, request }) => {
  const started = await request.post(`${apiBase}/api/focus/sessions`, { data: {
    requestId: crypto.randomUUID(), title: '整理周会材料', taskId: null, projectId: null, targetMinutes: 25, intervalMinutes: 10,
  } })
  expect(started.status()).toBe(201)
  const session = await started.json() as { id: string; version: number }
  await new Promise(resolve => setTimeout(resolve, 1200))
  const checkpoint = await request.post(`${apiBase}/api/focus/sessions/${session.id}/checkpoint`, { data: { version: session.version } })
  expect(checkpoint.ok()).toBeTruthy()
  const confirmed = await checkpoint.json() as { version: number }
  const ended = await request.post(`${apiBase}/api/focus/sessions/${session.id}/end`, { data: { version: confirmed.version } })
  expect(ended.ok()).toBeTruthy()
  const settled = await ended.json() as { version: number }
  const progress = await request.put(`${apiBase}/api/focus/sessions/${session.id}/progress`, { data: { version: settled.version, progress: '整理了会议提纲' } })
  expect(progress.ok()).toBeTruthy()
  const today = await (await request.get(`${apiBase}/api/focus/today`)).json() as { date: string; records: Array<{ source: string; focusMs: number }> }
  expect(today.records.some(record => record.source === 'FOCUS_SESSION' && record.focusMs > 0)).toBe(true)
  const reportResponse = await request.post(`${apiBase}/api/reports`, { data: { reportType: 'DAILY', date: today.date, requestId: crypto.randomUUID() } })
  expect(reportResponse.ok()).toBeTruthy()
  const report = await reportResponse.json() as { id: string }
  await expect.poll(async () => {
    const detail = await (await request.get(`${apiBase}/api/reports/${report.id}`)).json() as { status: string; errorCode?: string; errorStage?: string; errorMessage?: string; sourceCount?: number }
    return detail.status === 'FAILED' ? JSON.stringify(detail) : detail.status
  }).toBe('SUCCEEDED')
  await openWorkbench(page)
  await expect(page.getByTestId('record-item').filter({ hasText: '整理周会材料' })).toContainText('会话计时')
  await expect(page.getByTestId('record-item').filter({ hasText: '整理周会材料' })).toContainText('系统不检测实际工作；停工请手动暂停或结束。')
  await expect(page.getByTestId('record-item').filter({ hasText: '整理周会材料' })).toContainText('进展：整理了会议提纲')
  await expect(page.getByTestId('record-list').getByText('今日专注汇总')).toHaveCount(0)
  await navigate(page, '工作汇报')
  await expect(page.getByRole('region', { name: '日报来源数据' }).locator('article strong').filter({ hasText: '会话计时' })).toBeVisible()
  await expect(page.getByRole('region', { name: '日报来源数据' }).getByText(/净时长/)).toBeVisible()
  await expect(page.getByRole('region', { name: '日报来源数据' }).getByText(/不检测实际工作；停工需手动暂停或结束/)).toBeVisible()
  await expect(page.getByRole('region', { name: '日报来源数据' }).getByText('整理了会议提纲')).toBeVisible()
  await expect(page.getByRole('region', { name: '日报来源数据' }).getByText(/不代表任务完成/)).toBeVisible()
})

test('结束已提交但响应丢失时读取权威状态且重试不重复记录', async ({ page, request }) => {
  await openWorkbench(page)
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('响应丢失的结算')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect(page.getByRole('button', { name: '提前结束并保存投入' })).toBeEnabled()
  await new Promise(resolve => setTimeout(resolve, 1200))
  const current = await (await request.get(`${apiBase}/api/focus/current`)).json() as { id: string; version: number }
  let dropped = false
  await page.route(`**/api/focus/sessions/${current.id}/end`, async route => {
    const response = await route.fetch()
    expect(response.ok()).toBeTruthy()
    dropped = true
    await route.abort('failed')
  })
  await page.getByRole('button', { name: '提前结束并保存投入' }).click()
  await expect(page.getByRole('heading', { name: '本次会话已保存' })).toBeVisible()
  await expect(page.getByText('结束请求已提交，已从服务器恢复本次投入。')).toBeVisible()
  expect(dropped).toBe(true)
  const records = async () => {
    const today = await (await request.get(`${apiBase}/api/focus/today`)).json() as { records: Array<{ sessionId: string; progress: string }> }
    return today.records.filter(record => record.sessionId === current.id)
  }
  expect(await records()).toHaveLength(1)
  const retried = await request.post(`${apiBase}/api/focus/sessions/${current.id}/end`, { data: { version: current.version } })
  expect(retried.ok()).toBeTruthy()
  expect(await records()).toHaveLength(1)
  await page.getByLabel('补充进展（可选）').fill('已整理响应丢失场景')
  await page.getByRole('button', { name: '保存进展' }).click()
  await expect.poll(async () => (await records())[0]?.progress).toBe('已整理响应丢失场景')
  await navigate(page, '工作记录')
  await expect(page.getByTestId('record-item').filter({ hasText: '响应丢失的结算' })).toContainText('会话计时')
})

test('浏览器纽约时区仍按服务端业务日显示专注记录和今日汇总', async ({ page, request }) => {
  await openWorkbench(page)
  const started = await request.post(`${apiBase}/api/focus/sessions`, { data: {
    requestId: crypto.randomUUID(), title: '跨时区业务日核对', taskId: null, projectId: null, targetMinutes: 25, intervalMinutes: 10,
  } })
  expect(started.status()).toBe(201)
  const session = await started.json() as { id: string; version: number }
  await new Promise(resolve => setTimeout(resolve, 1100))
  expect((await request.post(`${apiBase}/api/focus/sessions/${session.id}/end`, { data: { version: session.version } })).ok()).toBeTruthy()
  const today = await (await request.get(`${apiBase}/api/focus/today`)).json() as { date: string; records: Array<{ sessionId: string; businessDate: string }> }
  expect(today.records.some(record => record.sessionId === session.id && record.businessDate === today.date)).toBe(true)
  const browser = page.context().browser()
  if (!browser) throw new Error('Browser is unavailable')
  const newYork = await browser.newContext({ baseURL: 'http://127.0.0.1:15173', timezoneId: 'America/New_York', storageState: await page.context().storageState() })
  try {
    const nyPage = await newYork.newPage()
    await nyPage.goto('/')
    await expect(nyPage.getByTestId('workbench')).toBeVisible()
    expect(await nyPage.evaluate(() => Intl.DateTimeFormat().resolvedOptions().timeZone)).toBe('America/New_York')
    await expect(nyPage.getByTestId('record-item').filter({ hasText: '跨时区业务日核对' })).toContainText('会话计时')
    await navigate(nyPage, '专注')
    await nyPage.getByRole('tab', { name: '今日汇总' }).click()
    await expect(nyPage.locator('.focus-today')).toContainText(today.date)
    await expect(nyPage.locator('.focus-today')).toContainText('跨时区业务日核对')
  } finally { await newYork.close() }
})

test('四区按需挂载并保留草稿筛选，AI刷新保留待办上下文', async ({ page, request }) => {
  await createProject(request)
  await openWorkbench(page)
  await expect(page.getByTestId('task-panel')).toHaveCount(0)
  await expect(page.getByTestId('daily-report')).toHaveCount(0)
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await page.getByTestId('record-content').fill('跨区保留的手工草稿')
  await navigate(page, '待办任务')
  await page.getByLabel('优先级筛选').selectOption('LOW')
  await navigate(page, '工作记录')
  await expect(page.getByTestId('record-content')).toHaveValue('跨区保留的手工草稿')
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: 'AI 快记' }).click()
  await page.getByTestId('capture-content').fill('[E2E_MULTI] 完成工作并安排后续')
  let captureWrites = 0
  page.on('request', outgoing => { if (outgoing.method() === 'POST' && new URL(outgoing.url()).pathname === '/api/inputs') captureWrites++ })
  await page.getByTestId('capture-content').dispatchEvent('keydown', { key: 'Enter', metaKey: true, isComposing: true })
  expect(captureWrites).toBe(0)
  await page.getByTestId('capture-submit').click()
  await expect(page.getByText('已生成 2 条工作记录和 2 项待办。')).toBeVisible()
  expect(captureWrites).toBe(1)
  await navigate(page, '待办任务')
  await expect(page.getByLabel('优先级筛选')).toHaveValue('LOW')
  await page.getByLabel('优先级筛选').selectOption('')
  await expect(page.getByTestId('task-item')).toHaveCount(2)
  await navigate(page, '工作记录')
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await expect(page.getByTestId('record-content')).toHaveValue('跨区保留的手工草稿')
})

test('手工记录IME不误提交，Command快捷提交防重，抽屉取消保留修改和焦点', async ({ page, request }) => {
  await openWorkbench(page)
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  const input = page.getByTestId('record-content')
  await input.fill('输入法和防重测试')
  let writes = 0
  page.on('request', outgoing => { if (outgoing.method() === 'POST' && new URL(outgoing.url()).pathname === '/api/records') writes++ })
  await input.dispatchEvent('keydown', { key: 'Enter', ctrlKey: true, isComposing: true })
  await expect(page.getByTestId('record-item')).toHaveCount(0)
  expect(writes).toBe(0)
  const held = await holdNextResponse(page, '**/api/records', 'POST')
  await input.press('Meta+Enter')
  await held.wait()
  await input.press('Control+Enter')
  expect(writes).toBe(1)
  held.release()
  await expect(page.getByTestId('record-item')).toHaveCount(1)
  const edit = page.getByTestId('record-item').getByRole('button', { name: '编辑' })
  await edit.click()
  const drawer = page.getByRole('dialog', { name: '编辑工作记录' })
  await expect(drawer.getByTestId('record-content')).toBeFocused()
  await drawer.getByTestId('record-content').fill('尚未保存修改')
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '放弃这次修改？' }).getByRole('button', { name: '取消' }).click()
  await expect(drawer.getByTestId('record-content')).toHaveValue('尚未保存修改')
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '放弃这次修改？' }).getByRole('button', { name: '放弃修改' }).click()
  await expect(edit).toBeFocused()
  expect((await (await request.get(`${apiBase}/api/records`)).json())[0].content).toBe('输入法和防重测试')
})

test('四区单屏滚动与编辑抽屉在桌面、手机和矮窗口可用', async ({ page, request }, testInfo) => {
  for (let index = 0; index < 6; index++) await createProject(request, `响应式项目-${index}`)
  for (let index = 0; index < 6; index++) {
    await request.post(`${apiBase}/api/tasks`, { data: {
      title: `响应式任务标题-${index}`, notes: '测试抽屉布局。'.repeat(8), priority: 'HIGH',
    } })
  }
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  for (let index = 0; index < 8; index++) {
    await request.post(`${apiBase}/api/records`, { data: { projectId: null,
      content: `响应式记录-${index}：完成四工作区接入，核对正文和来源的响应式布局。`,
      occurredAt: `${date}T09:00:00+08:00` } })
  }
  const response = await request.post(`${apiBase}/api/reports`, { data: { reportType: 'DAILY', date, requestId: crypto.randomUUID() } })
  expect(response.ok()).toBeTruthy()
  const report = await response.json()
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/reports/${report.id}`)).json()).status).toBe('SUCCEEDED')
  const pagedRequests: string[] = []
  page.on('request', outgoing => {
    if (/\/api\/(?:projects|records|tasks|reports)(?:\/[^/]+\/sources)?\/page/.test(outgoing.url())) pagedRequests.push(outgoing.url())
  })
  await openWorkbench(page)
  for (const [width, height] of [[320, 520], [390, 900], [768, 520], [1024, 900], [1440, 900]]) {
    await page.setViewportSize({ width, height })
    for (const [name, slug] of [['工作记录', 'records'], ['待办任务', 'tasks'], ['工作汇报', 'reports'], ['项目管理', 'projects']]) {
      await navigate(page, name)
      await expect(page.getByRole('heading', { name, exact: true, level: 1 })).toBeVisible()
      if (slug === 'reports') {
        await expect(page.getByTestId('daily-content')).toBeVisible()
        await expect(page.locator('.reports-page > .page-header').getByRole('group', { name: '报告类型' })).toBeVisible()
        await expect(page.getByText('生成内容可核对')).toHaveCount(0)
        await expect(page.getByText('选日期 → 生成版本 → 编辑并保存')).toHaveCount(0)
      }
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight <= window.innerHeight + 1)).toBe(true)
      const content = slug === 'reports' ? page.locator('.reports-page .report-scroll') : page.locator(`.${slug}-page .workspace-scroll`)
      const control = slug === 'reports' ? page.locator('.report-controls') : slug === 'tasks'
        ? page.locator('.task-controls') : slug === 'projects' ? page.locator('.project-controls') : page.locator('.date-navigation')
      await expect.poll(() => content.evaluate(element => element.clientHeight > 0)).toBe(true)
      if (width === 1440 && slug === 'projects') {
        await expect(page.locator('.project-list .project')).toHaveCount(5)
        await expect.poll(() => content.evaluate(element => element.scrollHeight <= element.clientHeight + 1)).toBe(true)
      }
      if (width === 1440 && slug === 'tasks') {
        const [status, project, priority, due] = await Promise.all([
          page.getByRole('group', { name: '任务状态' }).boundingBox(),
          page.getByLabel('项目筛选').boundingBox(), page.getByLabel('优先级筛选').boundingBox(),
          page.getByLabel('截止日期筛选').boundingBox(),
        ])
        const filterRows = await page.locator('.task-controls .task-filters label').evaluateAll(labels =>
          labels.map(label => Math.round(label.getBoundingClientRect().top)))
        expect(Math.max(...filterRows) - Math.min(...filterRows)).toBeLessThan(2)
        expect(Math.abs((status?.y ?? 0) - filterRows[0])).toBeLessThan(2)
        expect((project?.x ?? 0)).toBeGreaterThan((status?.x ?? 0) + (status?.width ?? 0))
        expect((priority?.x ?? 0)).toBeGreaterThan((project?.x ?? 0) + (project?.width ?? 0))
        expect((due?.x ?? 0)).toBeGreaterThan((priority?.x ?? 0) + (priority?.width ?? 0))
      }
      await expect.poll(() => content.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      await expect.poll(() => control.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
      const before = await control.boundingBox()
      await content.evaluate(element => { element.scrollTop = element.scrollHeight })
      await expect.poll(() => content.evaluate(element => element.scrollTop + element.clientHeight >= element.scrollHeight - 2)).toBe(true)
      const after = await control.boundingBox()
      expect(Math.round(after?.y ?? -1)).toBe(Math.round(before?.y ?? -2))
      expect(after?.y ?? height).toBeLessThan(height)
      expect((after?.y ?? height) + (after?.height ?? 0)).toBeLessThanOrEqual(height + 1)
      const lastAction = slug === 'records' ? content.locator('.workflow-footer') : slug === 'tasks'
        ? content.locator('.task-panel > .pagination') : slug === 'projects' ? content.locator('.projects > .pagination')
          : content.locator('.report-sources .pagination')
      if (slug === 'reports') {
        await page.getByTestId('daily-report').getByRole('region', { name: '日报来源数据' })
          .evaluate(element => { element.scrollTop = element.scrollHeight })
      }
      await expect(lastAction).toBeVisible()
      expect((await lastAction.boundingBox())?.y ?? height).toBeLessThan(height)
      if (height === 520 && slug !== 'records') {
        const finalControl = slug === 'reports' ? control.getByLabel('日报日期') : slug === 'tasks'
          ? control.getByLabel('截止日期筛选') : control.getByLabel('搜索项目列表')
        await control.evaluate(element => { element.scrollTop = element.scrollHeight })
        const controlBounds = await control.boundingBox()
        const actionBounds = await finalControl.boundingBox()
        expect(actionBounds?.y ?? 0).toBeGreaterThanOrEqual((controlBounds?.y ?? 0) - 1)
        expect((actionBounds?.y ?? height) + (actionBounds?.height ?? 0)).toBeLessThanOrEqual((controlBounds?.y ?? 0) + (controlBounds?.height ?? 0) + 1)
      }
      if (width === 320) {
        if (slug === 'records') await assertPinnedPager(page.getByRole('region', { name: '记录数据' }),
          page.getByTestId('record-list').getByRole('navigation', { name: '分页' }), page.getByTestId('record-item').last())
        if (slug === 'tasks') await assertPinnedPager(page.getByRole('region', { name: '待办数据' }),
          page.getByTestId('task-panel').getByRole('navigation', { name: '分页' }), page.getByTestId('task-item').last())
        if (slug === 'projects') await assertPinnedPager(page.getByRole('region', { name: '项目数据' }),
          page.locator('.projects > .pagination'), page.locator('.project-list .project').last())
        if (slug === 'reports') await assertPinnedPager(content, page.getByTestId('daily-report').locator(':scope > .pagination'))
      }
      await page.screenshot({ path: testInfo.outputPath(`${slug}-${width}.png`), fullPage: true })
    }
    await navigate(page, '待办任务')
    await page.getByTestId('task-item').first().getByRole('button', { name: '编辑' }).click()
    const drawer = page.getByRole('dialog', { name: '编辑待办' })
    await expect(drawer.getByTestId('task-title')).toBeFocused()
    await expect.poll(() => drawer.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
    if (width <= 390) {
      await expect.poll(async () => (await drawer.boundingBox())?.x).toBe(0)
      await expect.poll(async () => Math.round((await drawer.boundingBox())?.width ?? 0)).toBe(width)
    }
    await page.screenshot({ path: testInfo.outputPath(`drawer-${width}.png`) })
    await page.keyboard.press('Escape')
    await expect(drawer).toHaveCount(0)
  }
  await page.setViewportSize({ width: 320, height: 520 })
  await navigate(page, '工作记录')
  await page.locator('.records-page .workspace-scroll').evaluate(element => { element.scrollTop = element.scrollHeight })
  await navigate(page, '待办任务')
  await navigate(page, '工作记录')
  await expect.poll(() => page.locator('.records-page .workspace-scroll').evaluate(element => element.scrollTop)).toBe(0)
  await page.setViewportSize({ width: 1440, height: 400 })
  await openSection(page, 'daily')
  await assertIndependentReportPanes(page, 'daily')
  await page.setViewportSize({ width: 1440, height: 700 })
  const sourceToggle = page.getByTestId('daily-report').getByRole('button', { name: /核对本版本来源/ })
  await sourceToggle.click()
  await expect(sourceToggle).toHaveAttribute('aria-expanded', 'false')
  await expect(page.getByRole('region', { name: '日报来源数据' })).toHaveCount(0)
  await sourceToggle.click()
  await expect(sourceToggle).toHaveAttribute('aria-expanded', 'true')
  await page.getByTestId('daily-report').locator('.report-sources > .pagination').getByRole('button', { name: '下一页' }).click()
  await expect(page.getByRole('region', { name: '日报来源数据' })).toContainText('来源 6 ·')
  await page.setViewportSize({ width: 320, height: 520 })
  await openSection(page, 'weekly')
  const weeklyControls = page.getByTestId('weekly-report').getByRole('region', { name: '周报操作' })
  await weeklyControls.evaluate(element => { element.scrollTop = element.scrollHeight })
  await expect(weeklyControls.getByLabel('周报历史版本')).toBeInViewport()
  await expect.poll(() => page.getByTestId('weekly-report').getByRole('region', { name: '周报内容' }).evaluate(element => element.clientHeight > 0)).toBe(true)
  await page.locator('.reports-page .retained-view:not([hidden]) .report-scroll').evaluate(element => { element.scrollTop = element.scrollHeight })
  await navigate(page, '工作记录')
  await navigate(page, '工作汇报')
  await expect.poll(() => page.locator('.reports-page .retained-view:not([hidden]) .report-scroll').evaluate(element => element.scrollTop)).toBe(0)
  await page.setViewportSize({ width: 390, height: 900 })
  await openSection(page, 'daily')
  const dailyControlHeight = (await page.getByTestId('daily-report').getByRole('region', { name: '日报操作' }).boundingBox())?.height ?? 0
  await openSection(page, 'weekly')
  const weeklyControlHeight = (await page.getByTestId('weekly-report').getByRole('region', { name: '周报操作' }).boundingBox())?.height ?? 0
  expect(weeklyControlHeight).toBeLessThan(dailyControlHeight)
  await expect(page.getByLabel('周报所在日期')).toBeInViewport()
  await expect(page.getByRole('button', { name: '生成新周报' })).toBeInViewport()
  expect(pagedRequests.length).toBeGreaterThan(0)
  expect(new Set(pagedRequests.map(url => new URL(url).pathname).filter(path => !path.includes('/sources/')))).toEqual(new Set([
    '/api/projects/page', '/api/records/page', '/api/tasks/page', '/api/reports/page',
  ]))
  expect(pagedRequests.every(url => new URL(url).searchParams.get('size') === '5')).toBe(true)
  await expect(page.getByLabel('每页')).toHaveCount(0)
})

test('日报接收其他窗口删除事件并清理已删除的恢复跟踪', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  const response = await request.post(`${apiBase}/api/reports`, { data: { reportType: 'DAILY', date, requestId: crypto.randomUUID() } })
  const report = await response.json()
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/reports/${report.id}`)).json()).status).toBe('SUCCEEDED')
  const session = await page.context().newCDPSession(page)
  await session.send('Network.enable')
  const businessSockets = new Set<string>()
  session.on('Network.webSocketCreated', event => {
    if (new URL(event.url).pathname === '/ws/events') businessSockets.add(event.requestId)
  })
  const connected = new Promise<void>(resolve => session.on('Network.webSocketHandshakeResponseReceived', event => {
    if (businessSockets.has(event.requestId) && event.response.status === 101) resolve()
  }))
  await openWorkbench(page); await openSection(page, 'daily')
  const panel = page.getByTestId('daily-report')
  await expect(panel.getByTestId('daily-content')).toBeVisible()
  await connected // Exercise a connected push, not the separate 15-second reconnect fallback.
  const latest = await (await request.get(`${apiBase}/api/reports/${report.id}`)).json()
  expect((await request.delete(`${apiBase}/api/reports/${report.id}?version=${latest.version}`)).status()).toBe(204)
  await expect(panel.getByTestId('daily-content')).toHaveCount(0)
  await expect(panel.getByLabel('日报历史版本')).toContainText('暂无历史版本')
  const storageKey = await pendingStorageKey(page)
  await page.evaluate(({ key, id }) => localStorage.setItem(key, JSON.stringify([`REPORT:${id}`])), { key: storageKey, id: report.id })
  await page.reload()
  await expect.poll(() => page.evaluate(key => localStorage.getItem(key), storageKey)).toBe('[]')
  await session.detach()
})

test('日报删除单个版本保留其他版本，取消保留草稿，错误可重试，最后版本为空', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  const ids: string[] = []
  for (let i = 0; i < 2; i++) {
    const response = await request.post(`${apiBase}/api/reports`, { data: { reportType: 'DAILY', date, requestId: crypto.randomUUID() } })
    expect(response.ok()).toBeTruthy()
    ids.push((await response.json()).id)
  }
  await openWorkbench(page); await openSection(page, 'daily')
  const panel = page.getByTestId('daily-report')
  const body = panel.getByTestId('daily-content')
  await expect(body).toBeVisible()
  await body.fill('删除前未保存草稿')
  await panel.getByRole('button', { name: '删除此版本' }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('未保存')
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(body).toHaveValue('删除前未保存草稿')
  const selectedId = await panel.getByLabel('日报历史版本').inputValue()
  const endpoint = `**/api/reports/${selectedId}?version=*`
  await page.route(endpoint, route => route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: '日报已被其他操作修改，请刷新后重试' }) }))
  await panel.getByRole('button', { name: '删除此版本' }).click()
  await dialog.getByRole('button', { name: '删除此版本' }).click()
  await expect(dialog.getByRole('alert')).toContainText('其他操作修改')
  await expect(body).toHaveValue('删除前未保存草稿')
  await page.unroute(endpoint)
  await dialog.getByRole('button', { name: '删除此版本' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(panel.getByLabel('日报历史版本').locator('option')).toHaveCount(2)
  expect((await request.get(`${apiBase}/api/reports/${selectedId}`)).status()).toBe(404)
  expect((await request.get(`${apiBase}/api/reports/${ids.find(id => id !== selectedId)}`)).ok()).toBeTruthy()
  await panel.getByRole('button', { name: '删除此版本' }).click()
  await dialog.getByRole('button', { name: '删除此版本' }).click()
  await expect(body).toHaveCount(0)
  await expect(panel.getByLabel('日报历史版本')).toContainText('暂无历史版本')
  await expect(panel.getByRole('navigation', { name: '分页', exact: true })).toHaveCount(0)
})

test('失败日报可删除并保留导致失败的原记录', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  // Deterministic gateway echoes this source; the 1000-character bullet contract rejects it.
  const recordResponse = await request.post(`${apiBase}/api/records`, { data: { projectId: null, content: '长来源'.repeat(400), occurredAt: `${date}T04:00:00Z` } })
  expect(recordResponse.ok()).toBeTruthy()
  const record = await recordResponse.json()
  const response = await request.post(`${apiBase}/api/reports`, { data: { reportType: 'DAILY', date, requestId: crypto.randomUUID() } })
  const report = await response.json()
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/reports/${report.id}`)).json()).status).toBe('FAILED')
  await openWorkbench(page); await openSection(page, 'daily')
  const panel = page.getByTestId('daily-report')
  await panel.getByRole('button', { name: '删除此版本' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除此版本' }).click()
  await expect(panel.getByLabel('日报历史版本')).toContainText('暂无历史版本')
  expect((await request.get(`${apiBase}/api/reports/${report.id}`)).status()).toBe(404)
  expect((await request.get(`${apiBase}/api/records/${record.id}`)).ok()).toBeTruthy()
})

test('项目弹窗统一样式，取消无写入，错误保留名称，提交期间防重复', async ({ page, request }) => {
  const project = await createProject(request, '待调整名称')
  const nativeDialogs: string[] = []
  page.on('dialog', async dialog => { nativeDialogs.push(dialog.type()); await dialog.dismiss() })
  await openWorkbench(page)
  await navigate(page, '项目管理')
  const row = page.locator('.project').filter({ hasText: '待调整名称' })
  await row.getByRole('button', { name: '改名' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('项目名称').fill('取消的名称')
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '改名' }).click()
  const endpoint = `**/api/projects/${project.id}`
  await page.route(endpoint, route => route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: '同名的活动项目已存在' }) }))
  await dialog.getByLabel('项目名称').fill('整理后的名称')
  await dialog.getByRole('button', { name: '保存名称' }).click()
  await expect(dialog.getByRole('alert')).toContainText('同名')
  await expect(dialog.getByLabel('项目名称')).toHaveValue('整理后的名称')
  await page.unroute(endpoint)
  const held = await holdNextResponse(page, endpoint, 'PATCH')
  await dialog.getByRole('button', { name: '保存名称' }).click()
  await held.wait()
  await expect(dialog.getByRole('button', { name: '正在保存' })).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeVisible()
  held.release()
  await expect(dialog).toHaveCount(0)
  const renamed = page.locator('.project').filter({ hasText: '整理后的名称' })
  await renamed.getByRole('button', { name: '归档' }).click()
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(renamed).toBeVisible()
  await renamed.getByRole('button', { name: '归档' }).click()
  await dialog.getByRole('button', { name: '确认归档' }).click()
  await expect(renamed).toHaveCount(0)
  expect(nativeDialogs).toEqual([])
})

async function holdNextResponse(page: Page, url: string, method = 'GET') {
  let release = () => {}
  const gate = new Promise<void>(resolve => { release = resolve })
  let captured = false
  await page.route(url, async route => {
    if (captured || route.request().method() !== method) return route.continue()
    captured = true
    const response = await route.fetch()
    await gate
    await route.fulfill({ response }).catch(() => undefined) // A newer query may abort this fetch.
  })
  return { release, wait: () => expect.poll(() => captured).toBe(true) }
}

test('列表刷新迟到不覆盖新日期或筛选，失败后分页解除加载', async ({ page, request }) => {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  for (const [date, content] of [[today, '旧日期记录'], ['2080-01-02', '新日期记录']]) {
    expect((await request.post(`${apiBase}/api/records`, { data: { projectId: null, content, occurredAt: `${date}T09:00:00+08:00` } })).ok()).toBeTruthy()
  }
  for (const priority of ['HIGH', 'LOW']) {
    expect((await request.post(`${apiBase}/api/tasks`, { data: { title: `优先级-${priority}`, priority } })).ok()).toBeTruthy()
  }
  await createProject(request, '保留项目')
  await createProject(request, '待归档项目')
  await openWorkbench(page)

  const records = page.getByTestId('record-list')
  await expect(records.getByRole('navigation', { name: '分页', exact: true })).toHaveAttribute('aria-busy', 'false')
  const oldRecords = await holdNextResponse(page, '**/api/records/page**')
  await records.getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()
  await oldRecords.wait()
  await expect(records.getByRole('navigation', { name: '分页', exact: true })).toHaveAttribute('aria-busy', 'true')
  await page.getByLabel('查看日期').fill('2080-01-02')
  await expect(records).toContainText('新日期记录')
  oldRecords.release()
  await expect(records.getByRole('navigation', { name: '分页', exact: true })).toHaveAttribute('aria-busy', 'false')
  await expect(records.getByTestId('record-item')).toHaveCount(1)

  await navigate(page, '待办任务')
  const tasks = page.getByTestId('task-panel')
  const oldTasks = await holdNextResponse(page, '**/api/tasks/page**')
  await tasks.getByTestId('task-item').filter({ hasText: '优先级-LOW' }).getByRole('button', { name: '删除', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()
  await oldTasks.wait()
  await tasks.getByLabel('优先级筛选').selectOption('HIGH')
  await expect(tasks.getByRole('navigation', { name: '分页', exact: true })).toHaveAttribute('aria-busy', 'false')
  oldTasks.release()
  await expect(tasks.getByTestId('task-item')).toHaveCount(1)
  await expect(tasks.getByTestId('task-item')).toContainText('优先级-HIGH')

  await navigate(page, '项目管理')
  const projects = page.locator('.projects')
  const oldProjects = await holdNextResponse(page, '**/api/projects/page**')
  await projects.locator('.project').filter({ hasText: '待归档项目' }).getByRole('button', { name: '归档', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认归档' }).click()
  await oldProjects.wait()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByLabel('搜索项目列表').fill('不存在')
  await expect(projects.locator('.project')).toHaveCount(0)
  oldProjects.release()
  await expect(projects.locator('.project')).toHaveCount(0)
  await page.route('**/api/projects/page**', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ detail: '测试列表失败' }) }))
  await page.getByLabel('搜索项目列表').fill('失败')
  await expect(page.getByRole('alert').first()).toContainText('测试列表失败')
  await expect(projects.getByRole('navigation', { name: '分页', exact: true })).toHaveCount(0)
  await page.unroute('**/api/projects/page**')
  await projects.getByRole('button', { name: '重新加载' }).click()
  await expect(projects.getByRole('alert')).toHaveCount(0)
})

test('报告保存迟到不覆盖继续输入或切换后的版本', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  for (const reportType of ['DAILY', 'WEEKLY']) {
    for (let index = 0; index < 2; index++) expect((await request.post(`${apiBase}/api/reports`, {
      data: { reportType, date, requestId: crypto.randomUUID() },
    })).ok()).toBeTruthy()
  }
  await openWorkbench(page)
  for (const kind of ['daily', 'weekly']) {
    await openSection(page, kind)
    const panel = page.getByTestId(`${kind}-report`)
    const body = panel.getByTestId(`${kind}-content`)
    const history = panel.getByLabel(kind === 'daily' ? '日报历史版本' : '周报历史版本')
    const saveButton = panel.getByRole('button', { name: kind === 'daily' ? '保存正文' : '保存 AI 正文', exact: true })
    await expect(body).toBeVisible()
    const id = await history.inputValue()
    const first = await holdNextResponse(page, `**/api/reports/${id}`, 'PATCH')
    await body.fill('发送保存的正文')
    await saveButton.click()
    await first.wait()
    await body.fill('保存过程中继续编辑')
    first.release()
    await expect(saveButton).toBeEnabled()
    await expect(body).toHaveValue('保存过程中继续编辑')

    const second = await holdNextResponse(page, `**/api/reports/${id}`, 'PATCH')
    await saveButton.click()
    await second.wait()
    // PATCH is already committed while its response is held; WebSocket may
    // reconcile that saved text. Make a genuinely unsaved edit before navigation.
    await body.fill('切换前尚未保存的新修改')
    const otherId = await history.locator('option').evaluateAll(options => options.map(option => (option as HTMLOptionElement).value).filter(Boolean)).then(ids => ids.find(value => value !== id)!)
    await history.selectOption(otherId)
    await page.getByRole('dialog').getByRole('button', { name: '放弃修改' }).click()
    await expect(history).toHaveValue(otherId)
    await body.fill('新版本自己的草稿')
    second.release()
    await expect(saveButton).toBeEnabled()
    await expect(history).toHaveValue(otherId)
    await expect(body).toHaveValue('新版本自己的草稿')
  }
})

test('页面空态、键盘提交、长文本、记录补记编辑删除与刷新持久化', async ({ page }) => {
  await openWorkbench(page)
  await expect(page.getByText('给这一天留下第一条记录')).toBeVisible()
  await navigate(page, '待办任务')
  await expect(page.getByText('这里暂时没有待办')).toBeVisible()
  await navigate(page, '工作记录')
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()

  const longText = `补记昨天工作：${'长文本可编辑。'.repeat(80)}`
  await page.getByTestId('record-content').fill(longText)
  await page.getByTestId('record-content').press('Control+Enter')
  await expect(page.getByRole('status').filter({ hasText: '工作记录已保存' })).toBeVisible()
  await expect(page.getByTestId('record-item')).toContainText(longText)

  await page.getByTestId('record-item').getByRole('button', { name: '编辑' }).click()
  await page.getByRole('dialog').getByTestId('record-content').fill(`${longText}（已修正）`)
  await page.getByRole('dialog').getByTestId('record-submit').click()
  await expect(page.getByTestId('record-item')).toContainText('（已修正）')
  await page.reload()
  await expect(page.getByTestId('record-item')).toContainText('（已修正）')

  await page.getByTestId('record-item').getByRole('button', { name: '删除' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByText('给这一天留下第一条记录')).toBeVisible()
})

test('统一输入覆盖混合拆分、默认值、失败重试、撤销和修改冲突', async ({ page, request }) => {
  await createProject(request)
  await openWorkbench(page)

  await page.getByTestId('capture-content').fill('[E2E_MULTI] 今天完成联调，并安排两项后续')
  await page.getByTestId('capture-content').press('Control+Enter')
  await expect(page.getByText('已生成 2 条工作记录和 2 项待办。')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByTestId('record-item')).toHaveCount(2)
  await navigate(page, '待办任务')
  await expect(page.getByTestId('task-item')).toHaveCount(2)
  await navigate(page, '工作记录')

  await page.getByTestId('capture-revert').click()
  await page.getByRole('dialog').getByRole('button', { name: '确认撤销' }).click()
  await expect(page.getByText('本批次已撤销')).toBeVisible()
  await expect(page.getByTestId('record-item')).toHaveCount(0)
  await expect(page.getByTestId('task-item')).toHaveCount(0)

  await page.getByTestId('capture-content').fill('[E2E_FAIL_ONCE] [E2E_YESTERDAY] [E2E_UNKNOWN_PROJECT] [E2E_UNKNOWN_PRIORITY] 昨天完成修复，明天补测试')
  await page.getByTestId('capture-submit').click()
  await expect(page.getByTestId('capture-failed')).toBeVisible({ timeout: 15_000 })
  await page.getByRole('button', { name: '保留原时间基准重试' }).click()
  await expect(page.getByText('已生成 1 条工作记录和 1 项待办。')).toBeVisible({ timeout: 15_000 })
  const yesterday = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' })
    .format(new Date(Date.now() - 86_400_000))
  await expect(page.getByLabel('查看日期')).toHaveValue(yesterday)
  await expect(page.getByTestId('task-item')).toContainText('中优先级')
  await expect(page.getByTestId('task-item')).toContainText('未归属项目')

  await page.getByTestId('record-item').getByRole('button', { name: '编辑' }).click()
  await page.getByTestId('record-content').fill('用户后续修改，撤销不得覆盖')
  await page.getByTestId('record-submit').click()
  await page.getByTestId('capture-revert').click()
  await page.getByRole('dialog').getByRole('button', { name: '确认撤销' }).click()
  await expect(page.getByRole('alert')).toContainText('已被编辑')
  await expect(page.getByTestId('record-item')).toContainText('用户后续修改')
})

test('待办筛选、乐观锁、完整状态循环与自动完成事实', async ({ page, request }) => {
  const project = await createProject(request)
  await openWorkbench(page)
  await navigate(page, '待办任务')
  await page.getByRole('button', { name: '新建待办', exact: true }).first().click()
  await page.getByTestId('task-title').fill('高优先级发布任务')
  await page.getByRole('dialog').getByTestId('project-picker').selectOption(project.id)
  await page.getByTestId('task-priority').selectOption('HIGH')
  await page.getByTestId('task-title').press('Control+Enter')
  await expect(page.getByTestId('task-item')).toContainText('高优先级发布任务')

  await page.getByTestId('task-item').getByRole('button', { name: '编辑' }).click()
  await page.getByTestId('task-title').fill('高优先级发布任务（已编辑）')
  await page.getByRole('dialog').getByRole('button', { name: '保存修改' }).click()
  await expect(page.locator('.viewport-toast')).toContainText('待办已更新')
  await page.reload()
  await expect(page.getByTestId('task-item')).toContainText('（已编辑）')

  await page.getByLabel('优先级筛选').selectOption('HIGH')
  await expect(page.getByTestId('task-item')).toHaveCount(1)
  await page.getByLabel('项目筛选').selectOption(project.id)
  await expect(page.getByTestId('task-item')).toHaveCount(1)
  await page.getByLabel('项目筛选').selectOption('UNASSIGNED')
  await expect(page.getByText('这里暂时没有待办')).toBeVisible()
  await page.getByLabel('项目筛选').selectOption('')
  await page.getByLabel('优先级筛选').selectOption('LOW')
  await expect(page.getByText('这里暂时没有待办')).toBeVisible()
  await page.getByLabel('优先级筛选').selectOption('')

  const taskResponse = await request.get(`${apiBase}/api/tasks?status=PENDING`)
  const [task] = await taskResponse.json() as Array<{ id: string; version: number; title: string }>
  const stale = await request.put(`${apiBase}/api/tasks/${task.id}`, { data: {
    projectId: project.id, title: '并发写入', notes: '', dueAt: null, priority: 'HIGH', version: task.version + 1,
  } })
  expect(stale.status()).toBe(409)
  expect((await stale.json()).detail).toContain('刷新后重试')

  await page.getByTestId('task-item').getByRole('button', { name: /^完成任务：/ }).click()
  await page.getByRole('dialog').getByLabel('完成结果').fill('完成结果')
  await page.getByRole('dialog').getByRole('button', { name: '确认完成' }).click()
  await expect(page.locator('.viewport-toast')).toContainText('待办已完成')
  await page.getByRole('group', { name: '任务状态' }).getByRole('button', { name: '已完成', exact: true }).click()
  await expect(page.getByTestId('task-item').getByRole('button', { name: /^重开任务：/ })).toBeVisible()
  await navigate(page, '工作记录')
  await expect(page.getByTestId('record-item')).toContainText('任务完成')
  await navigate(page, '待办任务')

  const completed = await (await request.get(`${apiBase}/api/tasks/${task.id}`)).json() as { version: number }
  const repeated = await request.post(`${apiBase}/api/tasks/${task.id}/complete`, { data: { version: completed.version, result: '不得覆盖' } })
  expect(repeated.ok()).toBeTruthy()
  await page.getByTestId('task-item').getByRole('button', { name: /^重开任务：/ }).click()
  await expect(page.locator('.viewport-toast')).toContainText('待办已重开')
  await page.getByRole('group', { name: '任务状态' }).getByRole('button', { name: '待处理', exact: true }).click()
  await expect(page.getByTestId('task-item').getByRole('button', { name: /^完成任务：/ })).toBeVisible()
  await expect(page.getByTestId('record-item')).toHaveCount(0)
  await page.getByTestId('task-item').getByRole('button', { name: /^完成任务：/ }).click()
  await page.getByRole('dialog').getByLabel('完成结果').fill('再次完成')
  await page.getByRole('dialog').getByRole('button', { name: '确认完成' }).click()
  await expect(page.locator('.viewport-toast')).toContainText('待办已完成')
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/records`)).json() as unknown[]).length).toBe(1)
})

test('完成对话框支持取消、空结果、多行补充与失败保留', async ({ page, request }, testInfo) => {
  const response = await request.post(`${apiBase}/api/tasks`, { data: { title: '阅读第一章并整理笔记', notes: '', priority: 'MEDIUM' } })
  const task = await response.json() as { id: string }
  await openWorkbench(page)
  await navigate(page, '待办任务')
  const complete = page.getByTestId('task-item').getByRole('button', { name: /^完成任务：/ })
  await complete.click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('完成结果')).toBeFocused()
  await dialog.getByLabel('完成结果').fill('已经完成阅读第一章')
  await page.screenshot({ path: testInfo.outputPath('completion-dialog-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: testInfo.outputPath('completion-dialog-mobile.png') })
  await page.setViewportSize({ width: 1280, height: 720 })
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(complete).toBeFocused()
  expect((await (await request.get(`${apiBase}/api/tasks/${task.id}`)).json()).status).toBe('PENDING')
  await complete.click()
  await dialog.getByRole('button', { name: '取消' }).click()
  await complete.click()
  await expect(dialog.getByLabel('完成结果')).toHaveValue('')
  await dialog.getByRole('button', { name: '确认完成' }).click()
  await expect(dialog).toHaveCount(0)
  await page.getByRole('group', { name: '任务状态' }).getByRole('button', { name: '已完成', exact: true }).click()
  await page.getByTestId('task-item').getByRole('button', { name: '补充结果' }).click()
  await dialog.getByLabel('完成结果').fill('第一条笔记')
  await dialog.getByLabel('完成结果').press('Enter')
  await expect(dialog.getByLabel('完成结果')).toHaveValue('第一条笔记\n')
  const endpoint = `**/api/tasks/${task.id}/completion-result`
  await page.route(endpoint, route => route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: '测试保存冲突，请重试' }) }))
  await dialog.getByRole('button', { name: '保存结果' }).click()
  await expect(dialog.getByRole('alert')).toContainText('测试保存冲突')
  await expect(dialog.getByLabel('完成结果')).toHaveValue('第一条笔记\n')
  await page.unroute(endpoint)
  await dialog.getByLabel('完成结果').fill('第一条笔记\n第二条笔记')
  await dialog.getByLabel('完成结果').press('Control+Enter')
  await expect(dialog).toHaveCount(0)
  await page.getByTestId('task-item').getByRole('button', { name: '修改结果' }).click()
  await expect(dialog.getByLabel('完成结果')).toHaveValue('第一条笔记\n第二条笔记')
  await dialog.getByRole('button', { name: '取消' }).click()
  expect((await (await request.get(`${apiBase}/api/tasks/${task.id}`)).json()).completionResult).toBe('第一条笔记\n第二条笔记')
})

test('日报周报来源、版本、编辑、复制和 dirty guard', async ({ page, request, context }) => {
  await page.addInitScript(() => {
    localStorage.setItem('ai-workbench.panel.v1.weekly', 'broken-value')
    localStorage.setItem('ai-workbench.pending.v1', '{"bad":true}')
  })
  let automaticReportPosts = 0
  page.on('request', outgoing => { if (outgoing.method() === 'POST' && outgoing.url().includes('/api/reports')) automaticReportPosts += 1 })
  await createProject(request)
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  const workInstant = new Date(`${date}T10:00:00+08:00`)
  const recordResponse = await request.post(`${apiBase}/api/records`, { data: { projectId: null, content: '日报事实', occurredAt: workInstant.toISOString() } })
  const sourceRecord = await recordResponse.json() as { id: string }
  const monday = new Date(workInstant); monday.setUTCDate(workInstant.getUTCDate() - ((workInstant.getUTCDay() + 6) % 7))
  const nextWeek = new Date(monday); nextWeek.setUTCDate(monday.getUTCDate() + 8)
  await request.post(`${apiBase}/api/tasks`, { data: { projectId: null, title: '下周明确计划', notes: '', dueAt: nextWeek.toISOString(), priority: 'MEDIUM' } })
  await request.post(`${apiBase}/api/tasks`, { data: { projectId: null, title: '无期限事项', notes: '', dueAt: null, priority: 'LOW' } })
  await openWorkbench(page)
  await page.waitForTimeout(800)
  expect(automaticReportPosts).toBe(0)

  await expect(page.getByTestId('daily-report')).toHaveCount(0)
  await expect(page.getByTestId('weekly-report')).toHaveCount(0)
  await openSection(page, 'daily')
  await openSection(page, 'weekly')
  await expect(page.getByLabel('日报日期')).toHaveValue(date)
  await expect(page.getByLabel('周报所在日期')).toHaveValue(date)
  const otherDate = date === '2026-09-20' ? '2026-09-19' : '2026-09-20'
  await navigate(page, '工作记录')
  await page.getByLabel('查看日期').fill(otherDate)
  await expect(page.getByLabel('日报日期')).toHaveValue(date)
  await expect(page.getByLabel('周报所在日期')).toHaveValue(date)
  await page.getByLabel('查看日期').fill(date)
  await openSection(page, 'daily')
  await page.getByTestId('daily-report').getByRole('button', { name: '生成新日报' }).click()
  await expect(page.getByTestId('daily-content')).toContainText('日报事实', { timeout: 15_000 })
  await expect(page.getByTestId('daily-content')).toContainText('## 明确成果')
  await expect(page.getByTestId('daily-content')).toContainText('## 工作进展')
  await expect(page.getByTestId('daily-content')).toContainText('## 计划')
  await expect(page.getByTestId('daily-report')).toContainText('来源 1')
  await page.getByTestId('daily-content').fill('人工修订日报')
  await page.getByTestId('daily-report').getByRole('button', { name: '保存正文' }).click()
  await page.getByTestId('daily-report').getByRole('button', { name: '复制正文' }).click()
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('人工修订日报')

  await openSection(page, 'weekly')
  await page.setViewportSize({ width: 1440, height: 800 })
  await page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' }).click()
  await expect(page.getByTestId('weekly-content')).toContainText('本周完成', { timeout: 15_000 })
  await expect(page.getByTestId('weekly-content')).toContainText('下周明确计划')
  const aiCard = page.locator('.weekly-ai-card')
  const manualCard = page.locator('.weekly-manual-card')
  const aiBounds = await aiCard.boundingBox()
  const manualBounds = await manualCard.boundingBox()
  expect((manualBounds?.x ?? 0)).toBeGreaterThan((aiBounds?.x ?? 0) + (aiBounds?.width ?? 0) - 2)
  expect(Math.abs((manualBounds?.y ?? 0) - (aiBounds?.y ?? 0))).toBeLessThanOrEqual(2)
  const saveAiButton = manualCard.getByRole('button', { name: '保存 AI 正文' })
  const copyButton = manualCard.getByRole('button', { name: '复制组合周报' })
  await expect(saveAiButton).toHaveAttribute('form', 'weekly-ai-form')
  expect((await saveAiButton.boundingBox())?.y ?? 800).toBeLessThan((manualBounds?.y ?? 0) + 80)
  expect((await copyButton.boundingBox())?.y ?? 800).toBeLessThan((manualBounds?.y ?? 0) + 80)
  const weeklyBody = await page.getByTestId('weekly-content').inputValue()
  const plansSection = weeklyBody.split('## 下周计划')[1] ?? ''
  expect(plansSection).not.toContain('无期限事项')

  await page.getByTestId('weekly-content').fill('旧周报人工编辑稿')
  const firstWeeklyId = await page.getByLabel('周报历史版本').inputValue()
  await saveAiButton.click()
  await expect(page.locator('.viewport-toast')).toContainText('AI 正文已保存')
  const savedAi = await (await request.get(`${apiBase}/api/reports/${firstWeeklyId}`)).json() as { content: string; manualAdditions: string }
  expect(savedAi.content).toBe('旧周报人工编辑稿')
  expect(savedAi.manualAdditions).toBe('')
  const updateSource = await request.put(`${apiBase}/api/records/${sourceRecord.id}`, { data: {
    projectId: null, content: '日报事实（来源已修改）', occurredAt: workInstant.toISOString(),
  } })
  expect(updateSource.ok()).toBeTruthy()
  await page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' }).click()
  await expect(page.getByTestId('weekly-content')).toContainText('来源已修改', { timeout: 15_000 })
  const weeklyVersions = await (await request.get(`${apiBase}/api/reports?reportType=WEEKLY&date=${date}`)).json() as Array<{
    id: string; previousReportId: string | null; content: string; sources: Array<{ content: string }>
  }>
  expect(weeklyVersions).toHaveLength(2)
  expect(weeklyVersions[0].previousReportId).toBe(weeklyVersions[1].id)
  expect(weeklyVersions[1].content).toBe('旧周报人工编辑稿')
  expect(weeklyVersions[1].sources.some(source => source.content === '日报事实')).toBeTruthy()
  await page.setViewportSize({ width: 390, height: 740 })
  const mobileAiBounds = await aiCard.boundingBox()
  const mobileManualBounds = await manualCard.boundingBox()
  expect((mobileManualBounds?.y ?? 0)).toBeGreaterThanOrEqual((mobileAiBounds?.y ?? 0) + (mobileAiBounds?.height ?? 0) - 2)
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await saveAiButton.scrollIntoViewIfNeeded()
  await expect(saveAiButton).toBeInViewport()
  await copyButton.scrollIntoViewIfNeeded()
  await expect(copyButton).toBeInViewport()
  await page.getByTestId('weekly-manual').fill('用户补充：风险待确认')
  await manualCard.getByRole('button', { name: '独立保存用户补充' }).click()
  await expect(page.locator('.viewport-toast')).toContainText('用户补充已独立保存')
  const currentWeeklyId = await page.getByLabel('周报历史版本').inputValue()
  const savedManual = await (await request.get(`${apiBase}/api/reports/${currentWeeklyId}`)).json() as { content: string; manualAdditions: string }
  expect(savedManual.manualAdditions).toBe('用户补充：风险待确认')
  expect(savedManual.content).toContain('来源已修改')
  await copyButton.click()
  const copied = await page.evaluate(() => navigator.clipboard.readText())
  expect(copied).toContain('来源已修改')
  expect(copied).toContain('用户补充（无 AI 来源标记）')
  expect(copied).toContain('用户补充：风险待确认')
  await page.getByTestId('weekly-report').getByRole('button', { name: /核对本版本来源/ }).scrollIntoViewIfNeeded()
  await expect(page.getByTestId('weekly-report').getByRole('button', { name: /核对本版本来源/ })).toBeInViewport()
  await page.setViewportSize({ width: 1440, height: 800 })

  await page.getByTestId('weekly-content').fill('尚未保存的周报正文')
  await navigate(page, '工作记录')
  await openSection(page, 'daily')
  await page.getByTestId('daily-content').fill('日报切换后保留的草稿')
  await openSection(page, 'weekly')
  await expect(page.getByTestId('weekly-content')).toHaveValue('尚未保存的周报正文')
  await openSection(page, 'daily')
  await expect(page.getByTestId('daily-content')).toHaveValue('日报切换后保留的草稿')
  await openSection(page, 'weekly')
  await page.getByLabel('周报所在日期').fill(date === '2026-09-20' ? '2026-09-19' : '2026-09-20')
  await page.getByRole('dialog').getByRole('button', { name: '取消' }).click()
  await expect(page.getByTestId('weekly-content')).toHaveValue('尚未保存的周报正文')
  await page.screenshot({ path: 'test-results/d9-desktop-workflow.png', fullPage: true })
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
})

test('操作提示五秒消失、重复提示重新计时且可手动关闭', async ({ page }) => {
  await openWorkbench(page)
  await navigate(page, '项目管理')
  await page.clock.install()
  const name = page.getByLabel('新项目名称')
  const create = page.getByRole('button', { name: '创建项目', exact: true })
  await name.fill(`提示验证一-${crypto.randomUUID()}`)
  await create.click()
  const toast = page.locator('.viewport-toast')
  await expect(toast).toContainText('项目已创建')
  await page.clock.fastForward(3000)
  await name.fill(`提示验证二-${crypto.randomUUID()}`)
  await create.click()
  await expect(toast).toContainText('项目已创建')
  await page.clock.fastForward(2100)
  await expect(toast).toBeVisible()
  await page.clock.fastForward(3000)
  await expect(toast).toHaveCount(0)
  await name.fill(`提示验证三-${crypto.randomUUID()}`)
  await create.click()
  await expect(toast).toBeVisible()
  await toast.getByRole('button', { name: '关闭提示' }).click()
  await expect(toast).toHaveCount(0)
  await name.fill(`提示验证四-${crypto.randomUUID()}`)
  await create.click()
  await expect(toast).toBeVisible()
  await navigate(page, '待办任务')
  await expect(toast).toHaveCount(0)

  let releaseCreate!: () => void
  const heldCreate = new Promise<void>(resolve => { releaseCreate = resolve })
  await page.route('**/api/projects', async route => {
    if (route.request().method() === 'POST') await heldCreate
    await route.continue()
  })
  await navigate(page, '项目管理')
  await name.fill(`隐藏页面提示验证-${crypto.randomUUID()}`)
  await create.click()
  const created = page.waitForResponse(response => response.url().endsWith('/api/projects') && response.request().method() === 'POST')
  await navigate(page, '待办任务')
  releaseCreate()
  await created
  await expect(page.locator('.project-form button[type="submit"]')).toHaveText('创建项目')
  await expect(toast).toHaveCount(0)
})

test('日报版本信息右侧集中操作，长正文滚动且删除保存复制正确', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  expect((await request.post(`${apiBase}/api/records`, { data: {
    projectId: null, content: '日报编辑布局验证', occurredAt: `${date}T09:00:00+08:00`,
  } })).ok()).toBeTruthy()
  await page.setViewportSize({ width: 1440, height: 900 })
  await openWorkbench(page)
  await openSection(page, 'daily')
  const panel = page.getByTestId('daily-report')
  await panel.getByRole('button', { name: '生成新日报' }).click()
  const input = panel.getByTestId('daily-content')
  await expect(input).toBeVisible({ timeout: 15_000 })
  const actions = panel.locator('.daily-report-actions')
  const deleteButton = actions.getByRole('button', { name: '删除此版本' })
  const saveButton = actions.getByRole('button', { name: '保存正文' })
  const copyButton = actions.getByRole('button', { name: '复制正文' })
  const [metaBox, actionsBox, deleteBox, saveBox, copyBox, inputBox] = await Promise.all([
    panel.locator('.daily-report-meta-row .report-meta').boundingBox(), actions.boundingBox(),
    deleteButton.boundingBox(), saveButton.boundingBox(), copyButton.boundingBox(), input.boundingBox(),
  ])
  expect(metaBox?.x ?? 0).toBeLessThan(deleteBox?.x ?? 0)
  expect((metaBox?.x ?? 0) + (metaBox?.width ?? 0)).toBeLessThanOrEqual((deleteBox?.x ?? 0) + 2)
  expect(Math.abs((metaBox?.y ?? 0) + (metaBox?.height ?? 0) / 2 - (deleteBox?.y ?? 0) - (deleteBox?.height ?? 0) / 2)).toBeLessThanOrEqual(2)
  expect((deleteBox?.x ?? 0) + (deleteBox?.width ?? 0)).toBeLessThan(saveBox?.x ?? 0)
  expect((saveBox?.x ?? 0) + (saveBox?.width ?? 0)).toBeLessThan(copyBox?.x ?? 0)
  expect((actionsBox?.y ?? 0) + (actionsBox?.height ?? 0)).toBeLessThan(inputBox?.y ?? 0)
  await expect(saveButton).toHaveAttribute('form', 'daily-report-form')
  await expect(deleteButton).toBeInViewport()
  await expect(saveButton).toBeInViewport()
  await expect(copyButton).toBeInViewport()
  await deleteButton.click()
  const deleteDialog = page.getByRole('dialog', { name: '删除此日报版本？' })
  await expect(deleteDialog).toBeVisible()
  await deleteDialog.getByRole('button', { name: '取消' }).click()
  await expect(input).toBeVisible()

  const longText = Array.from({ length: 80 }, (_, index) => `日报正文段落 ${index + 1}`).join('\n') + '\n日报正文末段'
  await input.fill(longText)
  await expect.poll(() => input.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
  await input.evaluate(element => { element.scrollTop = element.scrollHeight })
  expect(await input.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
  await panel.getByRole('region', { name: '日报正文' }).evaluate(element => { element.scrollTop = 0 })
  await expect(saveButton).toBeInViewport()
  await saveButton.click()
  const id = await panel.getByLabel('日报历史版本').inputValue()
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/reports/${id}`)).json() as { content: string }).content).toBe(longText)
  await copyButton.click()
  await expect.poll(() => page.evaluate(async () => (await navigator.clipboard.readText()).replace(/\r\n/g, '\n'))).toBe(longText)

  await page.setViewportSize({ width: 320, height: 520 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  for (const button of [deleteButton, saveButton, copyButton]) {
    await button.scrollIntoViewIfNeeded()
    await expect(button).toBeInViewport()
  }
  await expect(input).toHaveValue(longText)
})

test('日报周报顶部顺序和周报紧凑双卡长文本滚动', async ({ page, request }, testInfo) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  await request.post(`${apiBase}/api/records`, { data: {
    projectId: null, content: '周报双卡布局验证', occurredAt: `${date}T09:00:00+08:00`,
  } })
  await page.setViewportSize({ width: 1440, height: 900 })
  await openWorkbench(page)
  for (const kind of ['daily', 'weekly'] as const) {
    await openSection(page, kind)
    const panel = page.getByTestId(`${kind}-report`)
    const toolbar = panel.locator('.report-toolbar')
    const history = toolbar.getByLabel(kind === 'daily' ? '日报历史版本' : '周报历史版本')
    const generate = toolbar.getByRole('button', { name: kind === 'daily' ? '生成新日报' : '生成新周报' })
    const dateInput = toolbar.getByLabel(kind === 'daily' ? '日报日期' : '周报所在日期')
    expect(await toolbar.locator(':scope > *').evaluateAll(nodes => nodes.map(node => node.tagName))).toEqual(['SELECT', 'BUTTON', 'INPUT'])
    const [historyBox, generateBox, dateBox, panelBox] = await Promise.all([
      history.boundingBox(), generate.boundingBox(), dateInput.boundingBox(), panel.boundingBox(),
    ])
    expect((historyBox?.x ?? 0) + (historyBox?.width ?? 0)).toBeLessThanOrEqual((generateBox?.x ?? 0) + 2)
    expect((generateBox?.x ?? 0) + (generateBox?.width ?? 0)).toBeLessThanOrEqual((dateBox?.x ?? 0) + 2)
    expect(historyBox?.x ?? 0).toBeGreaterThan((panelBox?.x ?? 0) + (panelBox?.width ?? 0) * 0.35)
  }
  await openSection(page, 'daily')
  await page.getByTestId('daily-report').getByRole('button', { name: '生成新日报' }).click()
  const dailyInput = page.getByTestId('daily-content')
  await expect(dailyInput).toBeVisible({ timeout: 15_000 })
  await dailyInput.fill('日报正文布局验证')
  await page.getByTestId('daily-report').getByRole('button', { name: '保存正文' }).click()
  const dailyNote = page.getByTestId('daily-report').getByText(/正文已由用户编辑；“来源 N”仅代表/)
  await expect(dailyNote).toBeVisible()
  await page.getByTestId('daily-report').getByRole('region', { name: '日报正文' })
    .evaluate(element => { element.scrollTop = element.scrollHeight })
  const [dailyNoteBox, dailyPagerBox] = await Promise.all([
    dailyNote.boundingBox(), page.getByTestId('daily-report').locator(':scope > .pagination').boundingBox(),
  ])
  expect((dailyPagerBox?.y ?? 0) - ((dailyNoteBox?.y ?? 0) + (dailyNoteBox?.height ?? 0))).toBeLessThan(35)
  await openSection(page, 'weekly')
  await page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' }).click()
  await expect(page.getByTestId('weekly-content')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' })).toBeEnabled()
  const aiText = Array.from({ length: 90 }, (_, index) => `AI 周报段落 ${index + 1}`).join('\n') + '\nAI 正文末段'
  const manualText = Array.from({ length: 80 }, (_, index) => `用户补充段落 ${index + 1}`).join('\n') + '\n补充末段'
  const aiInput = page.getByTestId('weekly-content')
  const manualInput = page.getByTestId('weekly-manual')
  await aiInput.fill(aiText)
  await manualInput.fill(manualText)
  for (const input of [aiInput, manualInput]) {
    await expect.poll(() => input.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
    await input.evaluate(element => { element.scrollTop = element.scrollHeight })
    expect(await input.evaluate(element => element.scrollTop)).toBeGreaterThan(0)
  }
  await expect(aiInput).toHaveValue(aiText)
  await expect(manualInput).toHaveValue(manualText)
  for (const card of [page.locator('.weekly-ai-card'), page.locator('.weekly-manual-card')]) {
    expect((await card.boundingBox())?.height ?? 900).toBeLessThan(470)
  }
  const manualSave = page.locator('.weekly-manual-card').getByRole('button', { name: '独立保存用户补充' })
  await page.getByTestId('weekly-report').getByRole('region', { name: '周报内容' }).evaluate(element => { element.scrollTop = 0 })
  await page.getByTestId('weekly-report').getByRole('region', { name: '周报正文' }).evaluate(element => { element.scrollTop = 0 })
  const documentBounds = await page.getByTestId('weekly-report').getByRole('region', { name: '周报正文' }).boundingBox()
  const manualSaveBounds = await manualSave.boundingBox()
  expect(manualSaveBounds?.y ?? -1).toBeGreaterThanOrEqual((documentBounds?.y ?? 0) - 1)
  expect((manualSaveBounds?.y ?? 900) + (manualSaveBounds?.height ?? 0)).toBeLessThanOrEqual(
    (documentBounds?.y ?? 0) + (documentBounds?.height ?? 0) + 1)
  await expect(manualSave).toBeInViewport()
  await page.locator('.weekly-manual-card').getByRole('button', { name: '保存 AI 正文' }).click()
  const note = page.getByTestId('weekly-report').getByText('AI 正文已由用户编辑；来源标记不会因人工修改自动重算。')
  await expect(note).toBeVisible()
  const [noteBox, pagerBox] = await Promise.all([note.boundingBox(), page.getByTestId('weekly-report').locator(':scope > .pagination').boundingBox()])
  expect((pagerBox?.y ?? 0) - ((noteBox?.y ?? 0) + (noteBox?.height ?? 0))).toBeLessThan(35)
  await page.screenshot({ path: testInfo.outputPath('weekly-compact-desktop.png') })

  await page.setViewportSize({ width: 320, height: 520 })
  const aiCardBox = await page.locator('.weekly-ai-card').boundingBox()
  const manualCardBox = await page.locator('.weekly-manual-card').boundingBox()
  expect(manualCardBox?.y ?? 0).toBeGreaterThanOrEqual((aiCardBox?.y ?? 0) + (aiCardBox?.height ?? 0) - 2)
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  for (const label of ['周报历史版本', '周报所在日期']) {
    const control = page.getByLabel(label)
    await control.scrollIntoViewIfNeeded()
    await expect(control).toBeInViewport()
  }
  await page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' }).scrollIntoViewIfNeeded()
  await expect(page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' })).toBeInViewport()
  await page.locator('.weekly-manual-card').getByRole('button', { name: '保存 AI 正文' }).scrollIntoViewIfNeeded()
  await expect(page.locator('.weekly-manual-card').getByRole('button', { name: '保存 AI 正文' })).toBeInViewport()
  const sourceToggle = page.getByTestId('weekly-report').getByRole('button', { name: /核对本版本来源/ })
  await sourceToggle.scrollIntoViewIfNeeded()
  await expect(sourceToggle).toBeInViewport()
  await page.screenshot({ path: testInfo.outputPath('weekly-compact-mobile.png') })
})

test('100+ 项目分页搜索、新建倒序与筛选保留', async ({ page, request }) => {
  await Promise.all([
    ...Array.from({ length: 105 }, (_, index) => createProject(request, `分页项目-${String(index).padStart(3, '0')}`)),
    ...Array.from({ length: 101 }, (_, index) => request.post(`${apiBase}/api/tasks`, { data: {
      projectId: null, title: `分页待办-${String(index).padStart(3, '0')}`, notes: '', dueAt: null, priority: 'MEDIUM',
    } })),
  ])
  expect((await request.get(`${apiBase}/api/projects/page?page=0&size=11`)).status()).toBe(400)
  const outOfRange = await (await request.get(`${apiBase}/api/projects/page?page=99&size=20`)).json() as { items: unknown[]; page: number }
  expect(outOfRange).toMatchObject({ items: [], page: 99 })
  expect((await request.get(`${apiBase}/api/records/page?size=20`)).ok()).toBeTruthy()
  expect((await request.get(`${apiBase}/api/reports/page?reportType=DAILY&size=20`)).ok()).toBeTruthy()
  await page.route('**/api/projects/page**', async route => {
    const q = new URL(route.request().url()).searchParams.get('q')
    if (q === '分页项目-0') await new Promise(resolve => setTimeout(resolve, 600))
    await route.continue()
  })
  await openWorkbench(page)
  await navigate(page, '项目管理')
  await expect(page.getByRole('navigation', { name: '分页', exact: true }).last()).toContainText('第 1 / 21 页')
  await expect(page.locator('.project-list .project')).toHaveCount(5)
  await expect(page.getByLabel('每页')).toHaveCount(0)
  await page.getByLabel('搜索项目列表').fill('分页项目-0')
  await page.getByLabel('搜索项目列表').fill('分页项目-104')
  await page.waitForTimeout(700)
  await expect(page.locator('.project-list .project')).toHaveCount(1)
  await page.getByLabel('搜索项目列表').fill('分页项目-104')
  await expect(page.locator('.project-list .project').filter({ hasText: '分页项目-104' })).toBeVisible()
  await page.getByLabel('新项目名称').fill('最新项目')
  await page.getByRole('button', { name: '创建项目', exact: true }).click()
  await page.getByLabel('搜索项目列表').fill('')
  await expect(page.locator('.project-list .project').first()).toContainText('最新项目')
  await page.getByRole('navigation', { name: '分页', exact: true }).last().getByRole('button', { name: '下一页' }).click()
  await expect(page.getByRole('navigation', { name: '分页', exact: true }).last()).toContainText('第 2 / 22 页')
  await navigate(page, '待办任务')
  await expect(page.getByTestId('task-panel').getByRole('navigation', { name: '分页', exact: true })).toContainText('第 1 / 21 页')
  await expect(page.getByTestId('task-item')).toHaveCount(5)
  await page.getByRole('button', { name: '新建待办', exact: true }).first().click()
  const picker = page.getByRole('dialog').getByTestId('project-picker')
  await expect(picker.locator('option')).toHaveCount(107)
  const allProjects = await (await request.get(`${apiBase}/api/projects`)).json() as Array<{ id: string; name: string }>
  const beyondFirstFifty = allProjects[80]
  await picker.selectOption(beyondFirstFifty.id)
  await expect(picker).toHaveValue(beyondFirstFifty.id)
  await page.getByTestId('task-title').fill('关联较早创建的项目')
  await page.getByRole('dialog').getByRole('button', { name: '创建待办', exact: true }).click()
  await expect(page.getByTestId('task-item').first()).toContainText(beyondFirstFifty.name)
  await navigate(page, '项目管理')
  await page.getByLabel('搜索项目列表').fill(beyondFirstFifty.name)
  await page.locator('.project').filter({ hasText: beyondFirstFifty.name }).getByRole('button', { name: '归档' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认归档' }).click()
  await expect(page.locator('.project')).toHaveCount(0)
  await page.getByLabel('包含已归档').check()
  await expect(page.locator('.project')).toContainText(beyondFirstFifty.name)
  await expect(page.locator('.project')).toContainText('已归档')
})

test('超过8秒的输入通过WebSocket自动完成，断线后GET恢复并提供编辑入口', async ({ page, context }) => {
  await openWorkbench(page)
  await page.getByTestId('capture-content').fill('[E2E_DELAY_9S] 延迟生成记录和待办')
  await page.getByTestId('capture-submit').click()
  await expect(page.getByText('原文已保存，AI 正在处理')).toBeVisible()
  const storageKey = await pendingStorageKey(page)
  await expect.poll(() => page.evaluate(key => localStorage.getItem(key) ?? '', storageKey)).toContain('INPUT:')
  await page.reload()
  await expect(page.getByText('原文已保存，AI 正在处理')).toBeVisible()
  await context.setOffline(true)
  await page.waitForTimeout(10_000)
  await context.setOffline(false)
  await expect(page.getByText('已生成 1 条工作记录和 1 项待办。')).toBeVisible({ timeout: 20_000 })
  await page.getByRole('button', { name: /记录：.*加载编辑/ }).click()
  await expect(page.getByTestId('record-submit')).toHaveText('保存修改')
  await page.reload()
  await expect(page.getByTestId('record-item')).toHaveCount(1)
})

test('日报周报短来源不产生双滚动，长来源内部滚动且分页固定', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  const contents = [
    '完成待办：阅读 hello-agent 第二章\n完成结果：已经完成阅读第二章',
    '阅读完了 hello-agent 第三章',
    '继续补充 AI 工作台浏览器验收',
  ]
  for (const content of contents) {
    const response = await request.post(`${apiBase}/api/records`, {
      data: { projectId: null, content, occurredAt: `${date}T09:00:00+08:00` },
    })
    expect(response.ok()).toBeTruthy()
  }
  await openWorkbench(page)
  for (const kind of ['daily', 'weekly'] as const) {
    await openSection(page, kind)
    const panel = page.getByTestId(`${kind}-report`)
    await panel.getByRole('button', { name: kind === 'daily' ? '生成新日报' : '生成新周报' }).click()
    await expect(panel.locator('.source-rows article')).toHaveCount(3, { timeout: 15_000 })
    for (const [width, height] of [[1440, 900], [1920, 1080], [1024, 900]]) {
      await page.setViewportSize({ width, height })
      const geometry = await panel.evaluate(element => {
        const outer = element.querySelector<HTMLElement>('.report-scroll')!
        const rows = element.querySelector<HTMLElement>('.source-rows')!
        const card = element.querySelector<HTMLElement>('.report-evidence')!
        const pager = element.querySelector<HTMLElement>('.report-sources > .pagination')!
        const historyPager = element.querySelector<HTMLElement>(':scope > .pagination')!
        const textarea = element.querySelector<HTMLElement>('.report-document textarea')!
        const manualTextarea = element.querySelector<HTMLElement>('.weekly-manual-card textarea')
        return {
          outerOverflow: outer.scrollHeight - outer.clientHeight,
          rowsOverflow: rows.scrollHeight - rows.clientHeight,
          pagerBottom: pager.getBoundingClientRect().bottom,
          cardBottom: card.getBoundingClientRect().bottom,
          evidenceHeight: card.getBoundingClientRect().height,
          textareaHeight: textarea.getBoundingClientRect().height,
          manualTextareaHeight: manualTextarea?.getBoundingClientRect().height,
          historyBottomGap: element.getBoundingClientRect().bottom - historyPager.getBoundingClientRect().bottom,
        }
      })
      expect(geometry.outerOverflow).toBeLessThanOrEqual(1)
      expect(geometry.rowsOverflow).toBeLessThanOrEqual(1)
      expect(geometry.pagerBottom).toBeLessThanOrEqual(geometry.cardBottom - 8)
      if (width >= 1440) {
        expect(geometry.historyBottomGap).toBeLessThanOrEqual(35)
        expect(geometry.evidenceHeight).toBeGreaterThan(height === 900 ? 450 : 620)
        expect(geometry.textareaHeight).toBeGreaterThan(height === 900 ? 280 : 450)
        if (kind === 'weekly') expect(geometry.manualTextareaHeight).toBeGreaterThan(height === 900 ? 280 : 450)
      }
      await expect(panel.locator('.report-sources > .pagination')).toBeInViewport()
    }
  }
  await page.setViewportSize({ width: 320, height: 520 })
  const weekly = page.getByTestId('weekly-report')
  const outer = weekly.getByRole('region', { name: '周报内容' })
  await expect.poll(() => outer.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
  const pager = weekly.locator('.report-sources > .pagination')
  await pager.scrollIntoViewIfNeeded()
  await expect(pager).toBeInViewport()
  await weekly.locator('.source-rows article').last().scrollIntoViewIfNeeded()
  await expect(weekly.locator('.source-rows article').last()).toBeInViewport({ ratio: 0.1 })
  await pager.scrollIntoViewIfNeeded()
  await expect(pager).toBeInViewport()

  await page.setViewportSize({ width: 1440, height: 520 })
  const manualSave = weekly.getByRole('button', { name: '独立保存用户补充' })
  await manualSave.scrollIntoViewIfNeeded()
  await expect(manualSave).toBeInViewport()
  await pager.scrollIntoViewIfNeeded()
  await expect(pager).toBeInViewport()
  await expect(weekly.locator(':scope > .pagination')).toBeInViewport()

  const longSource = await request.post(`${apiBase}/api/records`, {
    data: { projectId: null, content: '长来源内容'.repeat(300), occurredAt: `${date}T10:00:00+08:00` },
  })
  expect(longSource.ok()).toBeTruthy()
  await page.setViewportSize({ width: 1440, height: 900 })
  for (const kind of ['daily', 'weekly'] as const) {
    await openSection(page, kind)
    const panel = page.getByTestId(`${kind}-report`)
    await panel.getByRole('button', { name: kind === 'daily' ? '生成新日报' : '生成新周报' }).click()
    const rows = panel.locator('.source-rows')
    await expect(panel.locator('.source-rows article')).toHaveCount(4, { timeout: 15_000 })
    await expect.poll(() => rows.evaluate(element => element.scrollHeight > element.clientHeight + 1)).toBe(true)
    const sourcePager = panel.locator('.report-sources > .pagination')
    const before = await sourcePager.boundingBox()
    await rows.evaluate(element => { element.scrollTop = element.scrollHeight })
    await expect(panel.locator('.source-rows article').last()).toBeInViewport({ ratio: 0.1 })
    const after = await sourcePager.boundingBox()
    expect(Math.abs((after?.y ?? 0) - (before?.y ?? 0))).toBeLessThan(2)
    await expect(sourcePager).toBeInViewport()
  }
})

test('账号菜单、管理员创建与换账号隔离', async ({ page, request }) => {
  const username = `e2e_user_${crypto.randomUUID().slice(0, 8)}`
  const password = 'E2eOnly-User-9384!'
  const record = await request.post(`${apiBase}/api/records`, { data: {
    projectId: null, content: '仅管理员可见的工作记录', occurredAt: new Date().toISOString(),
  } })
  expect(record.ok()).toBeTruthy()
  await openWorkbench(page)
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await page.getByTestId('record-content').fill('管理员未保存草稿')
  await page.getByRole('button', { name: /^账号菜单/ }).click()
  await page.getByRole('menuitem', { name: '用户管理' }).click()
  await expect(page.getByRole('navigation', { name: '主导航' }).getByRole('link')).toHaveCount(5)
  await page.getByRole('button', { name: '创建用户' }).click()
  const dialog = page.getByRole('dialog', { name: '创建用户' })
  await expect(dialog.getByLabel('角色')).toHaveValue('USER')
  await dialog.getByLabel('用户名').fill(username)
  await dialog.getByLabel('密码', { exact: true }).fill(password)
  await dialog.getByLabel('确认密码').fill(password)
  await dialog.getByRole('button', { name: '创建用户' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByText(username).first()).toBeVisible()
  await page.setViewportSize({ width: 390, height: 700 })
  await navigate(page, '工作记录')
  const mobileMenu = page.locator('.mobile-account')
  await expect(mobileMenu.getByRole('button', { name: /^账号菜单/ })).toBeVisible()
  await mobileMenu.getByRole('button', { name: /^账号菜单/ }).click()
  await expect(mobileMenu.getByRole('menuitem', { name: '修改密码' })).toBeVisible()
  await expect(mobileMenu.getByRole('menuitem', { name: '退出登录' })).toBeVisible()
  await mobileMenu.getByRole('menuitem', { name: '修改密码' }).click()
  const mobilePassword = page.getByRole('dialog', { name: '修改密码' })
  await expect(mobilePassword.getByLabel('当前密码')).toBeVisible()
  await expect(mobilePassword.getByLabel('新密码', { exact: true })).toBeVisible()
  await expect(mobilePassword.getByLabel('确认新密码')).toBeVisible()
  await mobilePassword.getByRole('button', { name: '取消' }).click()
  await expect(mobilePassword).toHaveCount(0)
  await mobileMenu.getByRole('button', { name: /^账号菜单/ }).click()
  await mobileMenu.getByRole('menuitem', { name: '用户管理' }).click()
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
  await mobileMenu.getByRole('button', { name: /^账号菜单/ }).click()
  await mobileMenu.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '登录工作台' })).toBeVisible()
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await expect(page.getByTestId('record-content')).not.toHaveValue('管理员未保存草稿')
  await expect(page.getByText('仅管理员可见的工作记录')).toHaveCount(0)
  await page.getByRole('button', { name: /^账号菜单/ }).click()
  await expect(page.getByRole('menuitem', { name: '用户管理' })).toHaveCount(0)
  expect((await page.context().request.get('/api/admin/users')).status()).toBe(403)
  await page.setViewportSize({ width: 390, height: 700 })
  await expect(page.locator('.mobile-account').getByRole('button', { name: /^账号菜单/ })).toBeVisible()
  await expect(page.locator('.mobile-account').getByRole('button', { name: /^账号菜单/ })).toHaveAttribute('aria-expanded', 'true')
  await expect(page.locator('.mobile-account').getByRole('menuitem', { name: '修改密码' })).toBeVisible()
  await expect(page.locator('.mobile-account').getByRole('menuitem', { name: '退出登录' })).toBeVisible()
  await expect(page.locator('.mobile-account').getByRole('menuitem', { name: '用户管理' })).toHaveCount(0)
  await expect(page.getByRole('navigation', { name: '主导航' }).getByRole('link')).toHaveCount(5)
})

test('旧身份迟到HTTP与STOMP订阅及pending不会进入新身份', async ({ page, request }) => {
  test.setTimeout(60_000)
  const username = `e2e_switch_${crypto.randomUUID().slice(0, 8)}`
  const password = 'E2eOnly-Switch-9384!'
  const created = await request.post(`${apiBase}/api/admin/users`, { data: { username, password, role: 'USER' } })
  expect(created.status()).toBe(201)
  const projectName = `仅A可见-${crypto.randomUUID().slice(0, 8)}`
  expect((await request.post(`${apiBase}/api/projects`, { data: { name: projectName } })).status()).toBe(200)
  const sockets: import('@playwright/test').WebSocket[] = []
  let subscribedA = false
  page.on('websocket', socket => {
    if (new URL(socket.url()).pathname !== '/ws/events') return
    sockets.push(socket)
    socket.on('framesent', frame => {
      if (String(frame.payload).startsWith('SUBSCRIBE') && String(frame.payload).includes('/user/queue/workbench-events')) subscribedA = true
    })
  })
  await openWorkbench(page)
  const aPendingKey = await pendingStorageKey(page)
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await page.getByTestId('record-content').fill('A的未保存草稿')
  await navigate(page, '待办任务')
  await navigate(page, '工作汇报')
  await navigate(page, '工作记录')
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: 'AI 快记' }).click()
  await page.route('**/api/inputs/*', async route => {
    if (route.request().method() !== 'GET') { await route.continue(); return }
    const response = await route.fetch()
    if (response.status() !== 200) { await route.fulfill({ response }); return }
    const body = await response.json() as Record<string, unknown>
    await route.fulfill({ response, json: { ...body, status: 'PROCESSING', records: [], tasks: [] } })
  })
  await page.getByTestId('capture-content').fill('[E2E_DELAY_9S] A的未完成输入')
  await page.getByTestId('capture-submit').click()
  await expect.poll(() => subscribedA).toBe(true)
  await expect.poll(() => page.evaluate(key => localStorage.getItem(key) ?? '', aPendingKey)).toContain('INPUT:')
  const aInputId = await page.evaluate(key => (JSON.parse(localStorage.getItem(key) ?? '[]') as string[]).at(-1)?.slice('INPUT:'.length), aPendingKey)
  expect(aInputId).toBeTruthy()

  let releaseOld!: () => void
  let oldCaptured!: () => void
  let oldSettled!: () => void
  const oldGate = new Promise<void>(resolve => { releaseOld = resolve })
  const captured = new Promise<void>(resolve => { oldCaptured = resolve })
  const settled = new Promise<void>(resolve => { oldSettled = resolve })
  let held = false
  await page.route('**/api/projects/page?**', async route => {
    if (held) { await route.continue(); return }
    held = true
    const response = await route.fetch()
    oldCaptured()
    await oldGate
    try { await route.fulfill({ response }) } catch { /* Identity switch aborts this request. */ }
    oldSettled()
  })
  await navigate(page, '项目管理')
  await captured
  await page.getByRole('button', { name: /^账号菜单/ }).click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '登录工作台' })).toBeVisible()
  await expect.poll(() => sockets[0]?.isClosed()).toBe(true)
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  const bPendingKey = await pendingStorageKey(page)
  expect(bPendingKey).not.toBe(aPendingKey)
  await navigate(page, '工作记录')
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await expect(page.getByTestId('record-content')).not.toHaveValue('A的未保存草稿')
  await navigate(page, '待办任务')
  await navigate(page, '工作汇报')
  await navigate(page, '项目管理')
  const bBusinessWrites: string[] = []
  page.on('request', outgoing => {
    const pathname = new URL(outgoing.url()).pathname
    if (outgoing.method() !== 'GET' && /^\/api\/(records|tasks|inputs|reports|projects)(\/|$)/.test(pathname)) {
      bBusinessWrites.push(outgoing.postData() ?? '')
    }
  })
  releaseOld()
  await settled
  let bFetchedOldInput = 0
  page.on('request', outgoing => {
    if (new URL(outgoing.url()).pathname === `/api/inputs/${aInputId}`) bFetchedOldInput++
  })
  await page.waitForTimeout(10_000) // A's delayed AI completion can publish only to A's closed socket.
  expect(sockets).toHaveLength(1)
  expect(bFetchedOldInput).toBe(0)
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key) ?? '[]'), bPendingKey)).toEqual([])
  await expect(page.getByText(projectName)).toHaveCount(0)
  await expect(page.getByText('A的未完成输入')).toHaveCount(0)
  await page.reload()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await expect(page.getByText(projectName)).toHaveCount(0)
  await navigate(page, '工作记录')
  await page.getByRole('group', { name: '记录方式' }).getByRole('button', { name: '手工记录' }).click()
  await expect(page.getByTestId('record-content')).not.toHaveValue('A的未保存草稿')
  await expect(page.getByText('A的未完成输入')).toHaveCount(0)
  expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key) ?? '[]'), bPendingKey)).toEqual([])
  expect(bFetchedOldInput).toBe(0)
  expect(bBusinessWrites).toEqual([])
  await page.getByTestId('record-content').fill('B自己的工作记录')
  await page.getByTestId('record-submit').click()
  await expect(page.getByTestId('record-item')).toContainText('B自己的工作记录')
  expect(bBusinessWrites).toHaveLength(1)
  expect(bBusinessWrites[0]).toContain('B自己的工作记录')
  expect(bBusinessWrites[0]).not.toContain('A的未保存草稿')
})

test('明确键盘与表单操作续期，背景点击和被动加载不续期', async ({ page }) => {
  let activitySignals = 0
  page.on('request', outgoing => { if (new URL(outgoing.url()).pathname === '/api/auth/activity') activitySignals++ })
  await openWorkbench(page)
  await page.clock.install()
  expect(activitySignals).toBe(0)
  await page.locator('.topbar').click({ position: { x: 350, y: 10 } })
  expect(activitySignals).toBe(0)
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: '待办任务' }).focus()
  await page.keyboard.press('Enter')
  await expect.poll(() => activitySignals).toBe(1)
  await expect(page.getByRole('heading', { name: '待办任务', level: 1 })).toBeVisible()
  await page.clock.fastForward(61_000)
  const create = page.locator('.tasks-page > .page-header').getByRole('button', { name: '新建待办' })
  await create.focus()
  await page.keyboard.press('Space')
  await expect.poll(() => activitySignals).toBe(2)
  const editor = page.getByRole('dialog', { name: '新建待办' })
  await editor.getByLabel('待办标题').fill('表单键盘提交续期')
  await page.clock.fastForward(61_000)
  await editor.getByLabel('待办标题').press('Enter')
  await expect.poll(() => activitySignals).toBe(3)
  await expect(editor).toHaveCount(0)
})

test('专注被动检查点不续期，前台明确暂停才发送活动信号', async ({ page }) => {
  let activitySignals = 0
  let checkpointRequests = 0
  let checkpointResponses = 0
  page.on('request', outgoing => {
    const path = new URL(outgoing.url()).pathname
    if (path === '/api/auth/activity') activitySignals++
    if (/^\/api\/focus\/sessions\/[^/]+\/checkpoint$/.test(path)) checkpointRequests++
  })
  page.on('response', response => {
    if (/^\/api\/focus\/sessions\/[^/]+\/checkpoint$/.test(new URL(response.url()).pathname) && response.ok()) checkpointResponses++
  })
  await page.clock.install()
  await openWorkbench(page)
  await navigate(page, '专注')
  await page.getByLabel('目标', { exact: true }).fill('被动检查点活动边界')
  await page.getByRole('button', { name: '开始专注' }).click()
  await expect.poll(() => checkpointResponses).toBeGreaterThanOrEqual(1)
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeEnabled()
  // Start and navigation were explicit interactions. Observe only the quiet period.
  activitySignals = 0
  checkpointRequests = 0
  await page.clock.fastForward(21_000)
  await expect.poll(() => checkpointRequests).toBeGreaterThanOrEqual(1)
  expect(activitySignals).toBe(0)
  await page.clock.fastForward(41_000)
  await page.getByRole('button', { name: '暂停', exact: true }).click()
  await expect.poll(() => activitySignals).toBe(1)
})

test('用户管理取消无变更，重置表单校验与焦点恢复', async ({ page, request }) => {
  const username = `e2e_managed_${crypto.randomUUID().slice(0, 8)}`
  const created = await request.post(`${apiBase}/api/admin/users`, { data: {
    username, password: 'E2eOnly-Managed-9384!', role: 'USER',
  } })
  expect(created.status()).toBe(201)
  const user = await created.json() as { id: string }
  await openWorkbench(page)
  await page.getByRole('button', { name: /^账号菜单/ }).click()
  await page.getByRole('menuitem', { name: '用户管理' }).click()
  const row = page.locator('.user-row').filter({ hasText: username })
  await expect(row).toContainText('普通用户')
  await row.getByRole('button', { name: '修改角色' }).click()
  const roleDialog = page.getByRole('dialog', { name: '修改用户角色？' })
  await expect(roleDialog).toContainText('现有会话会失效，业务数据保留')
  await roleDialog.getByRole('button', { name: '取消' }).click()
  await expect(row).toContainText('普通用户')
  await row.getByRole('button', { name: '停用' }).click()
  await page.getByRole('dialog', { name: '停用用户？' }).getByRole('button', { name: '取消' }).click()
  const unchanged = await (await request.get(`${apiBase}/api/admin/users`)).json() as Array<{ id: string; enabled: boolean; role: string }>
  expect(unchanged.find(item => item.id === user.id)).toMatchObject({ enabled: true, role: 'USER' })
  const reset = row.getByRole('button', { name: '重置密码' })
  await reset.click()
  const passwordDialog = page.getByRole('dialog', { name: new RegExp(`重置 ${username} 的密码`) })
  await passwordDialog.getByLabel('密码', { exact: true }).fill('E2eOnly-Changed-9384!')
  await passwordDialog.getByLabel('确认密码').fill('different')
  await passwordDialog.getByRole('button', { name: '重置密码' }).click()
  await expect(passwordDialog.getByRole('alert')).toContainText('不一致')
  await passwordDialog.getByRole('button', { name: '取消' }).click()
  await expect(reset).toBeFocused()
})

for (const action of ['自降级', '自停用', '自重置密码'] as const) {
  test(`管理员${action}成功后立即撤出工作台，刷新失败也不保留旧管理页`, async ({ page, request }) => {
    const { account, row, username } = await openSyntheticAdminManagement(page, request)
    let listRefreshes = 0
    await page.route('**/api/admin/users', async route => {
      if (route.request().method() === 'GET') { listRefreshes++; await route.abort('failed'); return }
      await route.continue()
    })
    const mutationPath = `/api/admin/users/${account.id}/${action === '自降级' ? 'role' : action === '自停用' ? 'enabled' : 'reset-password'}`
    const succeeded = page.waitForResponse(response => new URL(response.url()).pathname === mutationPath && response.status() === 200)
    if (action === '自降级') {
      await row.getByRole('button', { name: '修改角色' }).click()
      await page.getByRole('dialog', { name: '修改用户角色？' }).getByRole('button', { name: '确认修改' }).click()
    } else if (action === '自停用') {
      await row.getByRole('button', { name: '停用' }).click()
      await page.getByRole('dialog', { name: '停用用户？' }).getByRole('button', { name: '确认停用' }).click()
    } else {
      await row.getByRole('button', { name: '重置密码' }).click()
      const dialog = page.getByRole('dialog', { name: `重置 ${username} 的密码` })
      await dialog.getByLabel('密码', { exact: true }).fill('E2eOnly-SelfReset-9384!')
      await dialog.getByLabel('确认密码').fill('E2eOnly-SelfReset-9384!')
      await dialog.getByRole('button', { name: '重置密码' }).click()
    }
    await succeeded
    await expect(page.getByRole('heading', { name: '登录工作台' })).toBeVisible()
    await expect(page.getByTestId('workbench')).toHaveCount(0)
    await expect(page.getByRole('heading', { name: '用户管理' })).toHaveCount(0)
    await expect(page.locator('.viewport-toast')).toContainText('账号权限或密码已变更，请重新登录')
    await page.waitForTimeout(100)
    expect(listRefreshes).toBe(0)
  })
}

test('STOMP断线时HTTP可用，15秒兜底取回结果且自动通信不续期', async ({ page, request }) => {
  test.setTimeout(75_000)
  let socketAttempts = 0
  let firstSocket: import('@playwright/test').WebSocketRoute | null = null
  let firstConnected = false
  let disconnectedSocket: import('@playwright/test').WebSocketRoute | null = null
  let recovered = false
  let activitySignals = 0
  let inputGets = 0
  let holdResult = true
  page.on('request', outgoing => {
    if (new URL(outgoing.url()).pathname === '/api/auth/activity') activitySignals++
  })
  await page.routeWebSocket('**/ws/events', ws => {
    socketAttempts++
    if (socketAttempts === 1) {
      firstSocket = ws
      const server = ws.connectToServer()
      server.onMessage(message => { if (String(message).startsWith('CONNECTED')) firstConnected = true; ws.send(message) })
    }
    else if (socketAttempts === 2) disconnectedSocket = ws // Open without CONNECTED: HTTP fallback remains authoritative.
    else {
      const server = ws.connectToServer()
      server.onMessage(message => { if (String(message).startsWith('CONNECTED')) recovered = true; ws.send(message) })
    }
  })
  await page.route('**/api/inputs/*', async route => {
    if (!holdResult) { await route.continue(); return }
    inputGets++
    const response = await route.fetch()
    const body = await response.json() as Record<string, unknown>
    await route.fulfill({ response, json: { ...body, status: 'PROCESSING', records: [], tasks: [] } })
  })
  await openWorkbench(page)
  await page.getByTestId('capture-content').fill('[E2E_MULTI] 断线后由HTTP兜底取回结果')
  await page.getByTestId('capture-submit').click()
  await expect.poll(() => socketAttempts).toBeGreaterThanOrEqual(1)
  await expect.poll(() => firstConnected).toBe(true)
  if (!firstSocket) throw new Error('STOMP connection was not opened')
  await firstSocket.close()
  await expect.poll(() => socketAttempts).toBeGreaterThanOrEqual(2)
  const project = await request.post(`${apiBase}/api/projects`, { data: { name: '断线期间HTTP仍可用' } })
  expect(project.status()).toBe(200)
  await expect(page.getByText('原文已保存，AI 正在处理。关闭页面也不会丢失原文。')).toBeVisible()
  const afterForegroundPolls = inputGets
  const afterInteraction = activitySignals
  await expect.poll(() => inputGets, { timeout: 20_000 }).toBeGreaterThan(afterForegroundPolls)
  expect(activitySignals).toBe(afterInteraction)
  if (!disconnectedSocket) throw new Error('Disconnected STOMP retry was not opened')
  const beforeReconnect = inputGets
  await disconnectedSocket.close()
  await expect.poll(() => socketAttempts).toBeGreaterThanOrEqual(3)
  await expect.poll(() => recovered).toBe(true)
  await expect.poll(() => inputGets).toBeGreaterThan(beforeReconnect)
  expect(activitySignals).toBe(afterInteraction)
  holdResult = false
  await expect(page.getByText('已生成 2 条工作记录和 2 项待办。')).toBeVisible({ timeout: 20_000 })
})

test('真实会话撤销立即撤出业务页并保留五秒提示', async ({ page, request }) => {
  const username = `e2e_expired_${crypto.randomUUID().slice(0, 8)}`
  const password = 'E2eOnly-Expired-9384!'
  const created = await request.post(`${apiBase}/api/admin/users`, { data: { username, password, role: 'USER' } })
  expect(created.status()).toBe(201)
  const user = await created.json() as { id: string }
  await page.goto('/')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  const disabled = await request.patch(`${apiBase}/api/admin/users/${user.id}/enabled`, { data: { enabled: false } })
  expect(disabled.status()).toBe(200)
  await navigate(page, '项目管理')
  await expect(page.getByRole('heading', { name: '登录工作台' })).toBeVisible()
  await expect(page.getByTestId('workbench')).toHaveCount(0)
  await expect(page.locator('.viewport-toast')).toContainText('会话已失效')
  await expect(page.locator('.viewport-toast')).toHaveCount(0, { timeout: 6500 })
})

test('失效提示替换旧事件后重计五秒且可提前关闭', async ({ page, request }) => {
  const username = `e2e_toast_${crypto.randomUUID().slice(0, 8)}`
  const password = 'E2eOnly-Toast-9384!'
  const created = await request.post(`${apiBase}/api/admin/users`, { data: { username, password, role: 'USER' } })
  expect(created.status()).toBe(201)
  const user = await created.json() as { id: string }
  await page.goto('/')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await page.clock.install()
  await navigate(page, '项目管理')
  await page.getByLabel('新项目名称').fill(`提示计时-${crypto.randomUUID().slice(0, 8)}`)
  await page.getByRole('button', { name: '创建项目', exact: true }).click()
  const toast = page.locator('.viewport-toast')
  await expect(toast).toContainText('项目已创建')
  await page.clock.fastForward(3000)
  expect((await request.patch(`${apiBase}/api/admin/users/${user.id}/enabled`, { data: { enabled: false } })).status()).toBe(200)
  await navigate(page, '待办任务')
  await expect(page.getByRole('heading', { name: '登录工作台' })).toBeVisible()
  await expect(toast).toContainText('会话已失效')
  await page.clock.fastForward(2100)
  await expect(toast).toContainText('会话已失效') // The earlier project's 5-second deadline has passed.
  await toast.getByRole('button', { name: '关闭提示' }).click()
  await expect(toast).toHaveCount(0)
})

test('403、404与网络失败保留当前身份和原页面', async ({ page }) => {
  await openWorkbench(page)
  const pageUrl = '**/api/projects/page?**'
  await page.route(pageUrl, route => route.fulfill({ status: 403, contentType: 'application/problem+json', body: JSON.stringify({ detail: '无权读取' }) }))
  await navigate(page, '项目管理')
  await expect(page.getByRole('alert')).toContainText('无权读取')
  await expect(page.getByTestId('workbench')).toBeVisible()
  await page.unrouteAll({ behavior: 'wait' })
  await page.route(pageUrl, route => route.fulfill({ status: 404, contentType: 'application/problem+json', body: JSON.stringify({ detail: '资源不存在' }) }))
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('alert')).toContainText('资源不存在')
  await expect(page.getByTestId('workbench')).toBeVisible()
  await page.unrouteAll({ behavior: 'wait' })
  await page.route(pageUrl, route => route.abort())
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('alert')).toContainText('暂时无法读取项目')
  await expect(page.getByTestId('workbench')).toBeVisible()
})

test('143来源使用完整快照和局部别名生成，不发生静默截断', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  const occurredAt = new Date(`${date}T09:00:00+08:00`).toISOString()
  await Promise.all(Array.from({ length: 143 }, (_, index) => request.post(`${apiBase}/api/records`, {
    data: { projectId: null, content: `大来源事实-${index}`, occurredAt },
  })))
  const recordPage = await (await request.get(`${apiBase}/api/records/page?date=${date}&page=0&size=50`)).json() as { totalElements: number; totalPages: number; items: unknown[] }
  expect(recordPage).toMatchObject({ totalElements: 143, totalPages: 3 })
  expect(recordPage.items).toHaveLength(50)
  await openWorkbench(page); await openSection(page, 'weekly')
  await page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' }).click()
  await expect(page.getByTestId('weekly-report')).toContainText('143 个冻结来源', { timeout: 20_000 })
  await expect(page.getByTestId('weekly-report')).toContainText('来源 1 ·')
  const sources = page.getByTestId('weekly-report').locator('.report-sources')
  await expect(sources.locator('article')).toHaveCount(5)
  await expect(sources.getByRole('navigation', { name: '分页', exact: true })).toContainText('第 1 / 29 页')
  await expect(sources.getByLabel('每页')).toHaveCount(0)
  await page.setViewportSize({ width: 1440, height: 700 })
  await assertIndependentReportPanes(page, 'weekly')
  await sources.getByRole('button', { name: '下一页' }).click()
  await expect(sources).toContainText('来源 6 ·')
  await expect(sources.locator('article')).toHaveCount(5)
  await expect(sources.getByRole('navigation', { name: '分页', exact: true })).toContainText('第 2 / 29 页')
  await sources.getByRole('button', { name: '下一页' }).click()
  await expect(sources).toContainText('来源 11 ·')
  await expect(sources.locator('article')).toHaveCount(5)
  const reports = await (await request.get(`${apiBase}/api/reports?reportType=WEEKLY&date=${date}`)).json() as Array<{ status: string; sourceCount: number; errorCode: string | null }>
  expect(reports[0]).toMatchObject({ status: 'SUCCEEDED', sourceCount: 143, errorCode: null })
})

test('记录和待办三页可用、末页删除回退与筛选重置', async ({ page, request }) => {
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  for (let index = 0; index < 21; index += 1) {
    expect((await request.post(`${apiBase}/api/records`, { data: {
      projectId: null, content: `翻页记录-${index}`, occurredAt: `${date}T09:00:00+08:00`,
    } })).ok()).toBeTruthy()
    expect((await request.post(`${apiBase}/api/tasks`, { data: {
      projectId: null, title: `翻页待办-${index}`, notes: '', dueAt: null, priority: index === 20 ? 'HIGH' : 'MEDIUM',
    } })).ok()).toBeTruthy()
  }
  await openWorkbench(page)
  for (const testId of ['record-list', 'task-panel']) {
    await navigate(page, testId === 'record-list' ? '工作记录' : '待办任务')
    const panel = page.getByTestId(testId)
    const pagination = panel.getByRole('navigation', { name: '分页', exact: true })
    await expect(pagination).toContainText('第 1 / 5 页')
    await expect(pagination.getByLabel('每页')).toHaveCount(0)
    for (let next = 2; next <= 5; next++) {
      await pagination.getByRole('button', { name: '下一页' }).click()
      await expect(pagination).toContainText(`第 ${next} / 5 页`)
    }
    const items = panel.getByTestId(testId === 'record-list' ? 'record-item' : 'task-item')
    await expect(items).toHaveCount(1)
    await expect(pagination.getByRole('button', { name: '下一页' })).toHaveCSS('cursor', 'not-allowed')
    await items.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()
    await expect(pagination).toContainText('第 4 / 4 页')
    await expect(items).toHaveCount(5)
  }
  await page.getByLabel('优先级筛选').selectOption('HIGH')
  await expect(page.getByTestId('task-panel').getByRole('navigation', { name: '分页', exact: true })).toContainText('第 1 / 1 页')
  await expect(page.getByTestId('task-item')).toHaveCount(1)
})

test('日报周报历史翻页保护草稿，新版本刷新页数，日期切换忽略旧详情', async ({ page, request }) => {
  test.setTimeout(120_000)
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  // Empty-source reports are deterministic; no model request is made even outside the substitute profile.
  for (const reportType of ['DAILY', 'WEEKLY']) {
    for (let index = 0; index < 20; index += 1) {
      expect((await request.post(`${apiBase}/api/reports`, { data: {
        reportType, date, requestId: crypto.randomUUID(),
      } })).ok()).toBeTruthy()
    }
  }
  await openWorkbench(page)
  for (const kind of ['daily', 'weekly']) {
    await openSection(page, kind)
    const panel = page.getByTestId(`${kind}-report`)
    const pagination = panel.getByRole('navigation', { name: '分页', exact: true }).first()
    const body = panel.getByTestId(kind === 'daily' ? 'daily-content' : 'weekly-content')
    await expect(pagination).toContainText('第 1 / 4 页')
    await expect(pagination.getByLabel('每页')).toHaveCount(0)
    await body.fill('未保存草稿，取消翻页应保留')
    await pagination.getByRole('button', { name: '下一页' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '取消' }).click()
    await expect(pagination).toContainText('第 1 / 4 页')
    await expect(body).toHaveValue('未保存草稿，取消翻页应保留')
    await pagination.getByRole('button', { name: '下一页' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '放弃修改' }).click()
    await expect(pagination).toContainText('第 2 / 4 页')
    await expect(body).not.toHaveValue('未保存草稿，取消翻页应保留')
    await panel.getByRole('button', { name: kind === 'daily' ? '生成新日报' : '生成新周报', exact: true }).click()
    await expect(pagination).toContainText('第 1 / 5 页')
    await expect(pagination.getByRole('button', { name: '下一页' })).toBeEnabled()
    for (let next = 2; next <= 5; next++) {
      await pagination.getByRole('button', { name: '下一页' }).click()
      await expect(pagination).toContainText(`第 ${next} / 5 页`)
    }
    await panel.getByLabel(kind === 'daily' ? '日报日期' : '周报所在日期').fill('2080-01-02')
    await expect(pagination).toHaveCount(0)
    await expect(body).toHaveCount(0)
  }
})
