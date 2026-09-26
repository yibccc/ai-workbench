/** Isolated d10deployvalidation acceptance check, invoked by verify-compose.py. */
import { execFileSync } from 'node:child_process'
import { chromium, expect } from '@playwright/test'

const username = process.env.WORKBENCH_BOOTSTRAP_USERNAME
const password = process.env.WORKBENCH_BOOTSTRAP_PASSWORD
const inputId = process.env.D10_INPUT_ID
if (!username || !password || !/^[0-9a-f-]{36}$/i.test(inputId ?? '')) {
  throw new Error('Isolated browser fixture environment is incomplete')
}

const docker = (...args) => execFileSync('wsl.exe', ['-d', 'Ubuntu', '--', 'docker', ...args], {
  encoding: 'utf8', timeout: 120_000,
})
const browser = await chromium.launch()
try {
  const context = await browser.newContext()
  const page = await context.newPage()
  let connected = 0
  let subscribed = 0
  let subscribedId = null
  let routedSocket = null
  let inputGets = 0
  page.on('request', request => {
    if (new URL(request.url()).pathname === `/api/inputs/${inputId}`) inputGets++
  })
  await page.routeWebSocket('**/ws/events', socket => {
    routedSocket = socket
    const server = socket.connectToServer()
    socket.onMessage(message => {
      const frame = String(message)
      if (frame.startsWith('SUBSCRIBE')) {
        subscribed++
        subscribedId = /^id:([^\r\n]+)/m.exec(frame)?.[1] ?? null
      }
      server.send(message)
    })
    server.onMessage(message => {
      if (String(message).startsWith('CONNECTED')) connected++
      socket.send(message)
    })
  })
  await page.goto('http://127.0.0.1:18088/')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  const account = await page.evaluate(async () => (await fetch('/api/auth/me')).json())
  const storageKey = `ai-workbench.pending.v2.${account.id}`
  await page.evaluate(({ key, id }) => localStorage.setItem(key, JSON.stringify([`INPUT:${id}`])),
    { key: storageKey, id: inputId })
  await page.reload()
  await expect(page.getByText('原文已保存，AI 正在处理。关闭页面也不会丢失原文。')).toBeVisible()
  await expect.poll(() => connected, { timeout: 15_000 }).toBeGreaterThanOrEqual(1)
  await expect.poll(() => subscribed, { timeout: 15_000 }).toBeGreaterThanOrEqual(1)
  expect(subscribedId).not.toBeNull()

  const frame = (eventId, revision) => `MESSAGE\nsubscription:${subscribedId}\nmessage-id:${eventId}\n` +
    `destination:/user/queue/workbench-events\ncontent-type:application/json\n\n` +
    JSON.stringify({ eventId, kind: 'INPUT', entityId: inputId, state: 'PROCESSING', revision,
      occurredAt: new Date().toISOString() }) + '\0'
  const eventId = crypto.randomUUID()
  const firstResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/inputs/${inputId}` && response.status() === 200)
  routedSocket.send(frame(eventId, 2))
  await firstResponse
  const afterNewEvent = inputGets
  routedSocket.send(frame(eventId, 2))
  routedSocket.send(frame(crypto.randomUUID(), 1))
  await page.waitForTimeout(500)
  expect(inputGets).toBe(afterNewEvent)

  const beforeRestartConnected = connected
  const beforeRestartSubscribed = subscribed
  docker('restart', 'd10deployvalidation-backend-1')
  await expect.poll(() => connected, { timeout: 60_000 }).toBeGreaterThan(beforeRestartConnected)
  await expect.poll(() => subscribed, { timeout: 15_000 }).toBeGreaterThan(beforeRestartSubscribed)
  await expect.poll(async () => page.evaluate(async () => (await fetch('/api/auth/me')).status),
    { timeout: 20_000 }).toBe(200)
  const afterRestartGets = inputGets
  const oldRevisionResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname === `/api/inputs/${inputId}` && response.status() === 200)
  routedSocket.send(frame(crypto.randomUUID(), 1))
  await oldRevisionResponse
  expect(inputGets).toBeGreaterThan(afterRestartGets)
  await expect(page.getByText('原文已保存，AI 正在处理。关闭页面也不会丢失原文。')).toBeVisible()
  docker('exec', 'd10deployvalidation-postgres-1', 'psql', '-U', 'deploy_validation', '-d',
    'deploy_validation', '-c', `UPDATE capture_inputs SET status='SUCCEEDED', completed_at=now(),
    updated_at=now(), lease_expires_at=NULL WHERE id='${inputId}' AND status='PROCESSING';`)
  await expect(page.getByText('已生成 0 条工作记录和 0 项待办。')).toBeVisible({ timeout: 25_000 })
  await expect.poll(() => page.evaluate(key => localStorage.getItem(key), storageKey)).toBe('[]')

  const cookies = await context.cookies()
  const session = cookies.find(cookie => cookie.name === 'WORKBENCH_SESSION')
  expect(session?.httpOnly).toBe(true)
  const browserStorage = await page.evaluate(() => JSON.stringify({
    local: { ...localStorage }, session: { ...sessionStorage },
  }))
  expect(browserStorage).not.toContain(password)
  expect(browserStorage).not.toContain(session.value)
  await context.close()
} finally {
  await browser.close()
}
