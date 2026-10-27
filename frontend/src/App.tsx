import { useQuery } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { api } from './api'
import { AppShell } from './components/AppShell'
import { AuthPage } from './pages/AuthPage'
import type { User } from './types'

const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const TransactionsPage = lazy(() => import('./pages/TransactionsPage').then((module) => ({ default: module.TransactionsPage })))
const ImportsPage = lazy(() => import('./pages/ImportsPage').then((module) => ({ default: module.ImportsPage })))
const BudgetsPage = lazy(() => import('./pages/BudgetsPage').then((module) => ({ default: module.BudgetsPage })))
const RulesPage = lazy(() => import('./pages/RulesPage').then((module) => ({ default: module.RulesPage })))
const PrivacyPage = lazy(() => import('./pages/PrivacyPage').then((module) => ({ default: module.PrivacyPage })))

export default function App() {
  const userQuery = useQuery({
    queryKey: ['user'],
    queryFn: () => api<User>('/auth/me'),
    retry: false,
  })

  if (userQuery.isPending) {
    return <div className="startup"><span className="brand-mark">S</span><p>Opening your private ledger...</p></div>
  }
  if (!userQuery.data) return <AuthPage />

  return (
    <Routes>
      <Route element={<AppShell user={userQuery.data} />}>
        <Route index element={<Suspense fallback={<PageFallback />}><DashboardPage /></Suspense>} />
        <Route path="transactions" element={<Suspense fallback={<PageFallback />}><TransactionsPage /></Suspense>} />
        <Route path="imports" element={<Suspense fallback={<PageFallback />}><ImportsPage /></Suspense>} />
        <Route path="budgets" element={<Suspense fallback={<PageFallback />}><BudgetsPage /></Suspense>} />
        <Route path="rules" element={<Suspense fallback={<PageFallback />}><RulesPage /></Suspense>} />
        <Route path="privacy" element={<Suspense fallback={<PageFallback />}><PrivacyPage /></Suspense>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function PageFallback() {
  return <div className="loading-block"><span /><span /><span /></div>
}
