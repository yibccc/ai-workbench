import { defineConfig, devices } from '@playwright/test'

const databaseUrl = process.env.E2E_DATABASE_URL
const redisPort = process.env.REDIS_PORT
const database = databaseUrl ? new URL(databaseUrl.replace(/^jdbc:/, '')) : null
if (!database || !['127.0.0.1', 'localhost'].includes(database.hostname)
    || !database.port || database.port === '5432'
    || database.pathname === '/ai_workbench' || database.searchParams.get('currentSchema') !== 'd9_e2e'
    || !redisPort || redisPort === '6379' || !process.env.POSTGRES_USER || !process.env.POSTGRES_PASSWORD) {
  throw new Error('E2E requires explicit isolated PostgreSQL/Redis resources; never use existing ai-workbench volumes.')
}

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
      env: {
        E2E_BACKEND_PORT: '18080',
        E2E_DATABASE_URL: databaseUrl,
        POSTGRES_USER: process.env.POSTGRES_USER,
        POSTGRES_PASSWORD: process.env.POSTGRES_PASSWORD,
        REDIS_HOST: '127.0.0.1',
        REDIS_PORT: redisPort,
        WORKBENCH_BOOTSTRAP_USERNAME: 'e2e_admin',
        WORKBENCH_BOOTSTRAP_PASSWORD: 'E2eOnly-Synthetic-9384!',
      },
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
