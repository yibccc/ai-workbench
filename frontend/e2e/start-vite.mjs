import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

// Vite receives the frontend environment only; backend and object-store secrets stay in its sibling process.
const allowed = /^(?:PATH|PATHEXT|SYSTEMROOT|WINDIR|COMSPEC|TEMP|TMP|USERPROFILE|LOCALAPPDATA|APPDATA|CI|NO_COLOR|FORCE_COLOR)$/i
const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => allowed.test(key)))
env.VITE_API_TARGET = 'http://127.0.0.1:18080'
const entry = fileURLToPath(new URL('../node_modules/vite/bin/vite.js', import.meta.url))
const child = spawn(process.execPath, [entry, '--host', '127.0.0.1', '--port', '15173'], { env, stdio: 'inherit', windowsHide: true })
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal))
child.on('exit', code => { process.exitCode = code ?? 1 })
