import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { api } from '../api'
import { DashboardPage } from './DashboardPage'

vi.mock('../api', () => ({ api: vi.fn() }))

function LocationProbe() { return <output aria-label="location">{useLocation().search}</output> }

beforeEach(() => {
  vi.mocked(api).mockImplementation(async (path: string) => {
    if (path === '/analytics/months') return { recommendedMonth: '2026-07', months: [
      { month: '2026-08', partial: true, exceptionCount: 1, coverage: [] },
      { month: '2026-07', partial: false, exceptionCount: 0, coverage: [{ provider: 'PUBLIC_BANK', coveredFrom: '2026-07-01', coveredTo: '2026-07-31', partial: false }] },
    ] }
    if (path === '/analytics/monthly?month=2026-07') return {
      month: '2026-07', expense: 90, grossExpense: 100, refunds: 10, netExpense: 90,
      income: 500, transferVolume: 100, previousExpense: 80, expenseChangePercent: 12.5,
      categories: { 'Food & Dining': 60, Transport: 30 }, merchants: { 'Synthetic Cafe': 60, 'Transit Rail': 30 }, budgets: [],
      coverage: [{ provider: 'PUBLIC_BANK', coveredFrom: '2026-07-01', coveredTo: '2026-07-31', partial: false }], partial: false, pendingExceptions: 0,
      transactionCount: 3, expenseCount: 2, refundCount: 1, categorizedExpense: 95,
      uncategorizedExpense: 5, categorizationPercent: 95, providerActivity: { TNG: 3 },
    }
    if (path === '/transactions') return []
    return undefined
  })
})

it('opens the recommended closed month and prioritizes monthly category and merchant analysis', async () => {
  render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DashboardPage /><LocationProbe /></QueryClientProvider></MemoryRouter>)

  expect((await screen.findAllByText('July 2026')).length).toBeGreaterThan(0)
  await waitFor(() => expect(screen.getByLabelText('location')).toHaveTextContent('month=2026-07'))
  expect(screen.getByText('Net monthly spending')).toBeInTheDocument()
  expect(screen.getByText(/RM\s*90\.00/)).toBeInTheDocument()
  expect(screen.getByText(/RM\s*10\.00 refunded/)).toBeInTheDocument()
  expect(screen.getByText('Spending by category')).toBeInTheDocument()
  expect(screen.getByText('Top merchants')).toBeInTheDocument()
  expect(screen.getByText('Synthetic Cafe')).toBeInTheDocument()
  expect(screen.getByText('3 transactions analyzed')).toBeInTheDocument()
  expect(screen.getByText('2 expenses')).toBeInTheDocument()
  expect(screen.getByText('1 refund')).toBeInTheDocument()
  expect(screen.getByText('95% categorized')).toBeInTheDocument()
  expect(screen.getByText(/RM\s*5\.00 awaiting category/)).toBeInTheDocument()
  expect(screen.getByText('TNG')).toBeInTheDocument()
  expect(screen.getByText('3 transactions')).toBeInTheDocument()
})
