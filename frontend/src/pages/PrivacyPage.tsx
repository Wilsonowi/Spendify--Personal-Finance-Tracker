import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Database, FileKey2, LockKeyhole, ScanLine, Trash2 } from 'lucide-react'
import { api } from '../api'
import type { DocumentRecord } from '../types'

export function PrivacyPage() {
  const queryClient = useQueryClient()
  const documents = useQuery({ queryKey: ['imports'], queryFn: () => api<DocumentRecord[]>('/imports') })

  async function remove(id: string) {
    if (!window.confirm('Permanently delete this encrypted source document and its derived transactions and transfer matches?')) return
    await api(`/imports/${id}`, { method: 'DELETE' })
    await queryClient.invalidateQueries({ queryKey: ['imports'] })
  }

  return <div className="privacy-layout">
    <section className="privacy-principles">
      <article><FileKey2 /><div><strong>Per-file encryption</strong><p>Each original uses an independent AES-256-GCM data key wrapped by the local master key.</p></div></article>
      <article><ScanLine /><div><strong>Private OCR boundary</strong><p>Document bytes reach only the internal OCR container and are deleted immediately after processing.</p></div></article>
      <article><LockKeyhole /><div><strong>Owner-scoped access</strong><p>Every source, transaction, rule, transfer, and budget is authorized against the signed-in account.</p></div></article>
      <article><Database /><div><strong>No financial caching</strong><p>The PWA caches its interface only. Financial responses and document previews use no-store.</p></div></article>
    </section>
    <section className="panel retention-panel"><div className="panel-heading"><div><p className="eyebrow">Retention</p><h2>Encrypted originals</h2></div><span>{documents.data?.length ?? 0} stored</span></div><div className="retention-list">
      {(documents.data ?? []).map((document) => <article key={document.id}><FileKey2 size={18} /><div><strong>{document.name}</strong><span>{(document.size / 1024).toFixed(1)} KB · {document.status.toLowerCase().replace('_', ' ')}</span></div><button className="icon-button danger" title="Delete original" onClick={() => remove(document.id)}><Trash2 size={17} /></button></article>)}
      {!documents.isPending && !documents.data?.length ? <div className="empty-state"><FileKey2 /><p>No encrypted originals are stored.</p></div> : null}
    </div></section>
  </div>
}
