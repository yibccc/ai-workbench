import { expect, test, type BrowserContext, type Page } from '@playwright/test'
import type { FocusSession } from '../src/api/focus'

interface AlarmAudioProbe {
  contexts: Array<{ state: AudioContextState; changeState: (state: AudioContextState) => void }>
  resumes: number; starts: number; active: number; previews: number; stops: number[]
  holdResume: boolean; failResume: boolean; release: (() => void) | null
}

declare global {
  interface Window { __alarmAudio: AlarmAudioProbe }
}

async function audioProbe(context: BrowserContext) {
  await context.addInitScript(() => {
    const probe: AlarmAudioProbe = { contexts: [], resumes: 0, starts: 0, active: 0, previews: 0, stops: [], holdResume: false, failResume: false, release: null }
    window.__alarmAudio = probe
    class TestAudioContext {
      state: AudioContextState = 'running'
      sampleRate = 48000; destination = {}
      listeners: Array<() => void> = []
      get currentTime() { return performance.now() / 1000 }
      constructor() { probe.contexts.push(this) }
      addEventListener(_name: string, listener: () => void) { this.listeners.push(listener) }
      removeEventListener(_name: string, listener: () => void) { this.listeners = this.listeners.filter(current => current !== listener) }
      changeState(state: AudioContextState) { this.state = state; this.listeners.forEach(listener => listener()) }
      async resume() {
        probe.resumes++
        if (probe.holdResume) await new Promise<void>(resolve => { probe.release = resolve })
        if (probe.failResume) throw new Error('system interrupted audio')
        if (this.state === 'closed') throw new Error('closed')
        this.changeState('running')
      }
      async close() { this.changeState('closed') }
      createGain() { return { gain: { value: 0 }, connect() { return this }, disconnect() {} } }
      createOscillator() { return { frequency: { value: 0 }, onended: null, connect() { return this }, disconnect() {}, start() { probe.previews++ }, stop() {} } }
      createBuffer(_channels: number, length: number) { return { getChannelData: () => new Float32Array(length) } }
      createBufferSource() {
        let active = false; let timer: number | null = null
        const currentTime = () => this.currentTime
        return {
          loop: false, buffer: null, onended: null as (() => void) | null,
          connect() { return this }, disconnect() {},
          start() { active = true; probe.active++; probe.starts++ },
          stop(when?: number) {
            if (timer !== null) window.clearTimeout(timer)
            const stop = () => { if (active) { active = false; probe.active--; this.onended?.() } }
            if (when === undefined) stop()
            else { probe.stops.push(when); timer = window.setTimeout(stop, Math.max(0, when - currentTime()) * 1000) }
          },
        }
      }
    }
    Object.defineProperty(window, 'AudioContext', { configurable: true, value: TestAudioContext })
  })
}

async function openFocus(page: Page) {
  await page.goto('/#focus')
  await expect(page.getByTestId('workbench').or(page.getByLabel('用户名'))).toBeVisible()
  if (await page.getByLabel('用户名').isVisible()) {
    await page.getByLabel('用户名').fill('e2e_admin')
    await page.getByLabel('密码', { exact: true }).fill('E2eOnly-Synthetic-9384!')
    await page.getByRole('button', { name: '登录', exact: true }).click()
  }
  await expect(page.getByTestId('focus-page')).toBeVisible()
}

function runningSession(): FocusSession {
  const now = new Date().toISOString()
  return {
    id: 'd9600000-0000-4000-8000-000000000001', requestId: 'd9600000-0000-4000-8000-000000000002', title: '后台提醒回归', taskId: null, projectId: null,
    targetMs: 600_000, intervalMs: 600_000, zoneId: 'Asia/Shanghai', phase: 'RUNNING', version: 1, startedAt: now, anchorAt: now, endedAt: null,
    focusMs: 0, breakMs: 0, pauseMs: 0, resumePhase: null, breakRemainingMs: 0, nextBreakAtMs: 600_000, remindersDismissed: false, reminderOrdinal: 0,
    controllerId: null, controllerGeneration: 0, controllerExpiresAt: null, progress: '',
  }
}

async function focusServer(context: BrowserContext) {
  const server = {
    session: runningSession(), settle: false, conflict: false,
    claims: [] as Array<{ controllerId: string | null; controllerGeneration: number | null }>,
    beforeCheckpoint: null as (() => Promise<void>) | null,
    leaseMs: 120_000,
    clockOffset: 0,
  }
  await context.route('**/api/focus/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/focus/current') return route.fulfill({ json: server.session.phase === 'ENDED' ? null : server.session })
    if (path.endsWith('/checkpoint')) {
      const claim = route.request().postDataJSON() as { controllerId: string | null; controllerGeneration: number | null }
      server.claims.push(claim)
      await server.beforeCheckpoint?.()
      if (server.conflict) { server.conflict = false; return route.fulfill({ status: 409, json: { message: 'stale version' } }) }
      if (claim.controllerId) {
        const expired = !server.session.controllerExpiresAt || Date.parse(server.session.controllerExpiresAt) <= Date.now()
        if (expired || server.session.controllerId === claim.controllerId && server.session.controllerGeneration === claim.controllerGeneration) {
          server.session = { ...server.session, controllerId: claim.controllerId, controllerGeneration: server.session.controllerGeneration + (expired ? 1 : 0), controllerExpiresAt: new Date(Date.now() + server.clockOffset + server.leaseMs).toISOString() }
        }
      }
      server.session = { ...server.session, version: server.session.version + 1, anchorAt: new Date().toISOString() }
      if (server.settle) server.session = { ...server.session, phase: 'ENDED', focusMs: server.session.targetMs, endedAt: new Date().toISOString() }
      return route.fulfill({ json: server.session })
    }
    if (path === `/api/focus/sessions/${server.session.id}`) return route.fulfill({ json: server.session })
    return route.continue()
  })
  return server
}

async function sync(page: Page) { await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange'))) }

test('后台提醒超过五分钟仍携带启声标签和代次续租，查看页面不能抢占', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await page.clock.install()
  await openFocus(page)
  await expect.poll(() => server.claims.length).toBeGreaterThan(0)
  expect(server.claims.every(claim => claim.controllerId === null)).toBe(true)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await expect.poll(() => server.session.controllerId).not.toBeNull()
  const owner = server.session.controllerId
  const generation = server.session.controllerGeneration
  // This covers hidden-branch behavior and elapsed deadlines, not native browser throttling.
  await page.evaluate(() => Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }))
  await sync(page)
  await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeEnabled()
  const beforeHiddenClaims = server.claims.length
  for (let elapsed = 20_000; elapsed <= 320_000; elapsed += 20_000) {
    server.clockOffset = elapsed
    const previousClaims = server.claims.length
    await page.clock.runFor(20_000)
    await expect.poll(() => server.claims.length).toBeGreaterThan(previousClaims)
    await expect(page.getByRole('button', { name: '暂停', exact: true })).toBeEnabled()
  }
  expect(server.claims.length - beforeHiddenClaims).toBeGreaterThanOrEqual(16)
  expect(server.session.controllerId).toBe(owner)
  expect(server.claims.filter(claim => claim.controllerId).every(claim => claim.controllerId === owner && claim.controllerGeneration === generation || claim.controllerGeneration === 0)).toBe(true)
  const viewer = await context.newPage()
  await openFocus(viewer)
  await expect.poll(() => server.claims.at(-1)?.controllerId).toBeNull()
  expect(server.session.controllerId).toBe(owner)
  server.settle = true
  await sync(page)
  await expect(page.getByTestId('focus-completion-alarm')).toBeVisible()
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(await viewer.evaluate(() => window.__alarmAudio.starts)).toBe(0)
  await sync(viewer)
  await expect(viewer.getByTestId('focus-completion-alarm')).toContainText('启用并试听声音')
  await viewer.getByTestId('focus-completion-alarm').getByRole('button', { name: '启用并试听声音' }).click()
  await expect(viewer.getByTestId('focus-completion-alarm')).toContainText('提醒由其他页面负责')
  expect(await viewer.evaluate(() => window.__alarmAudio.starts)).toBe(0)
  await page.getByTestId('focus-completion-alarm').getByRole('button', { name: '结束', exact: true }).click()
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
})

test('后台提醒完成检测不受可见性限制，终态续租且刷新不重放历史提醒', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  server.session = { ...server.session, targetMs: 1200 }
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await page.evaluate(() => Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }))
  server.settle = true
  await expect(page.getByTestId('focus-completion-alarm')).toBeVisible()
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  const oldExpiry = server.session.controllerExpiresAt
  await sync(page)
  await expect.poll(() => server.session.controllerExpiresAt).not.toBe(oldExpiry)
  await page.reload()
  await expect(page.getByTestId('focus-completion-alarm')).toHaveCount(0)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(0)
})

for (const state of ['suspended', 'interrupted'] as const) {
  test(`恢复音频复用曾启声的 ${state} 上下文，恢复后仅启动一个循环`, async ({ page, context }) => {
    await audioProbe(context)
    const server = await focusServer(context)
    await openFocus(page)
    await page.getByRole('button', { name: '启用并试听声音' }).click()
    const resumesBeforePause = await page.evaluate(() => window.__alarmAudio.resumes)
    await page.evaluate(state => window.__alarmAudio.contexts[0].changeState(state), state)
    await expect(page.getByRole('button', { name: '恢复声音' })).toBeVisible()
    await expect(page.getByText('声音已启用')).toHaveCount(0)
    server.settle = true
    await sync(page)
    await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
    expect(await page.evaluate(() => window.__alarmAudio.contexts.length)).toBe(1)
    expect(await page.evaluate(() => window.__alarmAudio.resumes)).toBeGreaterThan(resumesBeforePause)
    await page.getByTestId('focus-completion-alarm').getByRole('button', { name: '结束', exact: true }).click()
    expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
  })
}

test('恢复音频失败分类准确，关闭上下文由用户动作替换并重新核验终态控制权', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await page.evaluate(() => { window.__alarmAudio.failResume = true; window.__alarmAudio.contexts[0].changeState('interrupted') })
  server.settle = true
  await sync(page)
  const alarm = page.getByTestId('focus-completion-alarm')
  await expect(alarm).toContainText('声音被浏览器或系统暂停')
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(0)
  await page.evaluate(() => { window.__alarmAudio.failResume = false; window.__alarmAudio.contexts[0].changeState('closed') })
  await expect(alarm).toContainText('声音上下文已关闭')
  server.session = { ...server.session, controllerId: 'd9600000-0000-4000-8000-000000000099', controllerExpiresAt: new Date(Date.now() + 120_000).toISOString(), controllerGeneration: 9, version: server.session.version + 1 }
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect(alarm).toContainText('提醒由其他页面负责')
  expect(await page.evaluate(() => window.__alarmAudio.contexts.length)).toBe(2)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(0)
  server.session = { ...server.session, controllerExpiresAt: new Date(Date.now() - 1).toISOString() }
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(server.session.controllerGeneration).toBe(10)
})

test('恢复音频等待中点击结束，迟到resume不能重启提醒', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await page.evaluate(() => { window.__alarmAudio.holdResume = true; window.__alarmAudio.contexts[0].changeState('suspended') })
  server.settle = true
  await sync(page)
  const alarm = page.getByTestId('focus-completion-alarm')
  await expect(alarm).toBeVisible()
  await expect.poll(() => page.evaluate(() => !!window.__alarmAudio.release)).toBe(true)
  await alarm.getByRole('button', { name: '结束', exact: true }).click()
  await page.evaluate(() => { window.__alarmAudio.holdResume = false; window.__alarmAudio.release?.() })
  await page.waitForTimeout(200)
  await expect(alarm).toHaveCount(0)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(0)
  await sync(page)
  await expect(alarm).toHaveCount(0)
})

test('提醒控制权校准等待中点击结束，迟到checkpoint不能重启且租期调度停止循环', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  server.settle = true
  await sync(page)
  const alarm = page.getByTestId('focus-completion-alarm')
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(await page.evaluate(() => window.__alarmAudio.stops.at(-1)! > performance.now() / 1000 + 100)).toBe(true)
  await page.evaluate(() => window.__alarmAudio.contexts[0].changeState('suspended'))
  let release = () => {}
  let waiting = false
  server.beforeCheckpoint = () => new Promise<void>(resolve => { waiting = true; release = resolve })
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect.poll(() => waiting).toBe(true)
  const starts = await page.evaluate(() => window.__alarmAudio.starts)
  await alarm.getByRole('button', { name: '结束', exact: true }).click()
  server.beforeCheckpoint = null; release()
  await page.waitForTimeout(200)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(starts)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
  await expect(alarm).toHaveCount(0)
})

test('提醒控制权409终态校准和过期接管，账户卸载停止循环', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  server.session = { ...server.session, phase: 'ENDED', focusMs: server.session.targetMs, endedAt: new Date().toISOString(), controllerId: 'd9600000-0000-4000-8000-000000000099', controllerGeneration: 8, controllerExpiresAt: new Date(Date.now() - 1).toISOString(), version: 10 }
  server.conflict = true
  await sync(page)
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(server.session.controllerGeneration).toBe(9)
  await page.getByRole('button', { name: /^账号菜单/ }).click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page.getByTestId('workbench')).toHaveCount(0)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
})

test('提醒控制权到期停止循环，用户恢复只启动仍待处理的提醒', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  server.leaseMs = 1500; server.settle = true
  await sync(page)
  const alarm = page.getByTestId('focus-completion-alarm')
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(0)
  await expect(alarm).toContainText('提醒控制权已失效')
  server.leaseMs = 120_000
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  await alarm.getByRole('button', { name: '结束', exact: true }).click()
  await sync(page)
  await expect(alarm).toHaveCount(0)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
})

test('后台提醒同代次20秒续租只延长音频停止时间，不重复创建循环源', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await page.clock.install()
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  server.settle = true
  await sync(page)
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  const before = await page.evaluate(() => ({ starts: window.__alarmAudio.starts, stop: window.__alarmAudio.stops.at(-1)! }))
  const generation = server.session.controllerGeneration
  server.clockOffset = 25_000
  await page.clock.runFor(20_000)
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.stops.at(-1)!)).toBeGreaterThan(before.stop + 20)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(before.starts)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(server.session.controllerGeneration).toBe(generation)
})

test('恢复音频永远pending会超时，用户仍可恢复且迟到旧promise不重播', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  await page.evaluate(() => { window.__alarmAudio.holdResume = true; window.__alarmAudio.contexts[0].changeState('suspended') })
  server.settle = true
  await sync(page)
  const alarm = page.getByTestId('focus-completion-alarm')
  await page.waitForTimeout(5500)
  await expect(alarm).toContainText('暂停', { timeout: 9000 })
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(0)
  await page.evaluate(() => { window.__alarmAudio.holdResume = false })
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(await page.evaluate(() => window.__alarmAudio.contexts.length)).toBe(1)
  await alarm.getByRole('button', { name: '结束', exact: true }).click()
  const starts = await page.evaluate(() => window.__alarmAudio.starts)
  await page.evaluate(() => window.__alarmAudio.release?.())
  await page.waitForTimeout(200)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(starts)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
  await expect(alarm).toHaveCount(0)
})

test('恢复音频系统statechange恢复待处理提醒，已点击结束则不重启', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  server.settle = true
  await sync(page)
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  await page.evaluate(() => window.__alarmAudio.contexts[0].changeState('suspended'))
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(0)
  await page.evaluate(() => window.__alarmAudio.contexts[0].changeState('running'))
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  await page.getByTestId('focus-completion-alarm').getByRole('button', { name: '结束', exact: true }).click()
  const starts = await page.evaluate(() => window.__alarmAudio.starts)
  await page.evaluate(() => { window.__alarmAudio.contexts[0].changeState('suspended'); window.__alarmAudio.contexts[0].changeState('running') })
  await page.waitForTimeout(200)
  expect(await page.evaluate(() => window.__alarmAudio.starts)).toBe(starts)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
})

test('恢复音频等待期间上下文关闭，反馈保留关闭原因且用户可替换上下文', async ({ page, context }) => {
  await audioProbe(context)
  const server = await focusServer(context)
  await openFocus(page)
  await page.getByRole('button', { name: '启用并试听声音' }).click()
  server.settle = true
  await sync(page)
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  await page.evaluate(() => { window.__alarmAudio.holdResume = true; window.__alarmAudio.contexts[0].changeState('suspended') })
  const alarm = page.getByTestId('focus-completion-alarm')
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect.poll(() => page.evaluate(() => !!window.__alarmAudio.release)).toBe(true)
  await page.evaluate(() => window.__alarmAudio.contexts[0].changeState('closed'))
  await page.waitForTimeout(200)
  await expect(alarm).toContainText('声音上下文已关闭')
  await expect(alarm).not.toContainText('声音被浏览器或系统暂停')
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
  await page.evaluate(() => { window.__alarmAudio.holdResume = false })
  await alarm.getByRole('button', { name: '恢复声音' }).click()
  await expect.poll(() => page.evaluate(() => window.__alarmAudio.active)).toBe(1)
  expect(await page.evaluate(() => window.__alarmAudio.contexts.length)).toBe(2)
  await alarm.getByRole('button', { name: '结束', exact: true }).click()
  await page.evaluate(() => window.__alarmAudio.release?.())
  await page.waitForTimeout(200)
  expect(await page.evaluate(() => window.__alarmAudio.active)).toBe(0)
})
