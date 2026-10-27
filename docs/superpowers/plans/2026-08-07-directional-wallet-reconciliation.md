# Directional Wallet Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match TNG reloads only to same-day or later Public Bank postings within two calendar days, automatically selecting mutually unique nearest pairs.

**Architecture:** Keep reconciliation inside the existing Spring service. Convert bank/wallet timestamps to Malaysia calendar dates, apply a directional settlement window, retain only the nearest eligible matches per transaction, and reject obsolete proposals during listing. Preserve the existing transfer API and add clearer settlement evidence for the React interface.

**Tech Stack:** Java 21, Spring Boot, JPA, JUnit 5, Mockito, React 19, TypeScript, Vitest, React Testing Library, Docker Compose.

## Global Constraints

- Date-only matching accepts Public Bank dates from the TNG reload date through two calendar days later.
- Exact-time slip matching behavior remains unchanged.
- Automatic matching requires a mutually unique nearest pair.
- Tied nearest matches remain optional proposals.
- Confirmed transfers are never rewritten.
- Personal transaction values and statement content must not enter source, fixtures, or logs.

---

### Task 1: Directional Candidate Selection

**Files:**
- Modify: `backend/src/test/java/com/wilson/spendify/finance/ReconciliationServiceTest.java`
- Modify: `backend/src/main/java/com/wilson/spendify/finance/ReconciliationService.java`

**Interfaces:**
- Consumes: `FinanceTransactionRepository.findMatchCandidates(...)`, account types, transaction direction, precision, and timestamps.
- Produces: directional candidate proposals and automatic reconciliation through existing `proposeFor`, `autoReconcile`, and `list` methods.

- [ ] **Step 1: Write failing date-direction and ranking tests**

Add tests proving that a bank debit two days after a wallet credit is eligible, an earlier bank debit is rejected, a unique nearest pair is selected over a farther equal-amount pair, and a same-delay tie remains unconfirmed.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn --batch-mode --file backend/pom.xml -Dtest=ReconciliationServiceTest test`

Expected: the earlier-bank and nearest-match assertions fail under the symmetric implementation.

- [ ] **Step 3: Implement directional matching**

Add Malaysia-calendar settlement-day calculation. Query wallet candidates from `bank date - 2 days` through `bank date`, query bank candidates from `wallet date` through `wallet date + 2 days`, reject negative or greater-than-two settlement gaps, and retain only candidates at the smallest valid gap.

- [ ] **Step 4: Reject obsolete proposals and refresh pending pairs**

During `list(userId)`, reject proposed candidates that are directionally invalid, reuse confirmed transactions, or are not a best match for both sides. Re-run automatic reconciliation for pending rows so existing July data does not require re-import.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run: `mvn --batch-mode --file backend/pom.xml -Dtest=ReconciliationServiceTest test`

Expected: all reconciliation tests pass.

### Task 2: Settlement Evidence Presentation

**Files:**
- Modify: `backend/src/test/java/com/wilson/spendify/finance/ReconciliationServiceTest.java`
- Modify: `backend/src/main/java/com/wilson/spendify/finance/ReconciliationService.java`
- Modify: `frontend/src/pages/ImportsPage.test.tsx`

**Interfaces:**
- Consumes: existing `Transfer.evidence` text returned by `GET /api/transfers`.
- Produces: evidence containing `bank posted N days after wallet reload`, rendered by the existing transfer summary.

- [ ] **Step 1: Add a failing backend evidence assertion**

Assert that a two-day delayed candidate contains `bank posted 2 days after wallet reload` instead of the generic symmetric-window description.

- [ ] **Step 2: Run the focused backend test and verify RED**

Run: `mvn --batch-mode --file backend/pom.xml -Dtest=ReconciliationServiceTest test`

Expected: the settlement explanation is absent.

- [ ] **Step 3: Generate precise backend evidence**

For date-only candidates, append `bank posted same day as wallet reload`, `bank posted 1 day after wallet reload`, or `bank posted 2 days after wallet reload`.

- [ ] **Step 4: Update and verify the frontend contract**

Update the synthetic transfer fixture to use a wallet date two days before the bank date and the new evidence wording. Assert the existing transfer summary displays that wording, then run `npm --prefix frontend test -- --run src/pages/ImportsPage.test.tsx`. Expected: the frontend contract test passes without changing the generic evidence renderer.

### Task 3: Existing Data And Full Verification

**Files:**
- Modify: `docs/statement-import.md`

**Interfaces:**
- Consumes: current private Docker database and supplied July statements.
- Produces: recalculated proposals with no directionally impossible pair.

- [ ] **Step 1: Rebuild backend and frontend containers**

Run: `docker compose up -d --build backend frontend`

- [ ] **Step 2: Trigger transfer listing and inspect privacy-safe date deltas**

Confirm every proposed candidate has `0 <= bank_date - wallet_date <= 2`, farther alternatives are absent, and uniquely mutual pairs are confirmed automatically.

- [ ] **Step 3: Run complete verification**

Run backend tests, frontend lint/tests/build, Docker health checks, and privacy scans. Expected: zero failures, all services healthy, no private files tracked.

- [ ] **Step 4: Document the settlement rule**

Update `docs/statement-import.md` to state that TNG reloads match Public Bank postings from the same day through two calendar days later, with nearest mutually unique pairs confirmed automatically.

- [ ] **Step 5: Commit and push**

Commit production and test changes with `fix: match delayed bank wallet settlements`, push `main`, and verify GitHub Actions.
