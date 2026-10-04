import { defineConfig } from '@playwright/test'
import base from './playwright.config'

/** UI-only HTTP fixtures retain the normal isolation guard and frontend environment filter. */
export default defineConfig({
  ...base,
  testMatch: 'interview.spec.ts',
  grep: /受控 HTTP fixture/,
  retries: 0,
  webServer: [{
    command: 'node e2e/start-vite.mjs', url: 'http://127.0.0.1:15173', timeout: 60_000,
    reuseExistingServer: false, env: { VITE_API_TARGET: 'http://127.0.0.1:18080' },
  }],
})
