import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const apiBase = 'http://127.0.0.1:18080'

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
  await expect(page.getByTestId('workbench')).toBeVisible()
  await expect(page.getByText(/backend ·|postgres ·|redis ·|deepseek ·/)).toHaveCount(0)
}

async function openSection(page: Page, id: string) {
  await navigate(page, '工作汇报')
  await page.getByRole('group', { name: '报告类型' }).getByRole('button', { name: id === 'daily' ? /^日报/ : /^周报/ }).click()
}

async function navigate(page: Page, name: string) {
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: new RegExp(`^${name}`) }).click()
}

test.beforeEach(async ({ request }) => reset(request))

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

test('五个宽度四区和编辑抽屉布局无横向溢出', async ({ page, request }, testInfo) => {
  await createProject(request, '响应式项目')
  await request.post(`${apiBase}/api/tasks`, { data: { title: '响应式任务标题', notes: '测试抽屉布局', priority: 'HIGH' } })
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
  await request.post(`${apiBase}/api/records`, { data: { projectId: null, content: '完成四工作区接入，核对正文和来源的响应式布局。', occurredAt: `${date}T09:00:00+08:00` } })
  const response = await request.post(`${apiBase}/api/reports`, { data: { reportType: 'DAILY', date, requestId: crypto.randomUUID() } })
  expect(response.ok()).toBeTruthy()
  const report = await response.json()
  await expect.poll(async () => (await (await request.get(`${apiBase}/api/reports/${report.id}`)).json()).status).toBe('SUCCEEDED')
  await openWorkbench(page)
  for (const width of [320, 390, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    for (const [name, slug] of [['工作记录', 'records'], ['待办任务', 'tasks'], ['工作汇报', 'reports'], ['项目管理', 'projects']]) {
      await navigate(page, name)
      await expect(page.getByRole('heading', { name, exact: true, level: 1 })).toBeVisible()
      if (slug === 'reports') await expect(page.getByTestId('daily-content')).toBeVisible()
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      await page.screenshot({ path: testInfo.outputPath(`${slug}-${width}.png`), fullPage: true })
    }
    await navigate(page, '待办任务')
    await page.getByTestId('task-item').getByRole('button', { name: '编辑' }).click()
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
  await page.evaluate(id => localStorage.setItem('ai-workbench.pending.v1', JSON.stringify([`REPORT:${id}`])), report.id)
  await page.reload()
  await expect.poll(() => page.evaluate(() => localStorage.getItem('ai-workbench.pending.v1'))).toBe('[]')
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
  await expect(page.getByTestId('task-panel').getByRole('status')).toContainText('待办已更新')
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
  await expect(page.getByTestId('task-panel').getByRole('status')).toContainText('待办已完成')
  await page.getByRole('group', { name: '任务状态' }).getByRole('button', { name: '已完成', exact: true }).click()
  await expect(page.getByTestId('task-item').getByRole('button', { name: /^重开任务：/ })).toBeVisible()
  await navigate(page, '工作记录')
  await expect(page.getByTestId('record-item')).toContainText('任务完成')
  await navigate(page, '待办任务')

  const completed = await (await request.get(`${apiBase}/api/tasks/${task.id}`)).json() as { version: number }
  const repeated = await request.post(`${apiBase}/api/tasks/${task.id}/complete`, { data: { version: completed.version, result: '不得覆盖' } })
  expect(repeated.ok()).toBeTruthy()
  await page.getByTestId('task-item').getByRole('button', { name: /^重开任务：/ }).click()
  await expect(page.getByTestId('task-panel').getByRole('status')).toContainText('待办已重开')
  await page.getByRole('group', { name: '任务状态' }).getByRole('button', { name: '待处理', exact: true }).click()
  await expect(page.getByTestId('task-item').getByRole('button', { name: /^完成任务：/ })).toBeVisible()
  await expect(page.getByTestId('record-item')).toHaveCount(0)
  await page.getByTestId('task-item').getByRole('button', { name: /^完成任务：/ }).click()
  await page.getByRole('dialog').getByLabel('完成结果').fill('再次完成')
  await page.getByRole('dialog').getByRole('button', { name: '确认完成' }).click()
  await expect(page.getByTestId('task-panel').getByRole('status')).toContainText('待办已完成')
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
  await page.getByTestId('weekly-report').getByRole('button', { name: '生成新周报' }).click()
  await expect(page.getByTestId('weekly-content')).toContainText('本周完成', { timeout: 15_000 })
  await expect(page.getByTestId('weekly-content')).toContainText('下周明确计划')
  const weeklyBody = await page.getByTestId('weekly-content').inputValue()
  const plansSection = weeklyBody.split('## 下周计划')[1] ?? ''
  expect(plansSection).not.toContain('无期限事项')

  await page.getByTestId('weekly-content').fill('旧周报人工编辑稿')
  await page.getByTestId('weekly-report').getByRole('button', { name: '保存 AI 正文' }).click()
  await expect(page.getByTestId('weekly-report').getByRole('status')).toContainText('AI 正文已保存')
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
  await page.getByTestId('weekly-manual').fill('用户补充：风险待确认')
  await page.getByRole('button', { name: '独立保存用户补充' }).click()
  await page.getByTestId('weekly-report').getByRole('button', { name: '复制组合周报' }).click()
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toContain('用户补充（无 AI 来源标记）')

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
  await expect(page.getByRole('navigation', { name: '分页', exact: true }).last()).toContainText('第 1 / 6 页')
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
  await expect(page.getByRole('navigation', { name: '分页', exact: true }).last()).toContainText('第 2 / 6 页')
  await navigate(page, '待办任务')
  await expect(page.getByTestId('task-panel').getByRole('navigation', { name: '分页', exact: true })).toContainText('第 1 / 6 页')
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
  await expect.poll(() => page.evaluate(() => localStorage.getItem('ai-workbench.pending.v1'))).toContain('INPUT:')
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
  await page.getByTestId('weekly-report').locator('.report-sources').getByRole('navigation', { name: '分页', exact: true }).getByRole('button', { name: '下一页' }).click()
  await expect(page.getByTestId('weekly-report')).toContainText('来源 21 ·')
  const sources = page.getByTestId('weekly-report').locator('.report-sources')
  await sources.getByLabel('每页').selectOption('10')
  await expect(sources.locator('article')).toHaveCount(10)
  await expect(sources.getByRole('navigation', { name: '分页', exact: true })).toContainText('第 1 / 15 页')
  await sources.getByRole('button', { name: '下一页' }).click()
  await expect(sources).toContainText('来源 11 ·')
  await expect(sources.locator('article')).toHaveCount(10)
  await sources.getByLabel('每页').selectOption('50')
  await expect(sources.getByRole('navigation', { name: '分页', exact: true })).toContainText('第 1 / 3 页')
  await expect(sources.locator('article')).toHaveCount(50)
  await sources.getByRole('button', { name: '下一页' }).click()
  await expect(sources).toContainText('来源 51 ·')
  await sources.getByRole('button', { name: '下一页' }).click()
  await expect(sources).toContainText('来源 101 ·')
  await expect(sources.locator('article')).toHaveCount(43)
  await expect(sources.getByRole('button', { name: '下一页' })).toBeDisabled()
  await expect(sources.getByRole('button', { name: '下一页' })).toHaveCSS('cursor', 'not-allowed')
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
    await pagination.getByLabel('每页').selectOption('10')
    await expect(pagination).toContainText('第 1 / 3 页')
    await pagination.getByRole('button', { name: '下一页' }).click()
    await expect(pagination).toContainText('第 2 / 3 页')
    await pagination.getByRole('button', { name: '下一页' }).click()
    await expect(pagination).toContainText('第 3 / 3 页')
    const items = panel.getByTestId(testId === 'record-list' ? 'record-item' : 'task-item')
    await expect(items).toHaveCount(1)
    await expect(pagination.getByRole('button', { name: '下一页' })).toHaveCSS('cursor', 'not-allowed')
    await items.getByRole('button', { name: '删除', exact: true }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确认删除' }).click()
    await expect(pagination).toContainText('第 2 / 2 页')
    await expect(items).toHaveCount(10)
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
    await pagination.getByLabel('每页').selectOption('10')
    await expect(pagination).toContainText('第 1 / 2 页')
    await body.fill('未保存草稿，取消翻页应保留')
    await pagination.getByRole('button', { name: '下一页' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '取消' }).click()
    await expect(pagination).toContainText('第 1 / 2 页')
    await expect(body).toHaveValue('未保存草稿，取消翻页应保留')
    await pagination.getByRole('button', { name: '下一页' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '放弃修改' }).click()
    await expect(pagination).toContainText('第 2 / 2 页')
    await expect(body).not.toHaveValue('未保存草稿，取消翻页应保留')
    await panel.getByRole('button', { name: kind === 'daily' ? '生成新日报' : '生成新周报', exact: true }).click()
    await expect(pagination).toContainText('第 1 / 3 页')
    await expect(pagination.getByRole('button', { name: '下一页' })).toBeEnabled()
    await pagination.getByRole('button', { name: '下一页' }).click()
    await pagination.getByRole('button', { name: '下一页' }).click()
    await expect(pagination).toContainText('第 3 / 3 页')
    await panel.getByLabel(kind === 'daily' ? '日报日期' : '周报所在日期').fill('2080-01-02')
    await expect(pagination).toHaveCount(0)
    await expect(body).toHaveCount(0)
  }
})
