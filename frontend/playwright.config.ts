import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:15173',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    permissions: ['clipboard-read', 'clipboard-write'],
    screenshot: 'only-on-failure',
    trace: 'on',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium-desktop', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'mvn spring-boot:run -Dspring-boot.run.profiles=e2e',
      cwd: '../backend',
      url: 'http://127.0.0.1:18080/actuator/health',
      timeout: 120_000,
      reuseExistingServer: false,
      env: { E2E_BACKEND_PORT: '18080' },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173',
      url: 'http://127.0.0.1:15173',
      timeout: 60_000,
      reuseExistingServer: false,
      env: { VITE_API_TARGET: 'http://127.0.0.1:18080' },
    },
  ],
})
