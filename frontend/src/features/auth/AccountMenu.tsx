import { useEffect, useRef, useState } from 'react'
import type { Account } from '../../api/auth'

export function AccountMenu({ account, onPassword, onLogout, onUsers, onProfile, onCommunity, onWorkbench }: {
  account: Account; onPassword: () => void; onLogout: () => void; onUsers: () => void; onProfile: () => void; onCommunity?: () => void; onWorkbench?: () => void
}) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const close = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [])
  const action = (callback: () => void) => { setOpen(false); callback() }
  return <div className="account-menu" ref={root}>
    <button type="button" className="account-trigger secondary" aria-expanded={open} aria-label={`账号菜单：${account.username}`} onClick={() => setOpen(value => !value)}>{account.username}<span aria-hidden="true">⌄</span></button>
    {open && <div className="account-popover" role="menu" aria-label="账号操作" onKeyDown={event => { if (event.key === 'Escape') { setOpen(false); root.current?.querySelector('button')?.focus() } }}>
      <span className="account-role">{account.role === 'ADMIN' ? '管理员' : '普通用户'}</span>
      <button type="button" role="menuitem" onClick={() => action(onProfile)}>个人中心</button>
      {onWorkbench && <button type="button" role="menuitem" onClick={() => action(onWorkbench)}>返回工作台</button>}
      {onCommunity && <button type="button" role="menuitem" onClick={() => action(onCommunity)}>进入广场</button>}
      {account.role === 'ADMIN' && <button type="button" role="menuitem" onClick={() => action(onUsers)}>用户管理</button>}
      <button type="button" role="menuitem" onClick={() => action(onPassword)}>修改密码</button>
      <button type="button" role="menuitem" data-no-activity onClick={() => action(onLogout)}>退出登录</button>
    </div>}
  </div>
}
