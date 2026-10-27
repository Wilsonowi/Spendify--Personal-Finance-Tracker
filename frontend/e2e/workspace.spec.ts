import { expect, test, type Page, type Route } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const here = fileURLToPath(new URL('.', import.meta.url))

const account = { id: 'account-tng', provider: 'TNG', name: 'TNG eWallet', type: 'EWALLET' }
const bank = { id: 'account-bank', provider: 'PUBLIC_BANK', name: 'Public Bank', type: 'BANK' }
const food = { id: 'cat-food', name: 'Food & Dining', parentId: null, system: true }
const categories = [food, { id: 'cat-transport', name: 'Transport', parentId: null, system: true }, { id: 'cat-other', name: 'Uncategorized', parentId: null, system: true }]
const transactions = [
  { id: 'tx-1', documentId: null, accountId: 'account-tng', account: 'TNG eWallet', categoryId: 'cat-food', category: 'Food & Dining', direction: 'DEBIT', kind: 'EXPENSE', amount: 12.5, currency: 'MYR', occurredAt: '2026-07-10T10:45:00Z', merchant: "David's Chick", reference: 'TNG-9001', confidence: .94, status: 'CONFIRMED', sourcePage: null, sourceSequence: null, occurredPrecision: 'EXACT', statementBalance: null, reviewNote: null, exclusionReason: null, reviewReason: null, resolutionMethod: 'AUTO_POSTED', providerTransactionType: 'Payment', refundOf: null },
  { id: 'tx-2', documentId: null, accountId: 'account-tng', account: 'TNG eWallet', categoryId: 'cat-transport', category: 'Transport', direction: 'DEBIT', kind: 'EXPENSE', amount: 8.4, currency: 'MYR', occurredAt: '2026-07-12T01:30:00Z', merchant: 'Rapid KL', reference: 'TNG-9011', confidence: .92, status: 'CONFIRMED', sourcePage: null, sourceSequence: null, occurredPrecision: 'EXACT', statementBalance: null, reviewNote: null, exclusionReason: null, reviewReason: null, resolutionMethod: 'AUTO_POSTED', providerTransactionType: 'Payment', refundOf: null },
  { id: 'tx-draft', documentId: 'doc-1', accountId: 'account-tng', account: 'TNG eWallet', categoryId: 'cat-other', category: 'Uncategorized', direction: 'DEBIT', kind: 'EXPENSE', amount: 12.5, currency: 'MYR', occurredAt: '2026-07-15T10:45:00Z', merchant: 'X001 DAVIDS CHICK', reference: 'TNG-9100', confidence: .72, status: 'DRAFT', sourcePage: 1, sourceSequence: 1, occurredPrecision: 'EXACT', statementBalance: null, reviewNote: 'Statement row needs parser verification.', exclusionReason: null, reviewReason: 'PARSER_WARNING', resolutionMethod: null, providerTransactionType: 'Payment', refundOf: null },
]

async function mockApi(page: Page) {
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url())
    let body: unknown = {}
    if (url.pathname.endsWith('/imports/doc-1/content')) {
      const image = readFileSync(resolve(here, '../../fixtures/tng-chicken-rice-payment.png'))
      await route.fulfill({ status: 200, contentType: 'image/png', body: image })
      return
    }
    if (url.pathname.endsWith('/auth/csrf')) body = { token: 'synthetic-token' }
    else if (url.pathname.endsWith('/auth/me')) body = { id: 'user-1', email: 'demo@spendify.local', displayName: 'Ian Demo' }
    else if (url.pathname.endsWith('/analytics/months')) body = { recommendedMonth: '2026-07', months: [{ month: '2026-07', partial: false, exceptionCount: 1, coverage: [{ provider: 'TNG', coveredFrom: '2026-07-01', coveredTo: '2026-07-31', partial: false }] }] }
    else if (url.pathname.endsWith('/analytics/monthly')) body = { month: '2026-07', expense: 20.9, grossExpense: 20.9, refunds: 0, netExpense: 20.9, income: 3500, transferVolume: 100, previousExpense: 26.3, expenseChangePercent: -20.5, categories: { 'Food & Dining': 12.5, Transport: 8.4 }, merchants: { "David's Chick": 12.5, 'Rapid KL': 8.4 }, budgets: [{ id: 'budget-1', categoryId: 'cat-food', category: 'Food & Dining', limit: 300, spent: 12.5, percent: 4 }], coverage: [{ provider: 'TNG', coveredFrom: '2026-07-01', coveredTo: '2026-07-31', partial: false }], partial: false, pendingExceptions: 1, transactionCount: 3, expenseCount: 2, refundCount: 0, categorizedExpense: 20.9, uncategorizedExpense: 0, categorizationPercent: 100, providerActivity: { TNG: 3 } }
    else if (url.pathname.endsWith('/transactions')) body = transactions
    else if (url.pathname.endsWith('/review-queue')) body = { items: [transactions[2]], total: 1, remaining: 1, page: 0, size: 50 }
    else if (url.pathname.endsWith('/transfers')) body = [{ id: 'transfer-1', debitTransactionId: 'debit-1', creditTransactionId: 'credit-1', score: 100, evidence: 'Exact amount; opposite directions; bank-to-wallet accounts; within 30 minutes; related references', status: 'PROPOSED', debit: { transactionId: 'debit-1', account: 'Public Bank', direction: 'DEBIT', kind: 'TRANSFER_PENDING', amount: 100, occurredAt: '2026-07-10T04:00:00Z', merchant: 'TNG Reload', providerTransactionType: 'BANK_DEBIT' }, credit: { transactionId: 'credit-1', account: 'TNG eWallet', direction: 'CREDIT', kind: 'TRANSFER_PENDING', amount: 100, occurredAt: '2026-07-10T04:02:00Z', merchant: 'Reload', providerTransactionType: 'Reload' } }]
    else if (url.pathname.endsWith('/imports/coverage')) body = []
    else if (url.pathname.endsWith('/imports')) body = [{ id: 'doc-1', name: 'tng-chicken-rice-payment.png', contentType: 'image/png', size: 26776, provider: 'TNG', kind: 'SLIP', periodStart: null, periodEnd: null, rowCount: 1, resolvedCount: 0, autoPostedCount: 0, transferCount: 0, duplicateCount: 0, exceptionCount: 1, validationStatus: 'PENDING', validationMessage: null, status: 'NEEDS_REVIEW', error: null, createdAt: '2026-07-15T10:46:00Z' }]
    else if (url.pathname.endsWith('/accounts')) body = [bank, account]
    else if (url.pathname.endsWith('/categories')) body = categories
    else if (url.pathname.endsWith('/merchant-rules')) body = []
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

test('dashboard and review workspace remain readable at desktop and mobile sizes', async ({ page }) => {
  await mockApi(page)
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Monthly overview' })).toBeVisible()
  await expect(page.locator('.metric.expense strong')).toContainText('20.90')
  await page.screenshot({ path: '../docs/screenshots/dashboard-desktop.png', fullPage: true })

  await page.goto('/imports')
  await page.getByRole('button', { name: 'Review', exact: true }).click()
  await expect(page.getByAltText('Uploaded transaction slip')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'X001 DAVIDS CHICK' })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
  await page.screenshot({ path: '../docs/screenshots/import-review-mobile.png', fullPage: false })
})
