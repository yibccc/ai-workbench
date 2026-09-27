const path = require('node:path')
const { chromium } = require(path.join(__dirname, '../../../../frontend/node_modules/@playwright/test'))

async function main() {
  const password = process.env.MANUAL_FOCUS_PASSWORD
  if (!password) throw new Error('Set MANUAL_FOCUS_PASSWORD for the isolated manual instance.')
  for (const channel of ['chrome', 'msedge']) {
    const browser = await chromium.launch({ channel, headless: true })
    try {
      const page = await browser.newPage()
      await page.goto('http://127.0.0.1:15174')
      await page.getByLabel('用户名').fill('focus_manual_admin')
      await page.getByLabel('密码', { exact: true }).fill(password)
      await page.getByRole('button', { name: '登录', exact: true }).click()
      const navigation = page.getByRole('navigation', { name: '主导航' })
      await navigation.getByRole('link', { name: '专注' }).waitFor()
      const count = await navigation.getByRole('link').count()
      await navigation.getByRole('link', { name: '专注' }).click()
      await page.getByTestId('focus-page').waitFor()
      console.log(JSON.stringify({ channel, version: browser.version(), navCount: count, focusPage: true }))
    } finally {
      await browser.close()
    }
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
