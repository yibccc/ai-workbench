import { expect, test, type Page } from '@playwright/test'
import { createTab, fixture, makeReport, makeSession, openInterview, openProfile, secondSessionId, sessionId } from './interview-fixture'
import type { InterviewCreate, InterviewReport, InterviewSession, InterviewSummary } from '../src/api/interview'
import type { CurrentResume } from '../src/api/resume'
import { createHash } from 'node:crypto'

const confirm = (page: Page, name: string) => page.getByRole('dialog').getByRole('button', { name, exact: true }).click()
const openSession = (page: Page, id = sessionId) => page.locator(`[data-session-id="${id}"]`).getByRole('button', { name: /继续面试|查看答卷|查看出题/ }).click()
const submittedCalls = (calls: { path: string; method: string }[]) => calls.filter(call => call.path.endsWith('/submit') && call.method === 'POST')

test.describe('受控 HTTP fixture：仅 UI 行为证据', () => {
  test('UTF-8 File 先编辑后保存；原字节和最终正文分别发送，后续编辑保持原件', async ({ page }) => {
    const state = await fixture(page)
    await openProfile(page)
    const original = '# 原始简历\nemoji 😀\n'
    await page.getByLabel('选择新的 .md 文件', { exact: true }).setInputFiles({ name: 'source.md', mimeType: 'text/markdown', buffer: Buffer.from(original) })
    await expect(page.getByLabel('简历 Markdown')).toHaveValue(original)
    expect(state.calls.filter(call => call.path.endsWith('/import'))).toHaveLength(0)
    await page.getByLabel('简历 Markdown').fill('# 实际保存正文')
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click()
    await expect(page.getByText('已保存原件', { exact: false })).toBeVisible()
    const call = state.calls.find(call => call.path.endsWith('/import'))
    expect(call?.buffer?.toString('utf8')).toContain(original)
    expect(call?.buffer?.toString('utf8')).toContain('# 实际保存正文')
    await page.getByLabel('简历 Markdown').fill('# 普通编辑')
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click()
    await expect.poll(() => state.calls.filter(call => call.method === 'PUT' && call.path === '/api/me/resume').length).toBe(1)
    const edit = state.calls.find(call => call.method === 'PUT' && call.path === '/api/me/resume')
    expect(edit?.data?.mode).toBe('EDIT_CURRENT')
    expect(state.resume.originalFile?.fileName).toBe('source.md')
  })

  test('File 字节/UTF-8/正文三个门禁独立拒绝并保留草稿', async ({ page }) => {
    await fixture(page); await openProfile(page)
    await page.getByLabel('简历 Markdown').fill('有用草稿')
    const input = page.getByLabel('选择新的 .md 文件', { exact: true })
    for (const [name, buffer, error] of [
      ['wrong.txt', Buffer.from('text'), '只支持 .md 文件'],
      ['oversize.md', Buffer.alloc(1_048_577, 97), '原文件超过 1 MiB'],
      ['byte-boundary.md', Buffer.alloc(1_048_576, 97), '原文件正文超过 20,000'],
      ['invalid.md', Buffer.from([0xc3, 0x28]), '有效 UTF-8'],
      ['characters.md', Buffer.from('😀'.repeat(20_001)), '原文件正文超过 20,000'],
    ] as const) {
      await input.setInputFiles({ name, mimeType: 'text/markdown', buffer })
      await expect(page.getByRole('alert')).toContainText(error)
      await expect(page.getByLabel('简历 Markdown')).toHaveValue('有用草稿')
    }
  })

  test('简历码点 20k/+1 保留 emoji 全文；空正文可保存且 exists 为真', async ({ page }) => {
    const state = await fixture(page); await openProfile(page)
    const input = page.getByLabel('简历 Markdown'); const save = page.getByRole('button', { name: '保存为当前简历', exact: true })
    await input.fill('😀'.repeat(20_000)); await expect(save).toBeEnabled()
    await expect(page.getByText('20,000 / 20,000 字符', { exact: true })).toBeVisible()
    await input.fill('😀'.repeat(20_001)); await expect(save).toBeDisabled(); await expect(input).toHaveValue('😀'.repeat(20_001))
    expect(await input.getAttribute('maxlength')).toBeNull()
    await input.fill(''); await save.click(); await expect.poll(() => state.resume.markdownText).toBe('')
    expect(state.resume.exists).toBe(true)
  })

  test('保存丢 ACK 同 tuple/requestId 重放；版本冲突需明确确认且保留本地正文', async ({ page }) => {
    const state = await fixture(page); state.resumeFailOnce = true; await openProfile(page)
    await page.getByLabel('简历 Markdown').fill('未确认正文')
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click()
    await expect(page.getByRole('button', { name: '确认保存结果', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '确认保存结果', exact: true }).click()
    await expect.poll(() => state.resume.markdownText).toBe('未确认正文')
    const writes = state.calls.filter(call => call.path === '/api/me/resume' && call.method === 'PUT')
    expect(writes[0].data).toEqual(writes[1].data)
    await expect(page.getByRole('button', { name: '保存为当前简历', exact: true })).toBeEnabled()
    state.resume = { ...state.resume, version: state.resume.version + 1, markdownText: '另一页生效正文' }
    await page.getByLabel('简历 Markdown').fill('本地冲突草稿')
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('已被另一页面修改')
    await expect(page.getByLabel('简历 Markdown')).toHaveValue('本地冲突草稿')
    await page.getByRole('button', { name: '确认版本并重试保存' }).click()
    await expect(page.getByRole('dialog')).toContainText('以保留的本地正文替换')
    await confirm(page, '确认并保存')
    await expect.poll(() => state.resume.markdownText).toBe('本地冲突草稿')
  })

  test('四方向、三难度、完整 3–20；JD Unicode 边界和任何字符/方向变更失效', async ({ page }) => {
    const state = await fixture(page); await openInterview(page); await createTab(page)
    await expect(page.getByRole('radio', { name: /Java 后端|React 前端|Agent 开发|全栈/ })).toHaveCount(4)
    await expect(page.getByLabel('难度').locator('option')).toHaveCount(3)
    await expect(page.getByLabel('主问题数量').locator('option')).toHaveCount(18)
    await page.getByLabel('主问题数量').selectOption('20')
    await expect(page.getByRole('button', { name: '创建面试 · 40 轮', exact: true })).toBeEnabled()
    const jd = page.getByLabel('职位描述'); await jd.fill('😀'.repeat(10_001))
    await expect(jd).toHaveValue('😀'.repeat(10_001)); await expect(page.getByRole('button', { name: '解析 JD', exact: true })).toBeDisabled()
    await jd.fill('😀'.repeat(10_000)); await expect(page.getByRole('button', { name: '解析 JD', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '解析 JD', exact: true }).click(); await expect(page.getByText('已解析', { exact: true })).toBeVisible()
    await jd.fill('😀'.repeat(9_999) + ' '); await expect(page.getByRole('button', { name: '创建面试 · 40 轮' })).toBeDisabled()
    await expect.poll(() => state.calls.filter(call => call.method === 'DELETE' && call.path.includes('/jd/')).length).toBe(1)
    await page.getByRole('button', { name: '解析 JD', exact: true }).click(); await expect(page.getByText('已解析', { exact: true })).toBeVisible()
    await page.getByRole('radio', { name: /React 前端/ }).check(); await expect(page.getByRole('button', { name: '创建面试 · 40 轮' })).toBeDisabled()
    await expect.poll(() => state.calls.filter(call => call.method === 'DELETE' && call.path.includes('/jd/')).length).toBe(2)
  })

  test('JD 失败/不匹配保留原文；手动 retry，明确移除后才可无 JD 创建', async ({ page }) => {
    const state = await fixture(page); state.jdFailed = true; await openInterview(page); await createTab(page)
    await page.getByLabel('职位描述').fill('目标岗位完整原文')
    await page.getByRole('button', { name: '解析 JD', exact: true }).click()
    await expect(page.getByRole('button', { name: '创建面试 · 10 轮' })).toBeDisabled()
    const parseCount = () => state.calls.filter(call => call.path === '/api/interviews/jd/parse').length
    expect(parseCount()).toBe(1)
    await expect(page.getByRole('button', { name: '解析 JD', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: '手动重试 JD 解析' }).click(); await expect(page.getByText('已解析', { exact: true })).toBeVisible()
    expect(parseCount()).toBe(1)
    await page.getByLabel('职位描述').fill('不匹配岗位'); state.jdFailed = false; state.jdMismatch = true
    await page.getByRole('button', { name: '解析 JD', exact: true }).click(); await expect(page.getByText('方向不匹配', { exact: true })).toBeVisible()
    await expect(page.getByLabel('职位描述')).toHaveValue('不匹配岗位')
    await expect(page.getByRole('button', { name: '创建面试 · 10 轮' })).toBeDisabled()
    await page.getByRole('button', { name: '移除 JD', exact: true }).click(); await confirm(page, '移除 JD')
    await expect(page.getByLabel('职位描述')).toHaveValue(''); await expect(page.getByRole('button', { name: '创建面试 · 10 轮' })).toBeEnabled()
  })

  test('迟到 JD 解析不能绑定已编辑文本，修改回来也不能恢复旧确认', async ({ page }) => {
    const state = await fixture(page); let release = () => {}; const pending = new Promise<void>(resolve => { release = resolve }); state.holdJd = () => pending
    await openInterview(page); await createTab(page); await page.getByLabel('职位描述').fill('JD A')
    await page.getByRole('button', { name: '解析 JD', exact: true }).click()
    await expect.poll(() => state.analyses.size).toBe(1)
    await page.getByLabel('职位描述').fill('JD B'); await page.getByLabel('职位描述').fill('JD A'); release()
    await expect(page.getByText('旧 JD 解析结果已丢弃')).toBeVisible()
    await expect(page.getByRole('button', { name: '创建面试 · 10 轮' })).toBeDisabled()
    await expect.poll(() => state.analyses.size).toBe(0)
  })

  test('创建丢 ACK 同 requestId 恢复；成功解析可复用多场，不能 consume', async ({ page }) => {
    const state = await fixture(page); state.createDropOnce = true; await openInterview(page); await createTab(page)
    await page.getByLabel('职位描述').fill('Spring 岗位原文'); await page.getByRole('button', { name: '解析 JD', exact: true }).click()
    await expect(page.getByText('已解析', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '创建面试 · 10 轮', exact: true }).click()
    await expect(page.getByRole('button', { name: '确认创建结果', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '确认创建结果', exact: true }).click(); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toBeVisible()
    expect(state.sessions.size).toBe(1)
    const firstCalls = state.calls.filter(call => call.path === '/api/interviews' && call.method === 'POST')
    expect(firstCalls[0].data).toEqual(firstCalls[1].data)
    await createTab(page); await expect(page.getByText('已解析', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '创建面试 · 10 轮', exact: true }).click(); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toBeVisible()
    expect(state.sessions.size).toBe(2)
    expect(state.calls.filter(call => call.path === '/api/interviews/jd/parse')).toHaveLength(1)
    expect(state.calls.filter(call => call.method === 'DELETE' && call.path.includes('/jd/'))).toHaveLength(0)
  })

  test('答案 5k/+1 与空提交；草稿不推进，丢 ACK 读 SUBMITTED 只推进一次', async ({ page }) => {
    const state = await fixture(page, [makeSession()]); await openInterview(page); await openSession(page)
    const input = page.locator('.interview-session-view:visible').getByLabel('你的回答'); await input.fill('😀'.repeat(5_001))
    await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeDisabled(); await expect(input).toHaveValue('😀'.repeat(5_001))
    await input.fill('😀'.repeat(5_000)); await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect.poll(() => state.sessions.get(sessionId)?.answers[0]?.status).toBe('DRAFT'); expect(state.sessions.get(sessionId)?.currentTurn).toBe(0)
    await input.fill(''); state.submitDropOnce = true
    await page.getByRole('button', { name: '提交并进入追问 →', exact: true }).click()
    await expect(page.locator('.q-type:visible')).toContainText('第 2 / 6 轮')
    expect(state.sessions.get(sessionId)?.answers[0].answerText).toBe(''); expect(state.sessions.get(sessionId)?.answers[0].status).toBe('SUBMITTED')
    expect(submittedCalls(state.calls)).toHaveLength(1)
  })

  test('409 保留旧标签页输入，已锁定答案不覆盖；多场 retained 草稿相互独立', async ({ page }) => {
    const state = await fixture(page, [makeSession(), makeSession(secondSessionId)])
    await openInterview(page); await openSession(page); await page.locator('.interview-session-view:visible').getByLabel('你的回答').fill('第一场本地输入'); state.submitConflictOnce = true
    await page.getByRole('button', { name: '提交并进入追问 →', exact: true }).click()
    await expect(page.getByLabel('冲突时的答案')).toHaveValue('第一场本地输入')
    expect(state.sessions.get(sessionId)?.answers[0].answerText).toBe('另一标签页提交')
    await page.locator('.interview-session-view:visible').getByLabel('你的回答').fill('第一场第二轮草稿')
    await page.getByRole('button', { name: '返回面试列表', exact: true }).click(); await confirm(page, '保留草稿并离开')
    await openSession(page, secondSessionId); await page.locator('.interview-session-view:visible').getByLabel('你的回答').fill('第二场草稿')
    await page.getByRole('button', { name: '返回面试列表', exact: true }).click(); await confirm(page, '保留草稿并离开')
    await openSession(page); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toHaveValue('第一场第二轮草稿')
  })

  test('提前交卷取消无写；有未提交草稿仍未答；失败无总分且只能显式重试', async ({ page }) => {
    const session = makeSession(); session.answers = [{ turnIndex: 0, status: 'SUBMITTED', answerText: '', version: 2 }]; session.currentTurn = 1; session.submittedCount = 1; session.version = 2
    const state = await fixture(page, [session]); await openInterview(page); await openSession(page)
    await page.locator('.interview-session-view:visible').getByLabel('你的回答').fill('未提交的正文草稿')
    await page.getByRole('button', { name: '提前交卷', exact: true }).click()
    await expect(page.getByRole('dialog')).toContainText('已提交 1 轮，未提交 5 轮')
    await confirm(page, '取消'); expect(state.calls.filter(call => call.path.endsWith('/complete'))).toHaveLength(0)
    await page.getByRole('button', { name: '提前交卷', exact: true }).click(); await confirm(page, '确认交卷')
    await expect(page.getByText('尚无总分', { exact: true })).toBeVisible(); await expect(page.getByText('已提交（空内容）')).toBeVisible()
    await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toHaveCount(0)
    const modelWrites = () => state.calls.filter(call => call.path.endsWith('/evaluation/retry')).length
    await page.getByRole('button', { name: '重新读取', exact: true }).click(); expect(modelWrites()).toBe(0)
    await page.getByRole('button', { name: '手动重试评估', exact: true }).click(); await expect.poll(modelWrites).toBe(1)
    await expect(page.locator('.score')).toContainText('0/100')
    await expect(page.getByText('空答案未提供具体依据。')).toBeVisible()
  })

  test('所有阶段可删除；取消无写，404 清题目；后台 GET 不主动 signalActivity', async ({ page }) => {
    const pending = makeSession(); pending.generationStatus = 'FAILED'; pending.answerStatus = 'NOT_READY'
    const state = await fixture(page, [pending, makeSession(secondSessionId)])
    await openInterview(page); await openSession(page); await page.getByRole('button', { name: '删除面试', exact: true }).click()
    await confirm(page, '取消'); expect(state.sessions.size).toBe(2)
    await page.getByRole('button', { name: '删除面试', exact: true }).click(); await confirm(page, '确认删除')
    await expect(page.locator(`[data-session-id="${sessionId}"]`)).toHaveCount(0); expect(state.sessions.has(secondSessionId)).toBe(true)
    await openSession(page, secondSessionId); state.sessions.delete(secondSessionId)
    await page.getByRole('button', { name: '重新读取', exact: true }).click()
    await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toHaveCount(0); await expect(page.getByText('固定题目 1：', { exact: false })).toHaveCount(0)
    const activity = state.calls.filter(call => call.path === '/api/auth/activity').length
    await page.waitForTimeout(2_500)
    expect(state.calls.filter(call => call.path === '/api/auth/activity')).toHaveLength(activity)
  })

  test('Markdown 危险 HTML 与远程图片仅展示文字，不执行或请求外网', async ({ page }) => {
    const session = makeSession(); session.questions[0].text = '<script>window.interviewExecuted=true</script>\n![外部图片](https://example.invalid/private.png)\n[危险](javascript:alert(1))\n```html\n<img src=x onerror=alert(1)>\n```'
    const requests: string[] = []; page.on('request', request => { if (request.url().includes('example.invalid')) requests.push(request.url()) })
    await fixture(page, [session]); await openInterview(page); await openSession(page)
    await expect(page.getByText('[外链图片不加载：外部图片]', { exact: true })).toBeVisible()
    expect(await page.evaluate(() => 'interviewExecuted' in window)).toBe(false)
    expect(requests).toHaveLength(0); await expect(page.locator('.question-card img, .question-card script')).toHaveCount(0)
  })

  test('真实 401 移除 File/草稿/待确认状态；B 登录后只有 B 的简历', async ({ page }) => {
    const state = await fixture(page); await openProfile(page)
    await page.getByLabel('选择新的 .md 文件', { exact: true }).setInputFiles({ name: 'private-A.md', mimeType: 'text/markdown', buffer: Buffer.from('只属于 A 的原件') })
    await expect(page.getByLabel('简历 Markdown')).toHaveValue('只属于 A 的原件')
    await page.route(url => url.pathname === '/api/me/resume/import', route => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ detail: '合成真实401响应' }) }))
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click()
    await expect(page.getByTestId('workbench')).toHaveCount(0)
    await expect(page.getByLabel('用户名')).toBeVisible()
    await expect(page.getByText('会话已失效，请重新登录', { exact: true })).toBeVisible()
    state.accountId = 'd9800000-0000-4000-8000-000000000200'; state.resume = { exists: false, version: 0, markdownText: null, sourceKind: null, originalFile: null }
    await page.getByLabel('用户名').fill('B_fixture'); await page.getByLabel('密码', { exact: true }).fill('Synthetic-B-password'); await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByLabel('简历 Markdown')).toHaveValue('')
    await expect(page.getByText('private-A.md', { exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '确认保存结果', exact: true })).toHaveCount(0)
  })

  test('403 只是权限失败；dirty 生效版本读取不覆盖草稿或静默提升保存版本', async ({ page }) => {
    const state = await fixture(page); await openProfile(page)
    await page.getByLabel('简历 Markdown').fill('本地私有草稿')
    state.resume = { ...state.resume, version: 2, markdownText: '另页生效简历' }
    await page.getByRole('button', { name: '重新读取生效版本', exact: true }).click()
    await expect(page.getByLabel('简历 Markdown')).toHaveValue('本地私有草稿')
    await page.getByRole('button', { name: '确认版本并重试保存' }).click()
    await expect(page.getByRole('dialog')).toBeVisible(); await confirm(page, '取消')
    expect(state.calls.filter(call => call.path === '/api/me/resume' && call.method === 'PUT')).toHaveLength(0)
    await page.route(url => url.pathname === '/api/me/resume', route => route.request().method() === 'PUT' ? route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ detail: '没有保存权限' }) }) : route.fallback())
    await page.getByRole('button', { name: '确认版本并重试保存' }).click(); await confirm(page, '确认并保存')
    await expect(page.getByRole('alert')).toContainText('没有保存权限')
    await expect(page.getByTestId('workbench')).toBeVisible(); await expect(page.getByLabel('简历 Markdown')).toHaveValue('本地私有草稿')
  })

  test('评估轮询收到 404 后立即移除旧题单、答案与报告', async ({ page }) => {
    const session = makeSession(); session.answerStatus = 'COMPLETED'; session.evaluationStatus = 'PROCESSING'
    session.answers = [{ turnIndex: 0, status: 'SUBMITTED', answerText: '已删除场次的私有答案', version: 2 }]; session.submittedCount = 1
    const state = await fixture(page, [session]); await openInterview(page); await openSession(page)
    await expect(page.getByText('已删除场次的私有答案', { exact: true })).toBeVisible()
    state.sessions.delete(sessionId)
    await expect(page.getByRole('alert')).toContainText('场次不存在', { timeout: 10_000 })
    await expect(page.getByText('已删除场次的私有答案', { exact: true })).toHaveCount(0)
    await expect(page.locator('.feedback')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除面试', exact: true })).toHaveCount(0)
  })

  test('简历保存后的迟到旧读取不能回退生效摘要或编辑正文', async ({ page }) => {
    const state = await fixture(page); await openProfile(page)
    let release = () => {}; let requested = () => {}; let releaseAuthority = () => {}; let requestedAuthority = () => {}
    const pending = new Promise<void>(resolve => { release = resolve })
    const started = new Promise<void>(resolve => { requested = resolve })
    const authoritativePending = new Promise<void>(resolve => { releaseAuthority = resolve })
    const authoritativeStarted = new Promise<void>(resolve => { requestedAuthority = resolve })
    let holdNext = true
    await page.route(url => url.pathname === '/api/me/resume', async route => {
      if (route.request().method() !== 'GET') return route.fallback()
      if (!holdNext) { requestedAuthority(); await authoritativePending; return route.fallback() }
      holdNext = false; const previous = structuredClone(state.resume); requested(); await pending
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(previous) })
    })
    await page.getByRole('button', { name: '重新读取生效版本', exact: true }).click(); await started
    await page.getByLabel('简历 Markdown').fill('保存后的新正文')
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click()
    await authoritativeStarted
    const oldResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/me/resume' && response.request().method() === 'GET')
    release(); await oldResponse
    await expect(page.getByText('正在读取当前简历…', { exact: true })).toHaveCount(0)
    await expect(page.getByLabel('简历 Markdown')).toHaveValue('保存后的新正文')
    releaseAuthority()
    await expect(page.getByText('生效版本 2', { exact: false })).toBeVisible()
  })

  for (const operation of ['submit', 'complete'] as const) {
    test(`${operation} 已成功但状态读取失败时冻结旧轮，重新读取后恢复权威进度`, async ({ page }) => {
      const state = await fixture(page, [makeSession()]); await openInterview(page); await openSession(page)
      const input = page.locator('.interview-session-view:visible').getByLabel('你的回答')
      await input.fill('确证提交的正文')
      let failNextRead = true
      await page.route(url => url.pathname === `/api/interviews/${sessionId}`, route => {
        if (route.request().method() !== 'GET' || !failNextRead) return route.fallback()
        failNextRead = false; return route.abort('failed')
      })
      if (operation === 'submit') await page.getByRole('button', { name: '提交并进入追问 →', exact: true }).click()
      else { await page.getByRole('button', { name: '提前交卷', exact: true }).click(); await confirm(page, '确认交卷') }
      await expect(page.getByRole('alert')).toContainText('操作已成功，当前状态刷新失败')
      await expect(input).toBeDisabled()
      await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeDisabled()
      await expect(page.getByRole('button', { name: '提前交卷', exact: true })).toBeDisabled()
      expect(state.calls.filter(call => call.path.endsWith(operation === 'submit' ? '/submit' : '/complete'))).toHaveLength(1)
      await page.getByRole('button', { name: '重新读取', exact: true }).click()
      if (operation === 'submit') { await expect(page.locator('.q-type:visible')).toContainText('第 2 / 6 轮'); await expect(input).toBeEnabled() }
      else { await expect(input).toHaveCount(0); await expect(page.getByText('本场答卷已冻结', { exact: true })).toBeVisible() }
    })
  }

  test('JD 修改后新分析的手动 retry 不重放旧分析的未知请求', async ({ page }) => {
    const state = await fixture(page); state.jdFailed = true; await openInterview(page); await createTab(page)
    await page.getByLabel('职位描述').fill('旧 JD')
    await page.getByRole('button', { name: '解析 JD', exact: true }).click()
    const previousId = [...state.analyses.keys()][0]; let dropOnce = true
    await page.route(url => url.pathname.endsWith('/retry') && url.pathname.includes('/jd/'), route => {
      if (!dropOnce) return route.fallback()
      dropOnce = false; return route.abort('failed')
    })
    await page.getByRole('button', { name: '手动重试 JD 解析', exact: true }).click()
    await expect(page.getByRole('alert').first()).toBeVisible()
    await page.getByLabel('职位描述').fill('新 JD')
    await expect.poll(() => state.analyses.has(previousId)).toBe(false)
    await page.getByRole('button', { name: '解析 JD', exact: true }).click()
    const nextId = [...state.analyses.keys()][0]
    await page.getByRole('button', { name: '手动重试 JD 解析', exact: true }).click()
    await expect(page.getByText('已解析', { exact: true })).toBeVisible()
    expect(state.calls.filter(call => call.path.endsWith('/retry')).map(call => call.path)).toEqual([`/api/interviews/jd/${nextId}/retry`])
  })

  test('确认弹窗中的后台401立即关闭旧账号确认，不能在B执行A的删除', async ({ page }) => {
    const session = makeSession(); session.evaluationStatus = 'PROCESSING'; session.answerStatus = 'COMPLETED'
    const state = await fixture(page, [session]); await openInterview(page); await openSession(page)
    await page.getByRole('button', { name: '删除面试', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible()
    await page.route(url => url.pathname === `/api/interviews/${sessionId}`, route => route.request().method() === 'GET' ? route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ detail: '合成轮询401' }) }) : route.fallback())
    await expect(page.getByLabel('用户名')).toBeVisible({ timeout: 10_000 }); await expect(page.getByRole('dialog')).toHaveCount(0)
    state.accountId = 'd9800000-0000-4000-8000-000000000200'; state.sessions.clear()
    await page.getByLabel('用户名').fill('B_fixture'); await page.getByLabel('密码', { exact: true }).fill('Synthetic-B-password'); await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByTestId('workbench')).toBeVisible(); await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(state.calls.filter(call => call.method === 'DELETE' && call.path.startsWith('/api/interviews/'))).toHaveLength(0)
  })

  test('原项目共享Dialog回归：取消零写/焦点恢复，失败留输入，busy不能Escape或双提交', async ({ page }) => {
    await fixture(page)
    const project = { id: 'd9800000-0000-4000-8000-000000000400', name: '原项目', status: 'ACTIVE', archivedAt: null, createdAt: '2026-10-04T00:00:00Z', updatedAt: '2026-10-04T00:00:00Z' }
    await page.route(url => url.pathname === '/api/projects/page', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: [project], page: 0, size: 5, totalElements: 1, totalPages: 1 }) }))
    let writes = 0; let release = () => {}; const pending = new Promise<void>(resolve => { release = resolve })
    await page.route(url => url.pathname === `/api/projects/${project.id}`, async route => {
      writes++
      if (writes === 1) return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: '合成改名失败' }) })
      await pending; project.name = (route.request().postDataJSON() as { name: string }).name
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify(project) })
    })
    await page.goto('/#projects'); const trigger = page.getByRole('button', { name: '改名', exact: true })
    await trigger.click(); await confirm(page, '取消'); expect(writes).toBe(0); await expect(trigger).toBeFocused()
    await trigger.click(); await page.getByRole('dialog').getByLabel('项目名称').fill('修改后项目')
    await confirm(page, '保存名称'); await expect(page.getByRole('dialog')).toContainText('合成改名失败'); await expect(page.getByRole('dialog').getByLabel('项目名称')).toHaveValue('修改后项目')
    await page.getByRole('dialog').getByRole('button', { name: '保存名称', exact: true }).evaluate(element => { (element as HTMLButtonElement).click(); (element as HTMLButtonElement).click() })
    await expect.poll(() => writes).toBe(2); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeVisible(); await expect(page.getByRole('dialog').getByRole('button', { name: '取消' })).toBeDisabled()
    release(); await expect(page.getByRole('dialog')).toHaveCount(0); await expect(page.getByText('修改后项目', { exact: true })).toBeVisible(); await expect(trigger).toBeFocused(); expect(writes).toBe(2)
  })

  test('六导航与个人中心 retained 草稿；导航不重新初始化账号根专注控制器', async ({ page }) => {
    const state = await fixture(page)
    const focus = { id: 'd9800000-0000-4000-8000-000000000300', requestId: 'd9800000-0000-4000-8000-000000000301', title: '保留专注', taskId: null, projectId: null, targetMs: 1_500_000, intervalMs: 600_000, zoneId: 'Asia/Shanghai', phase: 'RUNNING', version: 1, startedAt: new Date().toISOString(), anchorAt: new Date().toISOString(), endedAt: null, focusMs: 100_000, breakMs: 0, pauseMs: 0, resumePhase: null, breakRemainingMs: 15_000, nextBreakAtMs: 600_000, remindersDismissed: false, reminderOrdinal: 0, controllerId: null, controllerGeneration: 0, controllerExpiresAt: null, progress: '' }
    let currentReads = 0
    await page.route(url => url.pathname === '/api/focus/current', route => { currentReads++; return route.fulfill({ contentType: 'application/json', body: JSON.stringify(focus) }) })
    await page.route(url => url.pathname.startsWith(`/api/focus/sessions/${focus.id}`), route => route.fulfill({ contentType: 'application/json', body: JSON.stringify(focus) }))
    await openInterview(page); await expect(page.getByRole('link', { name: /^返回专注：保留专注/ })).toBeVisible()
    const initialReads = currentReads; expect(initialReads).toBeGreaterThan(0)
    await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '个人中心', exact: true }).click()
    await page.getByLabel('简历 Markdown').fill('仍然保留的 Profile 草稿')
    await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: 'AI 面试', exact: true }).click(); await confirm(page, '保留草稿并离开')
    await expect(page.getByRole('link', { name: /^返回专注：保留专注/ })).toBeVisible()
    const finalResumeRead = page.waitForResponse(response => new URL(response.url()).pathname === '/api/me/resume' && response.request().method() === 'GET')
    await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '个人中心', exact: true }).click()
    await expect(page.getByLabel('简历 Markdown')).toHaveValue('仍然保留的 Profile 草稿')
    await finalResumeRead; await expect(page.getByText('正在读取当前简历…', { exact: true })).toHaveCount(0)
    expect(currentReads).toBe(initialReads); expect(state.calls.filter(call => call.path.startsWith('/api/focus/sessions') && call.method === 'POST')).toHaveLength(0)
  })

  for (const viewport of [{ width: 320, height: 520 }, { width: 390, height: 520 }, { width: 760, height: 700 }, { width: 1440, height: 900 }]) {
    test(`六屏几何与 40 轮可达 ${viewport.width}×${viewport.height}`, async ({ page }, info) => {
      await page.setViewportSize(viewport)
      const running = makeSession(sessionId, 20); const completed = makeSession(secondSessionId, 20)
      completed.answerStatus = 'COMPLETED'; completed.evaluationStatus = 'FAILED'; completed.answers = [{ turnIndex: 0, status: 'SUBMITTED', answerText: '真实字段长度的合成答案 '.repeat(300), version: 2 }]; completed.submittedCount = 1
      const state = await fixture(page, [running, completed]); state.reports.set(completed.id, makeReport(completed))
      const geometry = async (screen: string) => {
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight)).toBeLessThanOrEqual(viewport.height + 1)
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(viewport.width + 1)
        const regions = page.locator('[role="region"]:visible'); const count = await regions.count()
        for (let index = 0; index < count; index++) expect(await regions.nth(index).evaluate(element => element.clientHeight)).toBeGreaterThan(0)
        await page.screenshot({ path: info.outputPath(`${screen}-${viewport.width}.png`) })
      }
      await openInterview(page); await expect(page.getByRole('navigation', { name: '主导航' }).getByRole('link')).toHaveCount(6)
      await geometry('history'); await createTab(page); await geometry('create')
      await page.getByRole('group', { name: '面试页面' }).getByRole('button', { name: '面试中心', exact: true }).click()
      await openSession(page); await expect(page.locator('.timeline .turn')).toHaveCount(40)
      await page.getByRole('region', { name: '面试固定题单与进度' }).evaluate(element => { element.scrollTop = element.scrollHeight })
      await page.locator('.timeline .turn').last().scrollIntoViewIfNeeded()
      await expect(page.locator('.timeline .turn').last()).toBeInViewport(); await page.getByRole('button', { name: '提前交卷', exact: true }).scrollIntoViewIfNeeded(); await expect(page.getByRole('button', { name: '提前交卷', exact: true })).toBeInViewport(); await geometry('answer')
      await page.getByRole('button', { name: '返回面试列表', exact: true }).click(); await openSession(page, secondSessionId)
      await expect(page.locator('.feedback')).toHaveCount(40); await page.getByRole('region', { name: '逐轮面试反馈' }).evaluate(element => { element.scrollTop = element.scrollHeight })
      await page.locator('.feedback').last().scrollIntoViewIfNeeded(); await expect(page.locator('.feedback').last()).toBeInViewport(); await geometry('report')
      await page.getByRole('button', { name: '删除面试', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible(); await geometry('delete'); await confirm(page, '取消')
      await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '个人中心', exact: true }).click()
      await expect(page.getByLabel('简历 Markdown')).toBeVisible(); await page.getByRole('button', { name: '删除当前简历', exact: true }).scrollIntoViewIfNeeded(); await expect(page.getByRole('button', { name: '删除当前简历', exact: true })).toBeInViewport(); await geometry('resume')
    })
  }
})

test.describe('真实 HTTP/隔离 DB/Redis/RustFS 与 deterministic gateway', () => {
  test('原 File 编辑保存→A/B 两场快照→独立草稿/刷新重登→交卷报告→删 current/单场', async ({ page }, info) => {
    test.setTimeout(120_000)
    const creations: InterviewCreate[] = []; let jdParses = 0; let activitySignals = 0
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (request.method() === 'POST' && path === '/api/interviews') creations.push(request.postDataJSON() as InterviewCreate)
      if (request.method() === 'POST' && path === '/api/interviews/jd/parse') jdParses++
      if (request.method() === 'POST' && path === '/api/auth/activity') activitySignals++
    })
    await page.goto('/#profile')
    await page.getByLabel('用户名').fill('e2e_admin'); await page.getByLabel('密码', { exact: true }).fill('E2eOnly-Synthetic-9384!'); await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByTestId('workbench')).toBeVisible()
    const csrf = await (await page.request.get('/api/auth/csrf')).json() as { token: string }
    // A fresh owner isolates this test from existing suites and stored session history.
    const username = `e2e_interview_${crypto.randomUUID().slice(0, 8)}`; const password = 'E2eOnly-Interview-9482!'
    const account = await page.request.post('/api/admin/users', { headers: { 'X-XSRF-TOKEN': csrf.token }, data: { username, password, role: 'USER' } }); expect(account.status()).toBe(201)
    await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
    await page.getByLabel('用户名').fill(username); await page.getByLabel('密码', { exact: true }).fill(password); await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByLabel('简历 Markdown')).toBeVisible()
    const original = '# 不可变原件 A\nUTF-8 😀'
    await page.getByLabel('选择新的 .md 文件', { exact: true }).setInputFiles({ name: 'real-resume.md', mimeType: 'text/markdown', buffer: Buffer.from(original) })
    await page.getByLabel('简历 Markdown').fill('# 保存正文 A')
    await page.getByRole('button', { name: '保存为当前简历', exact: true }).click(); await expect(page.getByText('已保存原件', { exact: false })).toBeVisible()
    const source = await page.request.get('/api/me/resume/original'); expect(source.status()).toBe(200); expect(await source.text()).toBe(original); expect(source.headers()['cache-control']).toContain('no-store')
    const savedResume = await (await page.request.get('/api/me/resume')).json() as CurrentResume
    expect(savedResume.markdownText).toBe('# 保存正文 A'); expect(savedResume.originalFile?.sha256).toBe(createHash('sha256').update(original, 'utf8').digest('hex'))
    await page.screenshot({ path: info.outputPath('real-profile-saved.png') })
    await page.getByRole('link', { name: '返回 AI 面试', exact: true }).click(); await createTab(page)
    await page.getByLabel('主问题数量').selectOption('3'); await page.getByRole('radio', { name: /使用当前简历/ }).check()
    const jdText = 'Java 后端岗位：Spring、数据库一致性与失败恢复。'
    await page.getByLabel('职位描述').fill(jdText); await page.getByRole('button', { name: '解析 JD', exact: true }).click()
    await expect(page.getByText('已解析', { exact: true })).toBeVisible({ timeout: 30_000 })
    await page.getByRole('button', { name: '创建面试 · 6 轮', exact: true }).click(); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toBeVisible({ timeout: 30_000 })
    const listA = await (await page.request.get('/api/interviews/page?page=0&size=5')).json() as { items: InterviewSummary[] }; const first = listA.items[0]
    expect(creations[0].jdText).toBe(jdText); expect(creations[0].jdAnalysisId).toBeTruthy()
    const parsed = await (await page.request.get(`/api/interviews/jd/${creations[0].jdAnalysisId}`)).json() as { status: string; jdText: string }
    expect(parsed.status).toBe('SUCCEEDED'); expect(parsed.jdText).toBe(jdText); expect(jdParses).toBe(1)
    await page.locator('.interview-session-view:visible').getByLabel('你的回答').fill('第一场独立草稿'); await page.getByRole('button', { name: '保存并离开', exact: true }).click()
    await expect(page.locator(`[data-session-id="${first.id}"]`)).toBeVisible()
    await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '个人中心', exact: true }).click()
    await page.getByLabel('简历 Markdown').fill('# 保存正文 B'); await page.getByRole('button', { name: '保存为当前简历', exact: true }).click(); await expect(page.getByText('有未保存修改')).toHaveCount(0)
    await page.getByRole('link', { name: '返回 AI 面试', exact: true }).click(); await createTab(page)
    await page.getByRole('button', { name: /^确认使用版本/ }).click()
    await page.getByRole('radio', { name: /React 前端/ }).check(); await page.getByRole('combobox', { name: /^难度/ }).selectOption('SENIOR'); await page.getByLabel('主问题数量').selectOption('4')
    await expect(page.getByRole('button', { name: '创建面试 · 8 轮', exact: true })).toBeDisabled()
    await page.getByRole('button', { name: '移除 JD', exact: true }).click(); await confirm(page, '移除 JD')
    await page.getByRole('button', { name: '创建面试 · 8 轮', exact: true }).click(); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toBeVisible({ timeout: 30_000 })
    const listB = await (await page.request.get('/api/interviews/page?page=0&size=5')).json() as { items: InterviewSummary[] }; const second = listB.items.find(item => item.id !== first.id)!
    const a = await (await page.request.get(`/api/interviews/${first.id}`)).json() as InterviewSession; const b = await (await page.request.get(`/api/interviews/${second.id}`)).json() as InterviewSession
    expect(a.resumeSnapshot).toBe('# 保存正文 A'); expect(b.resumeSnapshot).toBe('# 保存正文 B')
    expect(a.jdText).toBe(jdText); expect(b.jdText).toBeNull(); expect(jdParses).toBe(1)
    expect(a.direction).not.toBe(b.direction); expect(a.mainQuestionCount).not.toBe(b.mainQuestionCount)
    await page.locator('.interview-session-view:visible').getByLabel('你的回答').fill('第二场独立草稿'); await page.getByRole('button', { name: '保存并离开', exact: true }).click(); await page.reload()
    await expect(page.locator(`[data-session-id="${first.id}"]`)).toBeVisible(); await expect(page.locator(`[data-session-id="${second.id}"]`)).toBeVisible()
    await page.screenshot({ path: info.outputPath('real-two-sessions.png') })
    await openSession(page, first.id); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toHaveValue('第一场独立草稿'); await page.getByRole('button', { name: '提交并进入追问 →', exact: true }).click(); await expect(page.locator('.q-type:visible')).toContainText('第 2 / 6 轮')
    await page.getByRole('button', { name: '返回面试列表', exact: true }).click(); await openSession(page, second.id); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toHaveValue('第二场独立草稿')
    await page.getByRole('button', { name: '提交并进入追问 →', exact: true }).click(); await expect(page.locator('.q-type:visible')).toContainText('第 2 / 8 轮')
    await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '个人中心', exact: true }).click()
    await page.getByRole('button', { name: '删除当前简历', exact: true }).click(); await confirm(page, '删除当前简历'); await expect(page.getByText('无当前简历', { exact: true })).toBeVisible()
    expect((await (await page.request.get(`/api/interviews/${first.id}`)).json() as InterviewSession).resumeSnapshot).toBe('# 保存正文 A')
    await page.getByRole('link', { name: '返回 AI 面试', exact: true }).click(); await createTab(page)
    await page.getByRole('radio', { name: /无简历通用面试/ }).check(); await page.getByRole('radio', { name: /Agent 开发/ }).check(); await page.getByLabel('主问题数量').selectOption('3')
    await page.getByRole('button', { name: '创建面试 · 6 轮', exact: true }).click(); await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toBeVisible({ timeout: 30_000 })
    const three = await (await page.request.get('/api/interviews/page?page=0&size=5')).json() as { items: InterviewSummary[] }; expect(three.items).toHaveLength(3)
    expect((await (await page.request.get(`/api/interviews/${second.id}`)).json() as InterviewSession).currentTurn).toBe(1)
    await page.getByRole('button', { name: /^账号菜单/ }).filter({ visible: true }).click(); await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
    await page.getByLabel('用户名').fill(username); await page.getByLabel('密码', { exact: true }).fill(password); await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.locator(`[data-session-id="${first.id}"]`)).toBeVisible(); await openSession(page, first.id); await expect(page.locator('.q-type:visible')).toContainText('第 2 / 6 轮')
    await page.getByRole('button', { name: '提前交卷', exact: true }).click()
    await expect(page.getByRole('dialog')).toContainText('已提交 1 轮，未提交 5 轮'); await expect(page.getByRole('dialog')).toContainText('按 0 分')
    await confirm(page, '确认交卷')
    await expect(page.locator('.score:visible')).toContainText('13.35', { timeout: 30_000 })
    await expect(page.locator('.session-heading .status:visible')).toHaveText('已完成', { timeout: 30_000 })
    await expect(page.locator('.interview-session-view:visible').getByLabel('你的回答')).toHaveCount(0)
    await expect(page.locator('.feedback:visible')).toHaveCount(6)
    await expect(page.locator('.report-copy .chip:visible')).toHaveText(['问题组 1 · 有效', '问题组 2 · 有效', '问题组 3 · 有效'])
    await expect(page.locator('.feedback:visible > strong').filter({ hasText: '未提交 / 未作答（0 分）' })).toHaveCount(5)
    await expect(page.getByText('尚无总分', { exact: true })).toHaveCount(0)
    const frozen = await (await page.request.get(`/api/interviews/${first.id}`)).json() as InterviewSession
    expect(frozen.answerStatus).toBe('COMPLETED'); expect(frozen.evaluationStatus).toBe('SUCCEEDED')
    const beforeReads = activitySignals
    const report = await (await page.request.get(`/api/interviews/${first.id}/report`)).json() as InterviewReport
    expect(report.totalScore).toBe(13.35); expect(report.turns.filter(turn => turn.status === 'UNANSWERED')).toHaveLength(5)
    expect(report.turns.find(turn => turn.turnIndex === 0)?.score).toBe(80.125)
    for (let index = 0; index < 2; index++) expect(await (await page.request.get(`/api/interviews/${first.id}/report`)).json()).toEqual(report)
    expect(activitySignals).toBe(beforeReads); expect(jdParses).toBe(1)
    await page.getByRole('region', { name: 'AI 面试内容' }).evaluate(element => { element.scrollTop = 0 })
    await page.screenshot({ path: info.outputPath('real-evaluated-report.png') })
    await page.getByRole('button', { name: '删除面试', exact: true }).click(); await confirm(page, '确认删除')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.locator(`[data-session-id="${first.id}"]`)).toHaveCount(0)
    expect((await page.request.get(`/api/interviews/${first.id}`)).status()).toBe(404); expect((await page.request.get(`/api/interviews/${second.id}`)).status()).toBe(200)
  })
})
