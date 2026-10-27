# Automatic Statement Analysis Design

## Goal

Make a valid monthly statement useful immediately: post structurally sound rows,
keep uncertain categorization visible without blocking analytics, exclude recognized
wallet reloads from spending, and reserve review for evidence that can change the
accounting result.

## Posting Policy

- A statement must pass complete-document validation before any row posts.
- Extraction confidence below `0.95`, contradictory balance movement, parser
  warnings, strong duplicates, and missing-reference duplicate candidates remain
  blocking exceptions.
- `Uncategorized` is a valid provisional category, not a transaction error.
- Valid expenses, income, and refunds with no category rule post automatically as
  `Uncategorized` and remain editable later.
- Recognized bank/eWallet reload rows are excluded from expense and income totals
  immediately. Pairing improves the audit trail but is not required for analytics.

## Transfer Lifecycle

- Unique bank-to-wallet pairs continue to reconcile automatically.
- Confirming a pair rejects every competing proposal that uses either transaction.
- Proposed pairs that already conflict with a confirmed pair are rejected before
  they are returned to the UI.
- Genuine unresolved proposals show both sides with account, date, direction, and
  amount. Confirm/reject errors appear in the interface.
- Unpaired recognized reloads are informational transfer records, not review
  exceptions and not expenses.

## Existing July Repair

Flyway V4 repairs existing valid statements without re-uploading private PDFs:

- valid `UNCATEGORIZED` drafts become confirmed automatic postings;
- recognized transfer-pending rows stop contributing to exception counts;
- stale proposed transfer candidates that overlap confirmed pairs become rejected;
- document counters and posting states are recalculated from transaction state.

Invalid statements and non-category review reasons are unchanged.

## Overview

The monthly overview prioritizes:

1. net spending, gross spending, refunds, income, and internal transfers;
2. spending by category with a visible categorized-versus-uncategorized quality
   indicator;
3. top merchants and transaction count;
4. refund activity, provider coverage, and only genuinely blocking exceptions;
5. daily movement as secondary detail.

The UI must remain useful when most spending is provisional: `Uncategorized`
appears as a real chart segment rather than producing an empty state.

## Validation

- Unit tests cover valid uncategorized auto-posting, transfer-pending behavior,
  competing proposal cleanup, and analytics quality fields.
- PostgreSQL tests cover the V4 migration and constraints.
- Frontend tests cover non-empty provisional analysis, quality messaging, and
  transfer details/errors.
- The two supplied July PDFs are parsed privately; no values or documents enter Git.
- A real-stack browser check verifies July totals render and the exception count no
  longer consists of categorization-only rows.
