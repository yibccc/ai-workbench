/** Read-only browser smoke for an already running localhost installation. */
import { chromium, expect } from '@playwright/test'

const username = process.env.WORKBENCH_SMOKE_USERNAME
const password = process.env.WORKBENCH_SMOKE_PASSWORD
const baseURL = process.env.WORKBENCH_SMOKE_URL ?? 'http://127.0.0.1:5173'
if (!username || !password || !new URL(baseURL).hostname.match(/^(127\.0\.0\.1|localhost)$/)) {
  throw new Error('Local browser smoke requires credentials and a loopback URL')
}

const browser = await chromium.launch()
try {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto(baseURL)
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByTestId('workbench')).toBeVisible()
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible()
  const result = await page.evaluate(async () => {
    const [me, projects, accounts] = await Promise.all([
      fetch('/api/auth/me'), fetch('/api/projects'), fetch('/api/admin/users'),
    ])
    return { me: me.ok ? await me.json() : null, projects: await projects.json(),
      accounts: accounts.ok ? await accounts.json() : null }
  })
  expect(result.me?.username).toBe(username)
  expect(result.me?.role).toBe('ADMIN')
  expect(result.projects).toEqual([])
  expect(result.accounts).toHaveLength(1)
  const session = (await context.cookies()).find(cookie => cookie.name === 'WORKBENCH_SESSION')
  expect(session?.httpOnly).toBe(true)
  const storage = await page.evaluate(() => JSON.stringify({
    local: Object.fromEntries(Array.from({ length: localStorage.length }, (_, i) => {
      const key = localStorage.key(i)
      return [key, key === null ? null : localStorage.getItem(key)]
    })),
    session: Object.fromEntries(Array.from({ length: sessionStorage.length }, (_, i) => {
      const key = sessionStorage.key(i)
      return [key, key === null ? null : sessionStorage.getItem(key)]
    })),
  }))
  expect(storage).not.toContain(password)
  expect(storage).not.toContain(session.value)
  await context.close()
  process.stdout.write('Local browser login, private API and credential storage passed\n')
} finally {
  await browser.close()
}
