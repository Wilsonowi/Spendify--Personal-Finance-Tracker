import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const here = fileURLToPath(new URL('.', import.meta.url))
const fixtures = resolve(here, '../../fixtures')
const screenshots = resolve(here, '../../docs/screenshots')
const password = 'Synthetic-Only-2026!'

async function register(page: Page, email: string, displayName: string) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Create a local account' }).click()
  await page.getByLabel('Display name').fill(displayName)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Monthly overview' })).toBeVisible()
}

async function openImport(page: Page, filename: string) {
  const row = page.locator('.document-filter-button').filter({ hasText: filename })
  await row.click()
  await page.getByRole('button', { name: 'Review', exact: true }).click()
  await expect(page.locator('.review-form')).toBeVisible()
  return row
}

test('synthetic slips reconcile without double counting and remain owner scoped', async ({ page, browser }) => {
  const runId = Date.now()
  await page.clock.setFixedTime(new Date('2026-07-15T12:00:00+08:00'))
  const email = `demo-${runId}@spendify.local`
  await register(page, email, 'Spendify Demo')

  await page.locator('header').getByRole('link', { name: 'Import statements' }).click()
  await page.getByRole('button', { name: 'Single slip' }).click()
  await page.locator('input[type=file]').setInputFiles([
    resolve(fixtures, 'public-bank-tng-topup.png'),
    resolve(fixtures, 'tng-topup-credit.png'),
    resolve(fixtures, 'tng-chicken-rice-payment.png'),
  ])

  const documentRows = page.locator('.document-list article')
  await expect(documentRows).toHaveCount(3, { timeout: 30_000 })
  await expect(documentRows.filter({ hasText: 'Needs review' })).toHaveCount(3, { timeout: 120_000 })

  const foodRow = await openImport(page, 'tng-chicken-rice-payment.png')
  await expect(page.getByRole('heading', { name: 'X001 DAVIDS CHICK' })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.locator('.review-workspace').scrollIntoViewIfNeeded()
  await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')
  await page.screenshot({ path: resolve(screenshots, 'import-review-real-mobile.png'), fullPage: false })
  await page.setViewportSize({ width: 1440, height: 960 })
  await page.locator('.review-form').getByLabel('Category').selectOption({ label: 'Food & Dining' })
  await page.locator('.review-form').getByLabel('Merchant', { exact: true }).fill("David's Chick")
  await page.getByLabel('Remember this merchant correction privately').check()
  await page.getByRole('button', { name: 'Resolve exception' }).click()
  await expect(foodRow).toContainText('Posted')

  const creditRow = await openImport(page, 'tng-topup-credit.png')
  await expect(page.getByLabel('Direction')).toHaveValue('CREDIT')
  await expect(page.getByLabel('Amount')).toHaveValue('100')
  await page.getByRole('button', { name: 'Resolve exception' }).click()
  await expect(creditRow).toContainText('Posted')

  const debitRow = await openImport(page, 'public-bank-tng-topup.png')
  await expect(page.getByLabel('Direction')).toHaveValue('DEBIT')
  await expect(page.getByLabel('Amount')).toHaveValue('100')
  await page.getByRole('button', { name: 'Resolve exception' }).click()
  await expect(debitRow).toContainText('Posted')

  const documents = await page.evaluate(async () => {
    const response = await fetch('/api/imports', { credentials: 'include', cache: 'no-store' })
    return response.json() as Promise<Array<{ id: string }>>
  })
  expect(documents).toHaveLength(3)

  const secondContext = await browser.newContext()
  const secondPage = await secondContext.newPage()
  await register(secondPage, `other-${runId}@spendify.local`, 'Other Synthetic User')
  const foreignDocumentStatus = await secondPage.evaluate(async (documentId) => {
    return (await fetch(`/api/imports/${documentId}/content`, {
      credentials: 'include',
      cache: 'no-store',
    })).status
  }, documents[0].id)
  expect(foreignDocumentStatus).toBe(404)
  await secondContext.close()

  await expect(page.getByText('Possible bank-to-wallet transfer')).toBeVisible()
  await page.getByTitle('Confirm transfer').click()
  await page.getByRole('link', { name: 'Overview' }).click()
  await expect(page.locator('.metric.expense strong')).toContainText('12.50')
  await expect(page.locator('.metric.transfer strong')).toContainText('100.00')
  await expect(page.getByText(/exceptions need attention/)).toHaveCount(0)

  await page.getByRole('link', { name: 'Budgets' }).click()
  await page.getByLabel('Category').selectOption({ label: 'Food & Dining' })
  await page.getByLabel('Limit (MYR)').fill('300')
  await page.getByRole('button', { name: 'Save budget' }).click()
  await expect(page.locator('.budget-summary')).toContainText('Food & Dining')
  await expect(page.locator('.budget-summary')).toContainText('RM 12.50 spent')

  await page.getByRole('link', { name: 'Merchant rules' }).click()
  await expect(page.locator('.rule-list-panel')).toContainText('x001 davids chick')
  await expect(page.locator('.rule-list-panel')).toContainText("David's Chick")

  await page.getByRole('link', { name: 'Transactions' }).click()
  await page.getByRole('button', { name: 'Add entry' }).click()
  await page.getByLabel('Account').selectOption({ label: 'Public Bank' })
  await page.getByLabel('Category').selectOption({ label: 'Food & Dining' })
  await page.getByLabel('Amount').fill('1.00')
  await page.getByLabel('Date and time').fill('2026-06-15T12:00')
  await page.locator('.modal').getByLabel('Merchant', { exact: true }).fill('=2+2')
  await page.getByLabel('Reference').fill('@cmd')
  await page.getByRole('button', { name: 'Save transaction' }).click()
  await expect(page.getByText('=2+2')).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('link', { name: 'CSV' }).click()
  const download = await downloadPromise
  const csvPath = await download.path()
  expect(csvPath).not.toBeNull()
  const csv = readFileSync(csvPath!, 'utf8')
  expect(csv).toContain('"\'=2+2"')
  expect(csv).toContain('"\'@cmd"')

  await page.getByRole('link', { name: 'Imports' }).click()
  await page.getByRole('button', { name: 'Single slip' }).click()
  await page.locator('input[type=file]').setInputFiles(resolve(fixtures, 'tng-chicken-rice-payment.png'))
  await expect(page.getByText('This exact document has already been uploaded.')).toBeVisible()

  await page.getByRole('link', { name: 'Privacy' }).click()
  await expect(page.locator('.retention-list article')).toHaveCount(3)
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByTitle('Delete original').first().click()
  await expect(page.locator('.retention-list article')).toHaveCount(2)
  await page.getByRole('link', { name: 'Overview' }).click()
  await page.screenshot({ path: resolve(screenshots, 'dashboard-real.png'), fullPage: true })

  const video = page.video()
  await page.close()
  await video?.saveAs(resolve(here, '../../docs/demo/spendify-real-demo.webm'))
})
