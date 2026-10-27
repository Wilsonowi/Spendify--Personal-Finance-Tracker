import { useQueries, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, BrainCircuit, Plus } from 'lucide-react'
import { type FormEvent } from 'react'
import { api } from '../api'
import type { Category, MerchantRule } from '../types'

export function RulesPage() {
  const queryClient = useQueryClient()
  const [rulesQuery, categoriesQuery] = useQueries({ queries: [
    { queryKey: ['merchant-rules'], queryFn: () => api<MerchantRule[]>('/merchant-rules') },
    { queryKey: ['categories'], queryFn: () => api<Category[]>('/categories') },
  ] })
  const categoryNames = new Map(categoriesQuery.data?.map((category) => [category.id, category.name]))

  async function addRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    await api('/merchant-rules', { method: 'POST', body: JSON.stringify({ alias: data.get('alias'), canonicalMerchant: data.get('merchant'), categoryId: data.get('categoryId') }) })
    form.reset()
    await queryClient.invalidateQueries({ queryKey: ['merchant-rules'] })
  }

  return <div className="rules-layout">
    <section className="panel rules-intro"><BrainCircuit size={30} /><p className="eyebrow">Private learning</p><h2>Your corrections become deterministic</h2><p>Merchant aliases are scoped to your account. They run before keyword rules and optional AI suggestions, so the same correction is not requested twice.</p></section>
    <form className="panel compact-form" onSubmit={addRule}><div className="panel-heading"><div><p className="eyebrow">New mapping</p><h2>Teach a merchant</h2></div></div><label>Raw alias<input name="alias" placeholder="X001 DAVIDS CHICK" required maxLength={180} /></label><label>Canonical merchant<input name="merchant" placeholder="David's Chick" required maxLength={160} /></label><label>Category<select name="categoryId">{categoriesQuery.data?.map((category) => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label><button className="primary-button"><Plus size={17} /> Add private rule</button></form>
    <section className="panel rule-list-panel"><div className="panel-heading"><div><p className="eyebrow">Resolution order: first</p><h2>Saved merchant rules</h2></div><span>{rulesQuery.data?.length ?? 0}</span></div><div className="rule-list">
      {(rulesQuery.data ?? []).map((rule) => <article key={rule.id}><code>{rule.alias}</code><ArrowRight size={16} /><strong>{rule.canonicalMerchant}</strong><span>{categoryNames.get(rule.categoryId) ?? 'Unknown'}</span></article>)}
      {!rulesQuery.isPending && !rulesQuery.data?.length ? <div className="empty-state"><BrainCircuit /><p>Confirm a merchant correction or add a rule here.</p></div> : null}
    </div></section>
  </div>
}
