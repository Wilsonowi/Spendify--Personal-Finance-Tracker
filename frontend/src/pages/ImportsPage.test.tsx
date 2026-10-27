import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, downloadDocument, downloadDocumentPage } from '../api'
import { ImportsPage } from './ImportsPage'

vi.mock('../api', () => ({ api: vi.fn(), downloadDocument: vi.fn(), downloadDocumentPage: vi.fn() }))

const statement = {
  id: 'document-1', name: 'synthetic-bank-2026.pdf', contentType: 'application/pdf', size: 1200,
  provider: 'PUBLIC_BANK', kind: 'STATEMENT', periodStart: '2026-07-01', periodEnd: '2026-07-31',
  rowCount: 137, resolvedCount: 133, autoPostedCount: 120, transferCount: 10, duplicateCount: 3,
  exceptionCount: 4, validationStatus: 'VALID', validationMessage: 'Totals match.',
  status: 'PARTIALLY_POSTED', error: null, createdAt: '2026-08-07T00:00:00Z',
}

const transaction = {
  id: 'transaction-1', documentId: statement.id, accountId: 'account-1', account: 'Public Bank',
  categoryId: 'category-1', category: 'Uncategorized', direction: 'DEBIT', kind: 'EXPENSE',
  amount: 12.5, currency: 'MYR', occurredAt: '2026-07-02T04:00:00Z', merchant: 'Synthetic Cafe',
  reference: null, confidence: 0.97, status: 'DRAFT', sourcePage: 3, sourceSequence: 8,
  occurredPrecision: 'DATE_ONLY', statementBalance: 87.5, reviewNote: null, exclusionReason: null,
  reviewReason: 'PARSER_WARNING', resolutionMethod: null, providerTransactionType: 'BANK_DEBIT', refundOf: null,
}

const transfer = {
  id: 'transfer-1', debitTransactionId: 'debit-1', creditTransactionId: 'credit-1', score: 78,
  evidence: 'Exact amount; opposite directions; bank-to-wallet accounts; bank posted 2 days after wallet reload; top-up evidence', status: 'PROPOSED',
  debit: { transactionId: 'debit-1', account: 'Public Bank', direction: 'DEBIT', kind: 'TRANSFER_PENDING',
    amount: 100, occurredAt: '2026-07-12T04:00:00Z', merchant: 'TNG eWallet Reload', providerTransactionType: 'BANK_DEBIT' },
  credit: { transactionId: 'credit-1', account: 'TNG eWallet', direction: 'CREDIT', kind: 'TRANSFER_PENDING',
    amount: 100, occurredAt: '2026-07-10T04:00:00Z', merchant: 'Reload', providerTransactionType: 'Reload' },
}

describe('ImportsPage statement-first workflow', () => {
  beforeEach(() => {
    vi.mocked(downloadDocument).mockResolvedValue(new Blob(['synthetic'], { type: 'application/pdf' }))
    vi.mocked(downloadDocumentPage).mockResolvedValue(new Blob(['synthetic-page'], { type: 'image/png' }))
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:synthetic'), revokeObjectURL: vi.fn() })
    vi.mocked(api).mockImplementation(async (path: string) => {
      if (path === '/imports') return [statement]
      if (path.startsWith('/review-queue')) return { items: [transaction], total: 1, remaining: 4, page: 0, size: 50 }
      if (path === '/accounts') return [{ id: 'account-1', provider: 'PUBLIC_BANK', name: 'Public Bank', type: 'BANK' }]
      if (path === '/categories') return [{ id: 'category-1', name: 'Uncategorized', parentId: null, system: true }]
      if (path === '/transfers') return [transfer]
      if (path === '/transfers/transfer-1/confirm') throw new Error('This proposal is no longer available.')
      if (path === '/imports/coverage?year=2026') return [{ provider: 'PUBLIC_BANK', coveredFrom: '2026-01-01', coveredTo: '2026-07-31', expectedFrom: '2026-01-01', expectedTo: '2026-08-07', complete: false, gapStart: '2026-08-01', gapEnd: '2026-08-07' }]
      return undefined
    })
  })

  it('shows automatic outcomes and opens source preview only for a selected exception', async () => {
    const user = userEvent.setup()
    render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ImportsPage /></QueryClientProvider></MemoryRouter>)

    expect(await screen.findByText('Monthly statement import')).toBeInTheDocument()
    expect(await screen.findByText('120')).toBeInTheDocument()
    expect((await screen.findAllByText('4')).length).toBeGreaterThan(0)
    expect(await screen.findByText('Parser warning')).toBeInTheDocument()
    expect(screen.queryByAltText('Statement PDF page 3')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm and next' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Review' }))

    expect(await screen.findByAltText('Statement PDF page 3')).toHaveAttribute('src', 'blob:synthetic')
    expect(screen.getByRole('button', { name: 'Resolve exception' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Exclude' })).toBeInTheDocument()
  })

  it('expands an ambiguous transfer and reports confirmation failures', async () => {
    const user = userEvent.setup()
    const view = render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ImportsPage /></QueryClientProvider></MemoryRouter>)
    const page = within(view.container)

    await user.click(await page.findByRole('button', { name: 'Show transfer details' }))

    const transferSection = page.getByRole('heading', { name: 'Ambiguous transfers' }).closest('section')!
    expect(within(transferSection).getByText('Public Bank')).toBeInTheDocument()
    expect(within(transferSection).getByText('TNG eWallet')).toBeInTheDocument()
    expect(within(transferSection).getByText(/bank posted 2 days after wallet reload/i)).toBeInTheDocument()
    expect(within(transferSection).getAllByText(/RM\s*100\.00/)).toHaveLength(2)
    await user.click(page.getByRole('button', { name: 'Confirm transfer' }))
    expect(await page.findByText('This proposal is no longer available.')).toBeInTheDocument()
  })
})
