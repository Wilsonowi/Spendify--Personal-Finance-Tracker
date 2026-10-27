export type User = { id: string; email: string; displayName: string }
export type Account = { id: string; provider: string; name: string; type: 'BANK' | 'EWALLET' | 'CASH' }
export type Category = { id: string; name: string; parentId: string | null; system: boolean }
export type DocumentRecord = {
  id: string
  name: string
  contentType: string
  size: number
  provider: string | null
  kind: 'SLIP' | 'STATEMENT'
  periodStart: string | null
  periodEnd: string | null
  rowCount: number
  resolvedCount: number
  autoPostedCount: number
  transferCount: number
  duplicateCount: number
  exceptionCount: number
  validationStatus: 'PENDING' | 'VALID' | 'WARNING' | 'INVALID'
  validationMessage: string | null
  status: 'UPLOADED' | 'PROCESSING' | 'NEEDS_REVIEW' | 'PARTIALLY_POSTED' | 'FAILED' | 'CONFIRMED'
  error: string | null
  createdAt: string
}
export type Transaction = {
  id: string
  documentId: string | null
  accountId: string
  account: string
  categoryId: string
  category: string
  direction: 'DEBIT' | 'CREDIT'
  kind: 'EXPENSE' | 'INCOME' | 'TRANSFER_PENDING' | 'TRANSFER' | 'REFUND'
  amount: number
  currency: 'MYR'
  occurredAt: string
  merchant: string | null
  reference: string | null
  confidence: number | null
  status: 'DRAFT' | 'CONFIRMED' | 'DUPLICATE' | 'EXCLUDED'
  sourcePage: number | null
  sourceSequence: number | null
  occurredPrecision: 'EXACT' | 'DATE_ONLY'
  statementBalance: number | null
  reviewNote: string | null
  exclusionReason: string | null
  reviewReason: 'SLIP_REVIEW_REQUIRED' | 'DOCUMENT_VALIDATION_FAILED' | 'LOW_EXTRACTION_CONFIDENCE' | 'LOW_CATEGORY_CONFIDENCE' | 'UNCATEGORIZED' | 'PARSER_WARNING' | 'POSSIBLE_DUPLICATE' | 'AMBIGUOUS_TRANSFER' | 'CONTRADICTORY_DIRECTION' | null
  resolutionMethod: 'AUTO_POSTED' | 'AUTO_DUPLICATE' | 'AUTO_TRANSFER' | 'USER_CONFIRMED' | 'USER_EXCLUDED' | 'DUPLICATE_OVERRIDE' | 'USER_TRANSFER' | 'USER_REJECTED_TRANSFER' | null
  providerTransactionType: string | null
  refundOf: string | null
}
export type ReviewQueuePage = { items: Transaction[]; total: number; remaining: number; page: number; size: number }
export type ProviderCoverage = {
  provider: string
  coveredFrom: string
  coveredTo: string
  expectedFrom: string
  expectedTo: string
  complete: boolean
  gapStart: string | null
  gapEnd: string | null
}
export type Transfer = {
  id: string
  debitTransactionId: string
  creditTransactionId: string
  score: number
  evidence: string
  status: 'PROPOSED' | 'CONFIRMED' | 'REJECTED'
  debit: TransferSide
  credit: TransferSide
}
export type TransferSide = {
  transactionId: string
  account: string
  direction: 'DEBIT' | 'CREDIT'
  kind: 'EXPENSE' | 'INCOME' | 'TRANSFER_PENDING' | 'TRANSFER' | 'REFUND'
  amount: number
  occurredAt: string
  merchant: string | null
  providerTransactionType: string | null
}
export type BudgetProgress = {
  id: string
  categoryId: string
  category: string
  limit: number
  spent: number
  percent: number
}
export type MonthlyAnalytics = {
  month: string
  expense: number
  grossExpense: number
  refunds: number
  netExpense: number
  income: number
  transferVolume: number
  previousExpense: number
  expenseChangePercent: number
  categories: Record<string, number>
  merchants: Record<string, number>
  budgets: BudgetProgress[]
  coverage: MonthCoverage[]
  partial: boolean
  pendingExceptions: number
  transactionCount: number
  expenseCount: number
  refundCount: number
  categorizedExpense: number
  uncategorizedExpense: number
  categorizationPercent: number
  providerActivity: Record<string, number>
}
export type MonthCoverage = { provider: string; coveredFrom: string; coveredTo: string; partial: boolean }
export type MonthOption = { month: string; partial: boolean; exceptionCount: number; coverage: MonthCoverage[] }
export type MonthCatalog = { recommendedMonth: string; months: MonthOption[] }
export type MerchantRule = {
  id: string
  alias: string
  canonicalMerchant: string
  categoryId: string
}
