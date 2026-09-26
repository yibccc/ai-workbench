import { useState, type FormEvent } from 'react'
import { login, type Account } from '../../api/auth'

export function LoginPage({ onLogin }: { onLogin: (account: Account) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [visible, setVisible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError(null)
    try { onLogin(await login(username, password)) }
    catch (caught) { setError(caught instanceof Error ? caught.message : '登录失败，请重试') }
    finally { setBusy(false) }
  }
  return <main className="login-page">
    <div className="login-card">
      <div className="login-brand"><span className="brand-mark" aria-hidden="true">W</span><strong>个人工作台</strong></div>
      <h1>登录工作台</h1><p>使用管理员创建的账号进入你的工作区。</p>
      <form onSubmit={event => void submit(event)} aria-busy={busy}>
        <label>用户名<input autoFocus autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} required disabled={busy} /></label>
        <div className="login-password"><label htmlFor="login-password">密码</label><span className="password-field"><input id="login-password" type={visible ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required disabled={busy} /><button type="button" className="text-button" aria-label={visible ? '隐藏密码' : '显示密码'} onClick={() => setVisible(value => !value)}>{visible ? '隐藏' : '显示'}</button></span></div>
        {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
        <button type="submit" disabled={busy}>{busy ? '正在登录…' : '登录'}</button>
      </form>
    </div>
  </main>
}
