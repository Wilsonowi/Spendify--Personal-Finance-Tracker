import { expect, test, type Page } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const here = fileURLToPath(new URL('.', import.meta.url))
const fixtures = resolve(here, '../../fixtures/statements')
const screenshots = resolve(here, '../../docs/screenshots')
const password = 'Synthetic-Statement-2026!'

async function register(page: Page, email: string) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Create a local account' }).click()
  await page.getByLabel('Display name').fill('Statement Demo')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Monthly overview' })).toBeVisible()
}

test('synthetic statements auto-post valid rows and analyze provisional categories', async ({ page }) => {
  const runId = Date.now()
  await page.clock.setFixedTime(new Date('2026-01-15T12:00:00+08:00'))
  await register(page, `statement-${runId}@spendify.local`)

  await page.locator('header').getByRole('link', { name: 'Import statements' }).click()
  await page.locator('input[type=file]').setInputFiles([
    resolve(fixtures, 'synthetic-public-bank-2026.pdf'),
    resolve(fixtures, 'synthetic-tng-2026.pdf'),
  ])

  await expect(page.getByLabel('Analysis month')).toHaveValue('2026-01', { timeout: 60_000 })
  await expect(page.getByText('Partial coverage')).toBeVisible()
  await expect(page.locator('.metric.transfer strong')).toContainText('100.00')
  await expect(page.getByText(/exceptions need attention/)).toHaveCount(0)
  await expect(page.getByText('93% categorized')).toBeVisible()
  await expect(page.getByText(/RM\s*1\.00 awaiting category/)).toBeVisible()
  await expect(page.locator('.metric.expense strong')).toContainText('13.50')
  await expect(page.locator('.metric.transfer strong')).toContainText('100.00')
  await expect(page.getByText('X001 David Kitchen')).toBeVisible()
  await page.screenshot({ path: resolve(screenshots, 'statement-overview-real.png'), fullPage: true })

  await page.getByRole('link', { name: 'Imports' }).click()
  await expect(page.getByRole('heading', { name: 'No transaction exceptions' })).toBeVisible()

  const csv = await page.evaluate(async () => {
    const response = await fetch('/api/transactions/export.csv?from=2026-01-01&to=2026-12-31', {
      credentials: 'include', cache: 'no-store',
    })
    return response.text()
  })
  expect(csv).toContain('provider_type')
  expect(csv).toContain('resolution_method')
  expect(csv).toContain('SYN-SNACK')
  expect(csv).toContain('SYN-PAY')
})
