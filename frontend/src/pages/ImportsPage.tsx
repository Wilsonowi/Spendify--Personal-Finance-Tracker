import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  ChevronDown,
  FileImage,
  FileText,
  FileUp,
  RefreshCw,
  ShieldCheck,
  Trash2,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api, downloadDocument, downloadDocumentPage } from '../api'
import type { Account, Category, DocumentRecord, MonthCatalog, ProviderCoverage, ReviewQueuePage, Transaction, Transfer, TransferSide } from '../types'

type ImportMode = 'slip' | 'statement'
type PendingUploads = { ids: string[]; navigateToOverview: boolean }

export function ImportsPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<ImportMode>('statement')
  const [periodStart, setPeriodStart] = useState('2026-01-01')
  const [periodEnd, setPeriodEnd] = useState('2026-12-31')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [documentFilter, setDocumentFilter] = useState('')
  const [provider, setProvider] = useState('')
  const [month, setMonth] = useState(searchParams.get('month') ?? '')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [error, setError] = useState('')
  const [duplicateWarning, setDuplicateWarning] = useState(false)
  const [transferError, setTransferError] = useState('')
  const [expandedTransferId, setExpandedTransferId] = useState<string | null>(null)
  const [pendingUploads, setPendingUploads] = useState<PendingUploads | null>(null)
  const [uploadNotice, setUploadNotice] = useState('')

  const documentsQuery = useQuery({
    queryKey: ['imports'],
    queryFn: () => api<DocumentRecord[]>('/imports'),
    refetchInterval: (query) => query.state.data?.some((item) => ['UPLOADED', 'PROCESSING'].includes(item.status)) ? 1500 : false,
  })
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') })
  const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('/categories') })
  const coverageQuery = useQuery({ queryKey: ['coverage', 2026], queryFn: () => api<ProviderCoverage[]>('/imports/coverage?year=2026') })
  const transfersQuery = useQuery({ queryKey: ['transfers'], queryFn: () => api<Transfer[]>('/transfers') })
  const queuePath = useMemo(() => {
    const params = new URLSearchParams({ page: '0', size: '50' })
    if (documentFilter) params.set('document', documentFilter)
    if (provider) params.set('provider', provider)
    if (month) params.set('month', month)
    if (categoryFilter) params.set('category', categoryFilter)
    return `/review-queue?${params}`
  }, [categoryFilter, documentFilter, month, provider])
  const queueQuery = useQuery({ queryKey: ['review-queue', queuePath], queryFn: () => api<ReviewQueuePage>(queuePath) })
  const exceptions = useMemo(() => queueQuery.data?.items ?? [], [queueQuery.data?.items])
  const selected = exceptions.find((item) => item.id === selectedId)
  const selectedDocument = (documentsQuery.data ?? []).find((item) => item.id === selected?.documentId)
  const statements = (documentsQuery.data ?? []).filter((item) => item.kind === 'STATEMENT')
  const latestStatement = statements[0]
  const openTransfers = (transfersQuery.data ?? []).filter((item) => item.status === 'PROPOSED')
  const documentStatusKey = documentsQuery.data?.map((item) => `${item.id}:${item.status}:${item.resolvedCount}:${item.exceptionCount}`).join('|') ?? ''

  useEffect(() => {
    if (!pendingUploads?.ids.length || !documentsQuery.data) return
    const uploaded = documentsQuery.data.filter((item) => pendingUploads.ids.includes(item.id))
    if (uploaded.length !== pendingUploads.ids.length || uploaded.some((item) => ['UPLOADED', 'PROCESSING'].includes(item.status))) return
    setPendingUploads(null)
    void Promise.all([
      queryClient.invalidateQueries({ queryKey: ['review-queue'] }),
      queryClient.invalidateQueries({ queryKey: ['analytics'] }),
      queryClient.invalidateQueries({ queryKey: ['analytics-months'] }),
      queryClient.invalidateQueries({ queryKey: ['transactions'] }),
      queryClient.invalidateQueries({ queryKey: ['coverage'] }),
      queryClient.invalidateQueries({ queryKey: ['transfers'] }),
    ]).then(async () => {
      if (!pendingUploads.navigateToOverview || uploaded.every((item) => item.status === 'FAILED')) return
      const catalog = await api<MonthCatalog>('/analytics/months')
      navigate(`/?month=${catalog.recommendedMonth}`)
    })
  }, [documentStatusKey, documentsQuery.data, navigate, pendingUploads, queryClient])

  useEffect(() => {
    if (documentsQuery.data?.some((item) => ['NEEDS_REVIEW', 'PARTIALLY_POSTED', 'CONFIRMED'].includes(item.status))) {
      void queryClient.invalidateQueries({ queryKey: ['review-queue'] })
      void queryClient.invalidateQueries({ queryKey: ['coverage'] })
    }
  }, [documentStatusKey, documentsQuery.data, queryClient])

  useEffect(() => {
    if (selectedId && !exceptions.some((item) => item.id === selectedId)) setSelectedId(null)
  }, [exceptions, selectedId])

  const upload = useMutation({
    mutationFn: async (files: FileList | File[]) => {
      const selectedFiles = Array.from(files)
      const imageFiles = mode === 'statement' ? selectedFiles.filter(isSupportedImageFile) : []
      const statementFiles = mode === 'statement' ? selectedFiles.filter((file) => !isSupportedImageFile(file)) : []
      const uploadGroup = (group: File[], statements: boolean) => {
        const form = new FormData()
        group.forEach((file) => form.append('files', file))
        if (statements) {
          form.append('periodStart', periodStart)
          form.append('periodEnd', periodEnd)
        }
        return api<DocumentRecord[]>(statements ? '/imports/statements' : '/imports', {
          method: 'POST',
          body: form,
        })
      }

      const requests = mode === 'slip'
        ? [{ promise: uploadGroup(selectedFiles, false) }]
        : [
            ...(statementFiles.length ? [{ promise: uploadGroup(statementFiles, true) }] : []),
            ...(imageFiles.length ? [{ promise: uploadGroup(imageFiles, false) }] : []),
          ]
      const results = await Promise.allSettled(requests.map((request) => request.promise))
      const documents = results.flatMap((result) => result.status === 'fulfilled' ? result.value : [])
      const failures = results.flatMap((result) => result.status === 'rejected'
        ? [result.reason instanceof Error ? result.reason.message : 'Some files could not be uploaded.']
        : [])
      if (documents.length === 0 && failures.length) throw new Error(failures.join(' '))
      return {
        documents,
        failures,
        navigateToOverview: mode === 'statement' && imageFiles.length === 0 && statementFiles.length > 0,
      }
    },
    onSuccess: async ({ documents, failures, navigateToOverview }) => {
      setUploadNotice(failures.join(' '))
      setPendingUploads(documents.length
        ? { ids: documents.map((item) => item.id), navigateToOverview }
        : null)
      await queryClient.invalidateQueries({ queryKey: ['imports'] })
    },
    onError: (reason) => setUploadNotice(reason instanceof Error ? reason.message : 'The files could not be uploaded.'),
  })

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['review-queue'] }),
      queryClient.invalidateQueries({ queryKey: ['imports'] }),
      queryClient.invalidateQueries({ queryKey: ['analytics'] }),
      queryClient.invalidateQueries({ queryKey: ['analytics-months'] }),
      queryClient.invalidateQueries({ queryKey: ['transactions'] }),
      queryClient.invalidateQueries({ queryKey: ['transfers'] }),
      queryClient.invalidateQueries({ queryKey: ['coverage'] }),
    ])
  }

  async function resolveException(event: FormEvent<HTMLFormElement>, allowDuplicate = false) {
    event.preventDefault()
    if (!selected) return
    setError('')
    const data = new FormData(event.currentTarget)
    const dateValue = String(data.get('occurredAt'))
    const occurredAt = selected.occurredPrecision === 'DATE_ONLY'
      ? new Date(`${dateValue}T12:00:00+08:00`).toISOString()
      : new Date(dateValue).toISOString()
    const payload = {
      accountId: data.get('accountId'), categoryId: data.get('categoryId'),
      direction: data.get('direction'), kind: data.get('kind'), amount: Number(data.get('amount')),
      occurredAt, merchant: data.get('merchant'), reference: data.get('reference'),
    }
    try {
      await api(`/transactions/${selected.id}`, { method: 'PATCH', body: JSON.stringify(payload) })
      if (data.get('remember') === 'on' && selected.merchant && payload.merchant) {
        await api('/merchant-rules', { method: 'POST', body: JSON.stringify({ alias: selected.merchant, canonicalMerchant: payload.merchant, categoryId: payload.categoryId }) })
      }
      await api(`/transactions/${selected.id}/confirm?allowDuplicate=${allowDuplicate}`, { method: 'POST' })
      setDuplicateWarning(false)
      setSelectedId(null)
      await refresh()
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : 'Exception could not be resolved.'
      setError(message)
      setDuplicateWarning(message.toLowerCase().includes('probable duplicate'))
    }
  }

  async function exclude(reason: string) {
    if (!selected || !reason) return
    await api(`/transactions/${selected.id}/exclude`, { method: 'POST', body: JSON.stringify({ reason }) })
    setSelectedId(null)
    await refresh()
  }

  async function updateTransfer(id: string, action: 'confirm' | 'reject') {
    setTransferError('')
    try {
      await api(`/transfers/${id}/${action}`, { method: 'POST' })
      setExpandedTransferId(null)
      await refresh()
    } catch (reason) {
      setTransferError(reason instanceof Error ? reason.message : 'The transfer could not be updated.')
    }
  }

  async function deleteDocument(document: DocumentRecord) {
    const effect = document.kind === 'STATEMENT'
      ? `Delete ${document.name}? Its imported transactions and transfer matches will also be removed.`
      : `Delete ${document.name}? Its extracted transaction will also be removed.`
    if (!window.confirm(effect)) return
    await api(`/imports/${document.id}`, { method: 'DELETE' })
    if (documentFilter === document.id) setDocumentFilter('')
    setSelectedId(null)
    await refresh()
  }

  const coverageGap = coverageQuery.data?.find((item) => item.provider === 'PUBLIC_BANK' && !item.complete)

  return <div className="imports-stack statement-first-imports">
    <div className="import-mode-bar" role="group" aria-label="Import type">
      <button className={mode === 'statement' ? 'active' : ''} onClick={() => setMode('statement')}>Statement</button>
      <button className={mode === 'slip' ? 'active' : ''} onClick={() => setMode('slip')}>Single slip</button>
    </div>
    {mode === 'statement' && coverageGap?.gapStart ? <div className="coverage-warning"><AlertTriangle size={16} /><span><strong>Coverage gap:</strong> {formatDate(coverageGap.gapStart)} to {formatDate(coverageGap.gapEnd!)}. Activity is unavailable, not zero.</span></div> : null}

    <section className="statement-upload-band">
      <div className="statement-range"><div><p className="eyebrow">{mode === 'statement' ? 'Monthly statement import' : 'Single transaction import'}</p><strong>{mode === 'statement' ? 'Public Bank or TNG PDF' : 'Slip image or PDF'}</strong></div>{mode === 'statement' ? <><label>From<input type="date" value={periodStart} onChange={(event) => setPeriodStart(event.target.value)} /></label><label>To<input type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} /></label></> : null}</div>
      <label className={`drop-zone ${upload.isPending ? 'busy' : ''}`}><input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" multiple onChange={(event) => { const files = Array.from(event.currentTarget.files ?? []); event.currentTarget.value = ''; if (files.length) upload.mutate(files) }} /><FileUp size={25} /><strong>{upload.isPending ? 'Encrypting uploads...' : mode === 'statement' ? 'Choose statements or transaction images' : 'Choose transaction slips'}</strong><span>{mode === 'statement' ? 'PDFs are imported as statements; JPG, PNG, and WebP images go through OCR' : 'Review OCR output before posting'}</span></label>
    </section>
    {uploadNotice ? <p className="form-error">{uploadNotice}</p> : null}

    {latestStatement ? <section className="import-outcome" aria-label="Latest statement result">
      <Outcome label="Posted" value={latestStatement.autoPostedCount} tone="posted" />
      <Outcome label="Transfers" value={latestStatement.transferCount} tone="transfer" />
      <Outcome label="Duplicates" value={latestStatement.duplicateCount} tone="duplicate" />
      <Outcome label="Exceptions" value={latestStatement.exceptionCount} tone="exception" />
    </section> : null}

    <section className="panel source-documents-panel">
      <div className="panel-heading"><div><p className="eyebrow">Import history</p><h2>Source documents</h2></div><span>{documentsQuery.data?.length ?? 0}</span></div>
      <div className="document-list horizontal-documents">{(documentsQuery.data ?? []).map((document) => <article key={document.id} className={documentFilter === document.id ? 'selected' : ''}>
        <button className="document-filter-button" onClick={() => setDocumentFilter(documentFilter === document.id ? '' : document.id)}>
          <span className={`document-icon ${document.status.toLowerCase()}`}>{document.kind === 'STATEMENT' ? <FileText size={18} /> : <FileImage size={18} />}</span>
          <div><strong>{document.name}</strong><small>{statusLabel(document.status)} | {format(new Date(document.createdAt), 'dd MMM, HH:mm')}</small>{document.error ? <em>{document.error}</em> : null}{document.kind === 'STATEMENT' ? <em className="document-progress">{document.autoPostedCount} posted, {document.exceptionCount} exceptions</em> : null}{document.validationStatus !== 'VALID' && document.validationMessage ? <em>{document.validationMessage}</em> : null}</div>
          {['UPLOADED', 'PROCESSING'].includes(document.status) ? <RefreshCw className="spin" size={16} /> : null}
        </button>
        <button className="document-delete icon-button" title={`Delete ${document.name}`} onClick={() => void deleteDocument(document)}><Trash2 size={16} /></button>
      </article>)}</div>
    </section>

    {openTransfers.length ? <section className="panel transfer-exceptions"><div className="panel-heading"><div><p className="eyebrow">Optional matching</p><h2>Ambiguous transfers</h2></div><span>{openTransfers.length}</span></div>{transferError ? <p className="form-error transfer-error"><AlertTriangle size={15} />{transferError}</p> : null}<div className="transfer-match-list">{openTransfers.map((transfer) => <article key={transfer.id} className={expandedTransferId === transfer.id ? 'expanded' : ''}><div className="transfer-match-row"><div className="score-ring">{transfer.score}</div><button className="transfer-match-summary" aria-label={expandedTransferId === transfer.id ? 'Hide transfer details' : 'Show transfer details'} onClick={() => setExpandedTransferId(expandedTransferId === transfer.id ? null : transfer.id)}><span><strong>Possible bank-to-wallet transfer</strong><small>{transfer.evidence}</small></span><ChevronDown size={17} /></button><div className="row-actions"><button className="icon-button positive" title="Confirm transfer" onClick={() => void updateTransfer(transfer.id, 'confirm')}><Check size={17} /></button><button className="icon-button danger" title="Reject transfer" onClick={() => void updateTransfer(transfer.id, 'reject')}><X size={17} /></button></div></div>{expandedTransferId === transfer.id ? <div className="transfer-side-grid"><TransferSideView label="Bank debit" side={transfer.debit} /><TransferSideView label="Wallet credit" side={transfer.credit} /></div> : null}</article>)}</div></section> : null}

    <section className="panel exception-panel">
      <div className="panel-heading"><div><p className="eyebrow">Optional review</p><h2>Exceptions only</h2></div><span>{queueQuery.data?.remaining ?? 0} open</span></div>
      <QueueFilters provider={provider} month={month} category={categoryFilter} categories={categoriesQuery.data ?? []} onProvider={setProvider} onMonth={setMonth} onCategory={setCategoryFilter} />
      <div className="exception-table-wrap"><table className="exception-table"><thead><tr><th>Date</th><th>Merchant</th><th>Reason</th><th>Type</th><th>Amount</th><th /></tr></thead><tbody>{exceptions.map((item) => <tr key={item.id}><td>{format(new Date(item.occurredAt), 'dd MMM yyyy')}</td><td><strong>{item.merchant || 'Unknown merchant'}</strong><small>{item.providerTransactionType || item.account}</small></td><td><span className="status-pill draft">{reasonLabel(item.reviewReason)}</span></td><td>{item.kind === 'TRANSFER_PENDING' ? 'Transfer pending' : titleCase(item.kind)}</td><td className={`amount-cell ${item.direction.toLowerCase()}`}>{item.direction === 'DEBIT' ? '-' : '+'}{money(item.amount)}</td><td><button className="secondary-button" onClick={() => setSelectedId(item.id)}>Review</button></td></tr>)}</tbody></table>{!exceptions.length ? <div className="review-empty compact"><ShieldCheck size={34} /><h2>No transaction exceptions</h2><p>Validated and categorized statement rows are posted automatically.</p></div> : null}</div>
    </section>

    {selected ? <section className="review-workspace selected-exception"><ExceptionForm key={selected.id} transaction={selected} document={selectedDocument} accounts={accountsQuery.data ?? []} categories={categoriesQuery.data ?? []} onSubmit={resolveException} onExclude={exclude} onClose={() => setSelectedId(null)} error={error} duplicateWarning={duplicateWarning} /></section> : null}
  </div>
}

function Outcome({ label, value, tone }: { label: string; value: number; tone: string }) { return <article className={tone}><span>{label}</span><strong>{value}</strong></article> }

function TransferSideView({ label, side }: { label: string; side: TransferSide }) { return <div className="transfer-side"><span>{label}</span><strong>{side.account}</strong><em>{format(new Date(side.occurredAt), 'dd MMM yyyy')} | {side.providerTransactionType || titleCase(side.kind)}</em><b>{side.direction === 'DEBIT' ? '-' : '+'}{money(side.amount)}</b><small>{side.merchant || 'No merchant label'}</small></div> }

function QueueFilters({ provider, month, category, categories, onProvider, onMonth, onCategory }: { provider: string; month: string; category: string; categories: Category[]; onProvider: (value: string) => void; onMonth: (value: string) => void; onCategory: (value: string) => void }) {
  return <div className="statement-filters exception-filters"><select aria-label="Provider filter" value={provider} onChange={(event) => onProvider(event.target.value)}><option value="">All providers</option><option value="PUBLIC_BANK">Public Bank</option><option value="TNG">TNG</option></select><input aria-label="Month filter" type="month" value={month} onChange={(event) => onMonth(event.target.value)} /><select aria-label="Category filter" value={category} onChange={(event) => onCategory(event.target.value)}><option value="">All categories</option>{categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div>
}

function ExceptionForm({ transaction, document, accounts, categories, onSubmit, onExclude, onClose, error, duplicateWarning }: { transaction: Transaction; document?: DocumentRecord; accounts: Account[]; categories: Category[]; onSubmit: (event: FormEvent<HTMLFormElement>, allowDuplicate?: boolean) => Promise<void>; onExclude: (reason: string) => Promise<void>; onClose: () => void; error: string; duplicateWarning: boolean }) {
  const [exclusionReason, setExclusionReason] = useState('')
  const defaultKind = transaction.kind === 'TRANSFER_PENDING' ? 'TRANSFER' : transaction.kind
  return <form className="review-form" onSubmit={(event) => void onSubmit(event)}>
    <div className="document-preview"><DocumentPreview id={transaction.documentId!} contentType={document?.contentType} page={transaction.sourcePage} /><span className="privacy-badge"><ShieldCheck size={14} /> Decrypted for this session only</span></div>
    <div className="review-fields"><div className="review-heading"><div><p className="eyebrow">Selected exception</p><h2>{transaction.merchant || 'Unrecognized transaction'}</h2><p className="source-meta">Page {transaction.sourcePage} | Row {transaction.sourceSequence} | {reasonLabel(transaction.reviewReason)}</p></div><button type="button" className="icon-button" title="Close review" onClick={onClose}><X /></button></div>
      {transaction.reviewNote ? <div className="review-warning"><AlertTriangle size={15} />{transaction.reviewNote}</div> : null}
      <div className="form-grid"><label>Account<select name="accountId" defaultValue={transaction.accountId}>{accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}</select></label><label>Category<select name="categoryId" defaultValue={transaction.categoryId}>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label><label>Direction<select name="direction" defaultValue={transaction.direction}><option value="DEBIT">Debit</option><option value="CREDIT">Credit</option></select></label><label>Type<select name="kind" defaultValue={defaultKind}><option value="EXPENSE">Expense</option><option value="INCOME">Income</option><option value="REFUND">Refund</option><option value="TRANSFER">Internal transfer</option></select></label><label>Amount<input aria-label="Amount" name="amount" type="number" step="0.01" min="0.01" defaultValue={transaction.amount} required /></label><label>Date<input name="occurredAt" type={transaction.occurredPrecision === 'DATE_ONLY' ? 'date' : 'datetime-local'} defaultValue={transaction.occurredPrecision === 'DATE_ONLY' ? format(new Date(transaction.occurredAt), 'yyyy-MM-dd') : format(new Date(transaction.occurredAt), "yyyy-MM-dd'T'HH:mm")} required /></label><label className="span-two">Merchant<input name="merchant" maxLength={160} defaultValue={transaction.merchant ?? ''} /></label><label className="span-two">Reference<input name="reference" maxLength={120} defaultValue={transaction.reference ?? ''} /></label></div>
      <label className="check-row"><input aria-label="Remember this merchant correction privately" type="checkbox" name="remember" /> Remember this merchant correction privately</label><div className="exclude-row"><select aria-label="Exclusion reason" value={exclusionReason} onChange={(event) => setExclusionReason(event.target.value)}><option value="">Select exclusion reason</option><option>Duplicate statement row</option><option>Not a financial transaction</option><option>Outside requested period</option></select><button type="button" className="secondary-button" disabled={!exclusionReason} onClick={() => void onExclude(exclusionReason)}>Exclude</button></div>{error ? <p className="form-error">{error}</p> : null}<div className="review-actions">{duplicateWarning ? <button type="button" className="danger-button" onClick={(event) => { const form = event.currentTarget.form; if (form) void onSubmit({ preventDefault() {}, currentTarget: form } as FormEvent<HTMLFormElement>, true) }}>Post as separate transaction</button> : <button className="primary-button"><CheckCircle2 size={17} /> Resolve exception</button>}</div>
    </div>
  </form>
}

function DocumentPreview({ id, contentType, page }: { id: string; contentType?: string; page: number | null }) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  useEffect(() => { let active = true; let objectUrl = ''; const request = contentType === 'application/pdf' ? downloadDocumentPage(id, page ?? 1) : downloadDocument(id); void request.then((blob) => { if (!active) return; objectUrl = URL.createObjectURL(blob); setUrl(objectUrl) }).catch((reason) => setError(reason instanceof Error ? reason.message : 'Preview unavailable.')); return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) } }, [contentType, id, page])
  if (error) return <div className="preview-message"><AlertTriangle /><p>{error}</p></div>
  if (!url) return <div className="preview-message"><RefreshCw className="spin" /><p>Decrypting preview...</p></div>
  return <img src={url} alt={contentType === 'application/pdf' ? `Statement PDF page ${page ?? 1}` : 'Uploaded transaction slip'} />
}

function money(value: number) { return new Intl.NumberFormat('en-MY', { style: 'currency', currency: 'MYR' }).format(value) }
function isSupportedImageFile(file: File) {
  return ['image/jpeg', 'image/png', 'image/webp'].includes(file.type.toLowerCase())
    || /\.(?:jpe?g|png|webp)$/i.test(file.name)
}
function formatDate(value: string) { return format(new Date(`${value}T12:00:00`), 'dd MMM yyyy') }
function titleCase(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase()) }
function reasonLabel(reason: Transaction['reviewReason']) { return reason ? titleCase(reason) : 'Needs review' }
function statusLabel(status: DocumentRecord['status']) { return ({ UPLOADED: 'Queued', PROCESSING: 'Validating', NEEDS_REVIEW: 'Needs review', PARTIALLY_POSTED: 'Partially posted', FAILED: 'Import failed', CONFIRMED: 'Posted' } as const)[status] }
