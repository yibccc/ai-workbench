import type { ReactNode } from 'react'
import { Icon } from '../Icon'
import { navigation } from './navigation'
import { WORKBENCH_TIME_ZONE } from '../../utils/date'
import type { Account } from '../../api/auth'
import { AccountMenu } from '../../features/auth/AccountMenu'
import { isCommunityView, routeTitle, type ViewId } from './routes'
import { CommunityBottomNav, CommunitySidebar } from '../../features/community/CommunityChrome'

export function AppShell({ page, hasDirtyReports, focusStatus, focusToggle, account, onPassword, onLogout, onUsers, children }: {
  page: ViewId; hasDirtyReports: boolean; focusStatus?: string | null; focusToggle?: { label: string; disabled: boolean; onClick: () => void } | null; account: Account | null; onPassword: () => void; onLogout: () => void; onUsers: () => void; children: ReactNode
}) {
  const community = isCommunityView(page)
  const current = { label: routeTitle(page) }
  const date = new Intl.DateTimeFormat('zh-CN', { timeZone: WORKBENCH_TIME_ZONE, month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
  const weekday = new Intl.DateTimeFormat('en-US', { timeZone: WORKBENCH_TIME_ZONE, weekday: 'short' }).format(new Date())
  const menu = account && <AccountMenu account={account} onPassword={onPassword} onLogout={onLogout} onUsers={onUsers} onProfile={() => { window.location.hash = 'profile' }} onCommunity={() => { window.location.hash = '/community' }} onWorkbench={() => { window.location.hash = 'records' }} />
  return <div className={`app-shell ${community ? 'community-shell' : ''}`} data-testid="workbench">
    <a className="skip-link" href="#main-content" onClick={event => {
      event.preventDefault()
      document.getElementById('main-content')?.focus()
    }}>跳转到主要内容</a>
    <aside className="sidebar" aria-label="工作区导航">
      {community ? <CommunitySidebar page={page} account={account} menu={menu} /> : <>
      <a className="brand" href="#records" aria-label="工作台首页"><span className="brand-mark"><Icon name="notebook" size={22} /></span><span><strong>工作台<span className="brand-dot">.</span></strong><small>LOCAL WORKBENCH</small></span></a>
      <div className="workspace-label"><span className="workspace-avatar">W</span><div><strong>个人工作区</strong><span>记录 · 执行 · 汇报</span></div></div>
      <div className="workbench-space-switch"><a className="active" href="#records">工作台</a><a href="#/community">广场</a></div>
      <p className="nav-caption">我的工作</p>
      <nav className="primary-nav" aria-label="主导航">{navigation.map(item => <a key={item.id} href={`#${item.id}`} aria-current={page === item.id ? 'page' : undefined}><Icon name={item.icon} /><span>{item.label}</span>{item.id === 'reports' && hasDirtyReports && <span className="dirty-dot" aria-label="有未保存修改" />}</a>)}</nav>
      <div className="sidebar-guide"><span className="guide-icon"><Icon name="sparkles" /></span><strong>少一点整理，多一点专注</strong><p>记下进展与计划，AI 帮你拆分，再整理成有来源的汇报。</p><a href="#reports">去整理汇报 <Icon name="arrow-right" size={14} /></a></div>
      <div className="sidebar-footer">{menu}</div></>}
    </aside>
    <div className={`app-body ${community ? 'body-shell' : ''}`}>
      <header className="topbar"><div className="breadcrumb"><span>{community ? '站内空间' : '个人工作区'}</span><span aria-hidden="true">/</span><strong>{current.label}</strong></div>{community && <a className="mobile-brand" href="#/community"><Icon name="notebook" className="icon" />工作台<span className="brand-dot">.</span></a>}<div className={community ? 'top-actions' : 'topbar-actions'}>{focusStatus && page !== 'focus' && <div className={community ? 'focus-chip' : 'focus-compact-controls'}><a className={community ? '' : 'focus-compact'} href="#focus" aria-label={`返回专注：${focusStatus}`}><Icon name="clock" size={15} /><span>{focusStatus}</span></a>{focusToggle && <button type="button" className="focus-compact-toggle" disabled={focusToggle.disabled} onClick={focusToggle.onClick}>{focusToggle.label}</button>}</div>}{community ? <><a className="btn ghost mobile-space-switch" href="#records">工作台</a>{menu}</> : <><time className={`topbar-date ${weekday === 'Sun' ? 'weekday-sunday' : weekday === 'Sat' ? 'weekday-saturday' : ''}`}><Icon name="calendar" size={15} />{date}</time><div className="mobile-account">{menu}</div></>}</div></header>
      <main id="main-content" className={`main-content ${community ? 'community-scroll' : ''}`} aria-label={community ? '社区内容滚动区' : undefined} tabIndex={community ? 0 : -1}>{children}</main>
    </div>
    {community && account && <CommunityBottomNav page={page} account={account} />}
  </div>
}
