import { useQuery } from '@tanstack/react-query'
import { format, parseISO } from 'date-fns'
import {
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  CircleDollarSign,
  ListChecks,
  RefreshCw,
  RotateCcw,
  Store,
  Tags,
  Wallet,
} from 'lucide-react'
import { useEffect, useMemo } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api'
import type { MonthCatalog, MonthlyAnalytics, Transaction } from '../types'

const palette = ['#0c6b58', '#d95743', '#c88a19', '#3d6f9f', '#8b5e83', '#517563', '#be7442']

function money(value: number) {
  return new Intl.NumberFormat('en-MY', { style: 'currency', currency: 'MYR' }).format(value)
}

function monthLabel(month: string) {
  return format(new Date(`${month}-01T12:00:00+08:00`), 'MMMM yyyy')
}

export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const catalogQuery = useQuery({ queryKey: ['analytics-months'], queryFn: () => api<MonthCatalog>('/analytics/months') })
  const requestedMonth = searchParams.get('month')
  const catalog = catalogQuery.data
  const selectedMonth = requestedMonth && catalog?.months.some((item) => item.month === requestedMonth)
    ? requestedMonth
    : catalog?.recommendedMonth

  useEffect(() => {
    if (selectedMonth && requestedMonth !== selectedMonth) setSearchParams({ month: selectedMonth }, { replace: true })
  }, [requestedMonth, selectedMonth, setSearchParams])

  const analyticsQuery = useQuery({
    queryKey: ['analytics', selectedMonth],
    queryFn: () => api<MonthlyAnalytics>(`/analytics/monthly?month=${selectedMonth}`),
    enabled: Boolean(selectedMonth),
  })
  const transactionsQuery = useQuery({ queryKey: ['transactions'], queryFn: () => api<Transaction[]>('/transactions') })
  const analytics = analyticsQuery.data
  const selectedOption = catalog?.months.find((item) => item.month === selectedMonth)
  const categories = Object.entries(analytics?.categories ?? {})
    .filter(([, value]) => Math.abs(value) > 0.004)
    .sort((left, right) => right[1] - left[1])
    .map(([name, value]) => ({ name, value }))
  const merchants = Object.entries(analytics?.merchants ?? {})
    .filter(([, value]) => Math.abs(value) > 0.004)
    .sort((left, right) => right[1] - left[1])
    .slice(0, 8)
  const daily = useMemo(() => dailySpending(transactionsQuery.data ?? [], selectedMonth), [transactionsQuery.data, selectedMonth])

  if (catalogQuery.isPending || (selectedMonth && analyticsQuery.isPending)) return <LoadingBlock />
  if (catalogQuery.isError) return <ErrorBlock message={catalogQuery.error.message} />
  if (analyticsQuery.isError) return <ErrorBlock message={analyticsQuery.error.message} />
  if (!catalog?.months.length || !selectedMonth) return <NoStatements />

  return (
    <div className="dashboard-grid statement-dashboard">
      <section className="overview-toolbar">
        <div>
          <p className="eyebrow">Analyzed statement month</p>
          <div className="month-title"><h2>{monthLabel(selectedMonth)}</h2>{selectedOption?.partial ? <span className="partial-badge">Partial coverage</span> : <span className="complete-badge">Closed month</span>}</div>
        </div>
        <label>Month<select aria-label="Analysis month" value={selectedMonth} onChange={(event) => setSearchParams({ month: event.target.value })}>{catalog.months.map((item) => <option key={item.month} value={item.month}>{monthLabel(item.month)}{item.partial ? ' (partial)' : ''}</option>)}</select></label>
      </section>

      {(analytics?.pendingExceptions ?? 0) > 0 ? <Link className="exception-banner" to={`/imports?month=${selectedMonth}`}><AlertTriangle size={17} /><span><strong>{analytics!.pendingExceptions} exceptions need attention.</strong> Clean rows are already included; held rows are not.</span></Link> : null}

      <section className="metric-strip" aria-label="Monthly finance summary">
        <Metric label="Net monthly spending" value={money(analytics?.expense ?? 0)} icon={<ArrowDownRight />} tone="expense" detail={`${analytics?.expenseChangePercent ?? 0}% vs previous imported month`} />
        <Metric label="Gross expenses" value={money(analytics?.grossExpense ?? 0)} icon={<CircleDollarSign />} tone="review" detail={`${money(analytics?.refunds ?? 0)} refunded across ${analytics?.refundCount ?? 0}`} />
        <Metric label="Recorded income" value={money(analytics?.income ?? 0)} icon={<ArrowUpRight />} tone="income" detail="Refunds shown separately" />
        <Metric label="Internal transfers" value={money(analytics?.transferVolume ?? 0)} icon={<RefreshCw />} tone="transfer" detail="Excluded from income and spending" />
      </section>

      <section className="panel activity-overview" aria-label="Monthly transaction activity">
        <div className="activity-summary">
          <ListChecks size={20} />
          <div><strong>{analytics?.transactionCount ?? 0} transactions analyzed</strong><span>{analytics?.expenseCount ?? 0} expenses</span><span>{analytics?.refundCount ?? 0} {plural(analytics?.refundCount ?? 0, 'refund')}</span></div>
        </div>
        <div className="provider-activity">{Object.entries(analytics?.providerActivity ?? {}).map(([provider, count]) => <div key={provider}><strong>{providerName(provider)}</strong><span>{count} {plural(count, 'transaction')}</span></div>)}</div>
      </section>

      <section className="panel category-panel primary-analysis">
        <div className="panel-heading"><div><p className="eyebrow">Primary analysis</p><h2>Spending by category</h2></div><span>Net of refunds</span></div>
        {categories.length ? <div className="category-analysis">
          <ResponsiveContainer width="100%" height={220}><PieChart><Pie data={categories} dataKey="value" nameKey="name" innerRadius={60} outerRadius={92} paddingAngle={2} isAnimationActive={false}>{categories.map((entry, index) => <Cell key={entry.name} fill={palette[index % palette.length]} />)}</Pie><Tooltip formatter={(value) => money(Number(value))} /></PieChart></ResponsiveContainer>
          <div className="legend-list">{categories.map((entry, index) => <div key={entry.name}><span style={{ background: palette[index % palette.length] }} /><strong>{entry.name}</strong><em>{money(entry.value)}</em></div>)}</div>
        </div> : <EmptyState icon={<CircleDollarSign />} text="No posted expenses are available for this month." />}
        <div className="category-quality"><div><Tags size={17} /><strong>{analytics?.categorizationPercent ?? 0}% categorized</strong><span>{money(analytics?.uncategorizedExpense ?? 0)} awaiting category</span></div><div className="quality-track"><span style={{ width: `${Math.min(100, Math.max(0, analytics?.categorizationPercent ?? 0))}%` }} /></div></div>
      </section>

      <section className="panel merchant-panel primary-analysis">
        <div className="panel-heading"><div><p className="eyebrow">Primary analysis</p><h2>Top merchants</h2></div><Store size={18} /></div>
        {merchants.length ? <div className="merchant-list">{merchants.map(([merchant, amount], index) => <div key={merchant}><span>{index + 1}</span><strong>{merchant}</strong><em>{money(amount)}</em></div>)}</div> : <EmptyState icon={<Store />} text="Merchant totals appear after statement transactions are posted." />}
      </section>

      <section className="panel comparison-panel">
        <div className="panel-heading"><div><p className="eyebrow">Month comparison</p><h2>Previous imported month</h2></div><RotateCcw size={18} /></div>
        <div className="comparison-body"><strong>{money(analytics?.previousExpense ?? 0)}</strong><p>{comparisonText(analytics?.expenseChangePercent ?? 0)}</p></div>
        <div className="coverage-list">{(analytics?.coverage ?? []).map((coverage) => <div key={coverage.provider}><strong>{providerName(coverage.provider)}</strong><span>{format(parseISO(coverage.coveredFrom), 'dd MMM')} to {format(parseISO(coverage.coveredTo), 'dd MMM yyyy')}</span><em>{coverage.partial ? 'Partial' : 'Complete'}</em></div>)}</div>
      </section>

      <section className="panel budget-panel">
        <div className="panel-heading"><div><p className="eyebrow">Budgets</p><h2>Monthly limits</h2></div><Wallet size={18} /></div>
        <div className="budget-list">{(analytics?.budgets ?? []).length ? analytics!.budgets.map((budget) => <div className="budget-row" key={budget.id}><div><strong>{budget.category}</strong><span>{money(budget.spent)} of {money(budget.limit)}</span></div><div className="progress-track"><span className={budget.percent > 100 ? 'over' : ''} style={{ width: `${Math.min(100, Math.max(0, budget.percent))}%` }} /></div><em>{budget.percent}%</em></div>) : <EmptyState icon={<Wallet />} text="Set category budgets to compare them with this imported month." />}</div>
      </section>

      <section className="panel trend-panel secondary-analysis">
        <div className="panel-heading"><div><p className="eyebrow">Secondary detail</p><h2>Daily movement</h2></div><span>{monthLabel(selectedMonth)}</span></div>
        {daily.length ? <ResponsiveContainer width="100%" height={230}><BarChart data={daily} margin={{ top: 20, right: 14, left: -14, bottom: 0 }}><CartesianGrid stroke="#dde2da" vertical={false} /><XAxis dataKey="day" tickLine={false} axisLine={false} fontSize={12} /><YAxis tickLine={false} axisLine={false} fontSize={12} /><Tooltip formatter={(value) => money(Number(value))} /><Bar dataKey="amount" fill="#3d6f9f" radius={[3, 3, 0, 0]} isAnimationActive={false} /></BarChart></ResponsiveContainer> : <EmptyState icon={<CircleDollarSign />} text="No daily expense movement is available for this month." />}
      </section>
    </div>
  )
}

function dailySpending(transactions: Transaction[], month?: string) {
  if (!month) return []
  const daily = new Map<string, number>()
  transactions.filter((item) => item.status === 'CONFIRMED' && item.occurredAt.startsWith(month))
    .forEach((item) => {
      const amount = item.kind === 'EXPENSE' && item.direction === 'DEBIT' ? Number(item.amount)
        : item.kind === 'REFUND' && item.direction === 'CREDIT' ? -Number(item.amount) : 0
      if (amount !== 0) {
        const day = format(parseISO(item.occurredAt), 'dd MMM')
        daily.set(day, (daily.get(day) ?? 0) + amount)
      }
    })
  return [...daily].map(([day, amount]) => ({ day, amount }))
}

function comparisonText(change: number) {
  if (change === 0) return 'No change from the previous imported month.'
  return `${Math.abs(change)}% ${change > 0 ? 'more' : 'less'} net spending than the previous imported month.`
}

function providerName(provider: string) { return provider === 'PUBLIC_BANK' ? 'Public Bank' : provider }
function plural(count: number, word: string) { return count === 1 ? word : `${word}s` }
function Metric({ label, value, icon, tone, detail }: { label: string; value: string; icon: React.ReactNode; tone: string; detail: string }) { return <article className={`metric ${tone}`}><div className="metric-icon">{icon}</div><p>{label}</p><strong>{value}</strong><span>{detail}</span></article> }
function EmptyState({ icon, text }: { icon: React.ReactNode; text: string }) { return <div className="empty-state">{icon}<p>{text}</p></div> }
function LoadingBlock() { return <div className="loading-block"><span /><span /><span /></div> }
function ErrorBlock({ message }: { message: string }) { return <div className="error-block"><strong>Unable to load this view</strong><p>{message}</p></div> }
function NoStatements() { return <div className="review-empty"><CircleDollarSign size={38} /><h2>No statement months yet</h2><p>Import a Public Bank or TNG monthly statement to build your expense overview.</p><Link className="primary-button" to="/imports">Import statements</Link></div> }
