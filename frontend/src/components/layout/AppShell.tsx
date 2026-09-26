import type { ReactNode } from 'react'
import { Icon } from '../Icon'
import { navigation, type PageId } from './navigation'
import { WORKBENCH_TIME_ZONE } from '../../utils/date'
import type { Account } from '../../api/auth'
import { AccountMenu } from '../../features/auth/AccountMenu'

export function AppShell({ page, hasDirtyReports, focusStatus, focusToggle, account, onPassword, onLogout, onUsers, children }: {
  page: PageId | 'users'; hasDirtyReports: boolean; focusStatus?: string | null; focusToggle?: { label: string; disabled: boolean; onClick: () => void } | null; account: Account; onPassword: () => void; onLogout: () => void; onUsers: () => void; children: ReactNode
}) {
  const current = navigation.find(item => item.id === page) ?? { label: '用户管理' }
  const date = new Intl.DateTimeFormat('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
  const weekday = new Intl.DateTimeFormat('en-US', { timeZone: WORKBENCH_TIME_ZONE, weekday: 'short' }).format(new Date())
  return <div className="app-shell" data-testid="workbench">
    <a className="skip-link" href="#main-content" onClick={event => {
      event.preventDefault()
      document.getElementById('main-content')?.focus()
    }}>跳转到主要内容</a>
    <aside className="sidebar" aria-label="工作区导航">
      <a className="brand" href="#records" aria-label="工作台首页"><span className="brand-mark"><Icon name="notebook" size={22} /></span><span><strong>工作台<span className="brand-dot">.</span></strong><small>LOCAL WORKBENCH</small></span></a>
      <div className="workspace-label"><span className="workspace-avatar">W</span><div><strong>个人工作区</strong><span>记录 · 执行 · 汇报</span></div></div>
      <p className="nav-caption">我的工作</p>
      <nav className="primary-nav" aria-label="主导航">{navigation.map(item => <a key={item.id} href={`#${item.id}`} aria-current={page === item.id ? 'page' : undefined}><Icon name={item.icon} /><span>{item.label}</span>{item.id === 'reports' && hasDirtyReports && <span className="dirty-dot" aria-label="有未保存修改" />}</a>)}</nav>
      <div className="sidebar-guide"><span className="guide-icon"><Icon name="sparkles" /></span><strong>少一点整理，多一点专注</strong><p>记下进展与计划，AI 帮你拆分，再整理成有来源的汇报。</p><a href="#reports">去整理汇报 <Icon name="arrow-right" size={14} /></a></div>
      <div className="sidebar-footer"><AccountMenu account={account} onPassword={onPassword} onLogout={onLogout} onUsers={onUsers} /></div>
    </aside>
    <div className="app-body">
      <header className="topbar"><div className="breadcrumb"><span>个人工作区</span><span aria-hidden="true">/</span><strong>{current.label}</strong></div><div className="topbar-actions">{focusStatus && page !== 'focus' && <div className="focus-compact-controls"><a className="focus-compact" href="#focus" aria-label={`返回专注：${focusStatus}`}><Icon name="clock" size={15} /><span>{focusStatus}</span></a>{focusToggle && <button type="button" className="focus-compact-toggle" disabled={focusToggle.disabled} onClick={focusToggle.onClick}>{focusToggle.label}</button>}</div>}<time className={`topbar-date ${weekday === 'Sun' ? 'weekday-sunday' : weekday === 'Sat' ? 'weekday-saturday' : ''}`}><Icon name="calendar" size={15} />{date}</time><div className="mobile-account"><AccountMenu account={account} onPassword={onPassword} onLogout={onLogout} onUsers={onUsers} /></div></div></header>
      <main id="main-content" className="main-content" tabIndex={-1}>{children}</main>
    </div>
  </div>
}
