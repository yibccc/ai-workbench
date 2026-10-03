import { pathToFileURL } from 'node:url'

// Verify the actual Node process boundary before launching the owned browser test.
const forbidden = Object.keys(process.env).filter(key =>
  /^(RUSTFS_|WORKBENCH_STORAGE_|AWS_|POSTGRES_|REDIS_|DEEPSEEK_|REPORT_AI_|OPENAI_|ANTHROPIC_|GEMINI_)/i.test(key)
  || /^(DATABASE_URL|TEST_DATABASE_URL|E2E_DATABASE_URL|LIVE_ACCEPTANCE_DATABASE_URL)$/i.test(key))
const required = ['WORKBENCH_BOOTSTRAP_USERNAME', 'WORKBENCH_BOOTSTRAP_PASSWORD', 'D10_INPUT_ID']
  .every(key => !!process.env[key])
console.log(JSON.stringify({ browserEnvironment: 'actual Node process', forbiddenCount: forbidden.length,
  syntheticLoginAndInputPresent: required }))
if (forbidden.length || !required || !process.argv[2]) {
  throw new Error('Browser process environment boundary failed')
}
await import(pathToFileURL(process.argv[2]).href)
