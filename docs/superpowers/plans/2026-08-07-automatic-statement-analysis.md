# Automatic Statement Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn valid monthly statements into immediate expense analysis while keeping only accounting-risk rows in the exception queue.

**Architecture:** Keep whole-document validation as the trust boundary, but separate transaction posting from categorization certainty. Reconciliation owns transfer-candidate cleanup, Flyway repairs existing imports, and the monthly analytics API exposes data-quality fields consumed by a refined React overview.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, PostgreSQL 18, React 19, TypeScript, TanStack Query, Recharts, JUnit, Vitest, Playwright.

## Global Constraints

- Real statements and financial values remain outside Git and test output.
- External AI remains disabled.
- Invalid documents post no rows.
- Recognized internal reloads never affect expenses or income.
- Existing REST endpoints remain compatible; response types may gain fields.

---

### Task 1: Automatic Posting Policy

**Files:**
- Modify: `backend/src/main/java/com/wilson/spendify/ingestion/StatementIngestionService.java`
- Modify: `backend/src/main/java/com/wilson/spendify/finance/FinanceTransaction.java`
- Test: `backend/src/test/java/com/wilson/spendify/ingestion/StatementIngestionServiceTest.java`

**Interfaces:**
- Consumes: `CategorizationService.Suggestion`, `StatementParseResult`
- Produces: valid uncategorized rows with `status=CONFIRMED`, `resolutionMethod=AUTO_POSTED`, and provisional category metadata

- [ ] Add a failing test proving a valid unknown merchant posts automatically.
- [ ] Add a failing test proving a recognized unmatched reload is excluded without entering the review queue.
- [ ] Run the focused tests and verify both fail for the current category gate.
- [ ] Change the posting policy so categorization confidence does not create a blocking review reason.
- [ ] Recalculate document outcome counts for posted rows and informational transfers.
- [ ] Run focused tests and commit the passing behavior.

### Task 2: Transfer Candidate Integrity

**Files:**
- Modify: `backend/src/main/java/com/wilson/spendify/finance/ReconciliationService.java`
- Modify: `backend/src/main/java/com/wilson/spendify/finance/TransferCandidateRepository.java`
- Modify: `backend/src/main/java/com/wilson/spendify/finance/FinanceController.java`
- Test: `backend/src/test/java/com/wilson/spendify/finance/ReconciliationServiceTest.java`

**Interfaces:**
- Produces: `TransferDetails` for both sides and a list containing only actionable proposals
- Enforces: one confirmed candidate per debit and credit transaction

- [ ] Add a failing test proving confirmation rejects competing proposals.
- [ ] Add a failing test proving list cleanup rejects proposals that conflict with an existing confirmed pair.
- [ ] Run tests and verify the stale-candidate behavior fails.
- [ ] Add repository queries for candidates sharing either transaction.
- [ ] Reject competing candidates in confirmation and automatic reconciliation.
- [ ] Return transaction details for each candidate and run focused tests.

### Task 3: Existing Import Repair

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__automatic_statement_analysis.sql`
- Modify: `backend/src/test/java/com/wilson/spendify/finance/PostgresContractTest.java`

**Interfaces:**
- Consumes: V3 transaction audit fields and source-document counters
- Produces: repaired valid statement rows and consistent counters/statuses

- [ ] Add a PostgreSQL test fixture with a valid statement, uncategorized draft,
  unmatched reload, and stale proposal.
- [ ] Verify the expected repaired state fails before V4 logic is applied.
- [ ] Add idempotent SQL updates scoped to valid statement documents.
- [ ] Recalculate each source document from its derived transaction rows.
- [ ] Run Testcontainers migration and ownership tests.

### Task 4: Monthly Analytics Quality

**Files:**
- Modify: `backend/src/main/java/com/wilson/spendify/finance/FinanceService.java`
- Test: `backend/src/test/java/com/wilson/spendify/finance/FinanceServiceStatementTest.java`

**Interfaces:**
- Extends: `MonthlyAnalytics`
- Produces: `transactionCount`, `categorizedExpense`, `uncategorizedExpense`, `refundCount`, and `providerActivity`

- [ ] Add a failing analytics test for provisional uncategorized spending and counts.
- [ ] Compute quality fields from confirmed monthly rows without changing net-expense semantics.
- [ ] Run focused analytics tests.

### Task 5: Overview And Transfer UI

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/pages/DashboardPage.tsx`
- Modify: `frontend/src/pages/ImportsPage.tsx`
- Modify: `frontend/src/index.css`
- Test: `frontend/src/pages/DashboardPage.test.tsx`
- Test: `frontend/src/pages/ImportsPage.test.tsx`

**Interfaces:**
- Consumes: extended `MonthlyAnalytics` and transfer-side details
- Produces: useful provisional overview and expandable actionable transfer rows

- [ ] Add failing component tests for uncategorized spending, quality messaging,
  transaction/refund activity, transfer details, and API error feedback.
- [ ] Update TypeScript contracts.
- [ ] Refine the overview hierarchy and responsive layout.
- [ ] Make genuine ambiguous matches expandable and show both transaction sides.
- [ ] Handle confirm/reject failures without unhandled promises.
- [ ] Run Vitest, lint, and production build.

### Task 6: End-To-End Acceptance

**Files:**
- Modify: `frontend/e2e-real/statement-workflow.spec.ts`
- Modify: `README.md`
- Modify: `docs/statement-import.md`

**Interfaces:**
- Verifies: upload to useful monthly analysis without categorization review

- [ ] Update the synthetic real-stack workflow for automatic uncategorized posting.
- [ ] Run backend, Testcontainers, frontend, OCR regression, and Playwright suites.
- [ ] Rebuild Docker from V3 data and verify V4 repairs the current July imports.
- [ ] Privately compare the two supplied July PDFs with posted row counts and totals.
- [ ] Run Gitleaks and personal-data scans.
- [ ] Commit, merge to `main`, push, and verify the healthy local application.
