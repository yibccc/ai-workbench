import fs from 'node:fs'
import path from 'node:path'
import postcss from '../../../frontend/node_modules/postcss/lib/postcss.mjs'
const source = '.trellis/tasks/10-03-community-oss/research/handoff/r3/assets/prototype-r2/src/styles.css'
const css = postcss.parse(fs.readFileSync(source, 'utf8'))
css.walkRules(rule => {
  const selectors = rule.selectors.filter(selector => !/demo-|prototype-|diagram-|workspace-placeholder|login-identity/.test(selector) && !(selector.startsWith('.paper') && !selector.startsWith('.paper-thumb')))
  if (!selectors.length) { rule.remove(); return }
  rule.selector = selectors.flatMap(selector => {
    if ([':root', 'body', 'html'].includes(selector)) return ['.community-shell', '.community-dialog']
    return [`.community-shell ${selector}`, `.community-dialog ${selector}`]
  }).join(',')
})
const target = 'frontend/src/features/community/community.css'
fs.mkdirSync(path.dirname(target), { recursive: true })
fs.writeFileSync(target, `/* Directly extracted from immutable R2 src/styles.css; scoped resets and removed demo-only selectors. */\n${css.toString()}\n`, 'utf8')
