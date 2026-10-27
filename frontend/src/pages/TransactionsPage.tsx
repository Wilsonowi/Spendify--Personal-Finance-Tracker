import { useQueries, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Download, Plus, Search, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { api } from '../api'
import type { Account, Category, Transaction } from '../types'

function money(value: number) {
  return new Intl.NumberFormat('en-MY', { style: 'currency', currency: 'MYR' }).format(value)
}

export function TransactionsPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [exportFrom, setExportFrom] = useState('2026-01-01')
  const [exportTo, setExportTo] = useState('2026-12-31')
  const [showForm, setShowForm] = useState(false)
  const [transactionsQuery, accountsQuery, categoriesQuery] = useQueries({ queries: [
    { queryKey: ['transactions', search, status], queryFn: () => api<Transaction[]>(`/transactions?q=${encodeURIComponent(search)}${status ? `&status=${status}` : ''}`) },
    { queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') },
    { queryKey: ['categories'], queryFn: () => api<Category[]>('/categories') },
  ] })

  async function addManual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    await api('/transactions', { method: 'POST', body: JSON.stringify({
      accountId: data.get('accountId'), categoryId: data.get('categoryId'),
      direction: data.get('direction'), kind: data.get('kind'), amount: Number(data.get('amount')),
      occurredAt: new Date(String(data.get('occurredAt'))).toISOString(), merchant: data.get('merchant'),
      reference: data.get('reference'),
    }) })
    setShowForm(false)
    await queryClient.invalidateQueries({ queryKey: ['transactions'] })
  }

  return (
    <div className="stack-page">
      <div className="toolbar">
        <label className="search-box"><Search size={17} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search merchant, reference, amount" /></label>
        <select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filter status"><option value="">All statuses</option><option value="DRAFT">Needs review</option><option value="CONFIRMED">Confirmed</option><option value="DUPLICATE">Duplicate</option></select>
        <label>Export from<input type="date" value={exportFrom} onChange={(event) => setExportFrom(event.target.value)} /></label>
        <label>Export to<input type="date" value={exportTo} onChange={(event) => setExportTo(event.target.value)} /></label>
        <a className="secondary-button" href={`/api/transactions/export.csv?from=${exportFrom}&to=${exportTo}`}><Download size={17} /> CSV</a>
        <button className="primary-button" onClick={() => setShowForm(true)}><Plus size={17} /> Add entry</button>
      </div>
      <section className="table-panel">
        <table>
          <thead><tr><th>Date</th><th>Merchant</th><th>Account</th><th>Category</th><th>Status</th><th className="amount-cell">Amount</th></tr></thead>
          <tbody>
            {(transactionsQuery.data ?? []).map((transaction) => (
              <tr key={transaction.id}>
                <td><strong>{format(new Date(transaction.occurredAt), 'dd MMM yyyy')}</strong><small>{format(new Date(transaction.occurredAt), 'HH:mm')}</small></td>
                <td><strong>{transaction.merchant || 'Unnamed transaction'}</strong><small>{transaction.reference || 'No reference'}</small></td>
                <td>{transaction.account}</td><td>{transaction.category}</td>
                <td><span className={`status-pill ${transaction.status.toLowerCase()}`}>{transaction.status === 'DRAFT' ? 'Needs review' : transaction.status.toLowerCase()}</span></td>
                <td className={`amount-cell ${transaction.direction === 'DEBIT' ? 'debit' : 'credit'}`}>{transaction.direction === 'DEBIT' ? '-' : '+'}{money(Number(transaction.amount))}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!transactionsQuery.isPending && !(transactionsQuery.data ?? []).length ? <div className="empty-table"><p>No transactions match this view.</p></div> : null}
      </section>
      {showForm ? <div className="modal-backdrop" role="presentation"><form className="modal" onSubmit={addManual}><div className="modal-heading"><div><p className="eyebrow">Manual record</p><h2>Add transaction</h2></div><button type="button" className="icon-button" onClick={() => setShowForm(false)}><X /></button></div><div className="form-grid">
        <label>Account<select name="accountId" required>{accountsQuery.data?.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}</select></label>
        <label>Category<select name="categoryId" required>{categoriesQuery.data?.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
        <label>Direction<select name="direction"><option value="DEBIT">Debit</option><option value="CREDIT">Credit</option></select></label>
        <label>Type<select name="kind"><option value="EXPENSE">Expense</option><option value="INCOME">Income</option><option value="REFUND">Refund</option></select></label>
        <label>Amount<input name="amount" type="number" step="0.01" min="0.01" required /></label>
        <label>Date and time<input name="occurredAt" type="datetime-local" defaultValue={format(new Date(), "yyyy-MM-dd'T'HH:mm")} required /></label>
        <label className="span-two">Merchant<input name="merchant" maxLength={160} /></label><label className="span-two">Reference<input name="reference" maxLength={120} /></label>
      </div><div className="modal-actions"><button type="button" className="secondary-button" onClick={() => setShowForm(false)}>Cancel</button><button className="primary-button">Save transaction</button></div></form></div> : null}
    </div>
  )
}
