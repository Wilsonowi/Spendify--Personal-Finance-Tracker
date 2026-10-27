import {
  ChartNoAxesCombined,
  FileScan,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  ReceiptText,
  Tags,
} from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { api } from '../api'
import { queryClient } from '../query'
import type { User } from '../types'

const navigation = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/transactions', label: 'Transactions', icon: ReceiptText },
  { to: '/imports', label: 'Imports', icon: FileScan },
  { to: '/budgets', label: 'Budgets', icon: ChartNoAxesCombined },
  { to: '/rules', label: 'Merchant rules', icon: Tags },
  { to: '/privacy', label: 'Privacy', icon: LockKeyhole },
]

const titles: Record<string, string> = {
  '/': 'Monthly overview',
  '/transactions': 'Transactions',
  '/imports': 'Statements and exceptions',
  '/budgets': 'Budget control',
  '/rules': 'Merchant learning',
  '/privacy': 'Privacy controls',
}

export function AppShell({ user }: { user: User }) {
  const location = useLocation()

  async function logout() {
    await api('/auth/logout', { method: 'POST' })
    queryClient.clear()
    window.location.assign('/')
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="wordmark"><span className="brand-mark">S</span><span>Spendify</span></div>
        <nav aria-label="Primary navigation">
          {navigation.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => isActive ? 'active' : ''}>
              <Icon size={18} strokeWidth={1.8} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip"><span>{user.displayName.slice(0, 1).toUpperCase()}</span><div><strong>{user.displayName}</strong><small>{user.email}</small></div></div>
          <button className="icon-button" onClick={logout} title="Sign out" aria-label="Sign out"><LogOut size={18} /></button>
        </div>
      </aside>
      <main className="main-area">
        <header className="topbar">
          <div><p className="eyebrow">Personal finance workspace</p><h1>{titles[location.pathname] ?? 'Spendify'}</h1></div>
          <NavLink to="/imports" className="primary-button"><FileScan size={17} /> Import statements</NavLink>
        </header>
        <div className="page-content"><Outlet /></div>
      </main>
      <nav className="mobile-nav" aria-label="Mobile navigation">
        {navigation.slice(0, 5).map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} aria-label={label}><Icon size={20} /></NavLink>
        ))}
      </nav>
    </div>
  )
}
