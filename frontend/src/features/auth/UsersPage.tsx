import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { changeAccountEnabled, changeAccountRole, createAccount, listAccounts, resetAccountPassword, type Account } from '../../api/auth'
import { Dialog } from '../../components/Dialog'
import { useDialog } from '../../components/dialogContext'
import { useGlobalToast } from '../../components/toastContext'

type FormMode = { kind: 'create' } | { kind: 'reset'; account: Account }

function AccountForm({ mode, onClose, onSaved }: { mode: FormMode; onClose: () => void; onSaved: () => void }) {
  const [username, setUsername] = useState('')
  const [role, setRole] = useState<Account['role']>('USER')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pending = useRef(false)
  const creating = mode.kind === 'create'
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (pending.current) return
    if (password !== confirm) { setError('两次输入的密码不一致'); return }
    pending.current = true; setBusy(true); setError(null)
    try {
      if (mode.kind === 'create') await createAccount(username, password, role)
      else await resetAccountPassword(mode.account.id, password)
      onSaved(); onClose()
    } catch (caught) { setError(caught instanceof Error ? caught.message : '保存失败，请重试') }
    finally { pending.current = false; setBusy(false) }
  }
  return <Dialog titleId="account-form-title" descriptionId="account-form-description" busy={busy} onClose={onClose}>
    <form onSubmit={event => void submit(event)} aria-busy={busy}>
      <h2 id="account-form-title">{creating ? '创建用户' : `重置 ${mode.account.username} 的密码`}</h2>
      <p id="account-form-description" className="dialog-description">{creating ? '新用户只能通过受邀账号登录，默认角色为普通用户。' : '重置后该用户所有现有会话会失效，业务数据仍保留。'} 密码为 8～64 个字符。</p>
      {creating && <><label>用户名<input data-dialog-autofocus autoComplete="off" required value={username} onChange={event => setUsername(event.target.value)} disabled={busy} /></label>
        <label>角色<select value={role} onChange={event => setRole(event.target.value as Account['role'])} disabled={busy}><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label></>}
      <label>密码<input data-dialog-autofocus={!creating ? true : undefined} type="password" autoComplete="new-password" required value={password} onChange={event => setPassword(event.target.value)} disabled={busy} /></label>
      <label>确认密码<input type="password" autoComplete="new-password" required value={confirm} onChange={event => setConfirm(event.target.value)} disabled={busy} /></label>
      {error && <p className="inline-notice inline-error" role="alert">{error}</p>}
      <div className="actions completion-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>取消</button><button type="submit" disabled={busy}>{busy ? '正在保存…' : creating ? '创建用户' : '重置密码'}</button></div>
    </form>
  </Dialog>
}

export function UsersPage({ currentId, onSelfRevoked }: { currentId: string; onSelfRevoked: () => void }) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [form, setForm] = useState<FormMode | null>(null)
  const confirm = useDialog()
  const notify = useGlobalToast()
  const refresh = useCallback(async () => {
    setLoading(true)
    try { setAccounts(await listAccounts()); setError(null) }
    catch (caught) { setError(caught instanceof Error ? caught.message : '账号列表加载失败') }
    finally { setLoading(false) }
  }, [])
  useEffect(() => {
    let active = true
    void listAccounts().then(items => { if (active) { setAccounts(items); setError(null) } })
      .catch((caught: unknown) => { if (active) setError(caught instanceof Error ? caught.message : '账号列表加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  const saved = (message: string) => { notify(message, 'success'); void refresh() }
  const toggle = async (account: Account) => {
    await confirm({ title: account.enabled ? '停用用户？' : '启用用户？', description: account.enabled
      ? `停用 ${account.username} 后，其所有会话失效，业务数据保留。` : `启用 ${account.username} 后，可重新登录。`,
      confirmLabel: account.enabled ? '确认停用' : '确认启用', danger: account.enabled,
      onConfirm: async () => {
        await changeAccountEnabled(account.id, !account.enabled)
        if (account.id === currentId) onSelfRevoked()
        else saved(account.enabled ? '用户已停用' : '用户已启用')
      },
    })
  }
  const changeRole = async (account: Account) => {
    const next = account.role === 'ADMIN' ? 'USER' : 'ADMIN'
    await confirm({ title: '修改用户角色？', description: `${account.username} 将变为${next === 'ADMIN' ? '管理员' : '普通用户'}，现有会话会失效，业务数据保留。`,
      confirmLabel: '确认修改', danger: next === 'USER',
      onConfirm: async () => {
        await changeAccountRole(account.id, next)
        if (account.id === currentId) onSelfRevoked()
        else saved('角色已修改')
      },
    })
  }
  return <section className="page users-page" aria-labelledby="users-title">
    <div className="page-header"><div><h1 id="users-title">用户管理</h1><p className="page-description">创建受邀账号并管理访问权限。</p></div><button type="button" onClick={() => setForm({ kind: 'create' })}>创建用户</button></div>
    {error && <p className="inline-notice inline-error" role="alert">{error} <button className="text-button" type="button" onClick={() => void refresh()}>重新加载</button></p>}
    <div className="workspace-scroll users-scroll" role="region" aria-label="用户列表" tabIndex={0}>
      {loading && !accounts.length ? <p>正在加载…</p> : <div className="users-list">{accounts.map(account => <article className="user-row" key={account.id}>
        <div className="user-details"><strong>{account.username}</strong><span>{account.role === 'ADMIN' ? '管理员' : '普通用户'} · {account.enabled ? '启用' : '停用'} · 创建于 {new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeZone: 'Asia/Shanghai' }).format(new Date(account.createdAt))}{account.id === currentId ? ' · 当前账号' : ''}</span></div>
        <div className="user-actions"><button className="secondary" type="button" onClick={() => void changeRole(account)}>修改角色</button><button className="secondary" type="button" onClick={() => setForm({ kind: 'reset', account })}>重置密码</button><button className={account.enabled ? 'secondary danger' : 'secondary'} type="button" onClick={() => void toggle(account)}>{account.enabled ? '停用' : '启用'}</button></div>
      </article>)}</div>}
    </div>
    {form && <AccountForm mode={form} onClose={() => setForm(null)} onSaved={() => {
      if (form.kind === 'reset' && form.account.id === currentId) onSelfRevoked()
      else saved(form.kind === 'create' ? '用户已创建' : '密码已重置')
    }} />}
  </section>
}
