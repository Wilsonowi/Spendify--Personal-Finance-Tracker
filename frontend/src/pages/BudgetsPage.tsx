import { useQueries, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { CircleGauge, Save } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { api } from '../api'
import type { Category, MonthlyAnalytics } from '../types'

function monthValue() { return format(new Date(), 'yyyy-MM') }
function money(value: number) { return new Intl.NumberFormat('en-MY', { style: 'currency', currency: 'MYR' }).format(value) }

export function BudgetsPage() {
  const [month, setMonth] = useState(monthValue())
  const queryClient = useQueryClient()
  const [analyticsQuery, categoriesQuery] = useQueries({ queries: [
    { queryKey: ['analytics', month], queryFn: () => api<MonthlyAnalytics>(`/analytics/monthly?month=${month}`) },
    { queryKey: ['categories'], queryFn: () => api<Category[]>('/categories') },
  ] })

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    await api(`/budgets/${data.get('categoryId')}?month=${month}`, {
      method: 'PUT', body: JSON.stringify({ amount: Number(data.get('amount')) }),
    })
    form.reset()
    await queryClient.invalidateQueries({ queryKey: ['analytics', month] })
  }

  return <div className="stack-page">
    <div className="toolbar"><label>Budget month<input type="month" value={month} onChange={(event) => setMonth(event.target.value)} /></label></div>
    <section className="budget-editor">
      <form className="panel compact-form" onSubmit={save}><div className="panel-heading"><div><p className="eyebrow">Monthly limit</p><h2>Add or update budget</h2></div><CircleGauge /></div><label>Category<select name="categoryId">{categoriesQuery.data?.map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label><label>Limit (MYR)<input name="amount" type="number" min="0.01" step="0.01" required /></label><button className="primary-button"><Save size={17} /> Save budget</button></form>
      <section className="panel budget-summary"><div className="panel-heading"><div><p className="eyebrow">Current position</p><h2>Category limits</h2></div><span>{analyticsQuery.data?.budgets.length ?? 0} active</span></div><div className="budget-list">
        {(analyticsQuery.data?.budgets ?? []).map((budget) => <div className="budget-row detailed" key={budget.id}><div><strong>{budget.category}</strong><span>{money(budget.spent)} spent · {money(Math.max(0, budget.limit - budget.spent))} remaining</span></div><div className="progress-track"><span className={budget.percent > 100 ? 'over' : ''} style={{ width: `${Math.min(100, budget.percent)}%` }} /></div><em>{budget.percent}%</em></div>)}
        {!analyticsQuery.isPending && !analyticsQuery.data?.budgets.length ? <div className="empty-state"><CircleGauge /><p>No budgets are set for this month.</p></div> : null}
      </div></section>
    </section>
  </div>
}
