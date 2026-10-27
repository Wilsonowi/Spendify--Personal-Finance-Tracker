# Directional Wallet Reconciliation Design

## Purpose

Correct bank-to-wallet reconciliation so statement posting delays are represented accurately. A TNG reload is visible immediately, while the corresponding Public Bank debit can appear later after settlement. Matching must therefore be directional instead of using a symmetric date window.

## Matching Rule

For date-only statement transactions, a candidate is eligible only when:

- the wallet side is a credit and the bank side is a debit;
- the amount is equal;
- the accounts form a bank-to-eWallet pair;
- at least one side contains wallet reload or top-up evidence; and
- the Public Bank date is the same as, one day after, or two days after the TNG reload date.

A Public Bank date before the wallet reload is not eligible. Exact-time slip matching keeps its existing 30-minute behavior.

## Selection And Ambiguity

Eligible date-only candidates are ranked by settlement delay:

1. same-day bank posting;
2. bank posting one day later;
3. bank posting two days later.

Automatic reconciliation requires a mutually unique nearest match. Both sides must select each other at the same best delay. If two candidates share the same best delay, neither is automatically confirmed and the tied choices remain available for optional review.

Candidates at a worse delay than a unique nearest candidate are not presented. This avoids multiple low-value proposals when the same reload amount occurs repeatedly.

## Existing Data

When transfer candidates are listed, proposed date-only candidates that violate the directional rule or are no longer the best eligible match are rejected. Confirmed transfers remain unchanged to preserve user decisions and audit history.

After deployment, the current July proposals will be recalculated from pending transfer rows. No statement re-upload is required.

## Interface

Each optional transfer displays both transaction dates and a settlement explanation such as `Bank posted 2 days after wallet reload`. The existing expand, confirm, and reject controls remain unchanged.

## Testing

- Accept same-day, one-day-later, and two-day-later bank postings.
- Reject bank postings before the wallet reload.
- Reject bank postings more than two days later.
- Prefer the unique nearest later bank posting over farther candidates.
- Preserve optional review when best candidates tie.
- Keep exact-time slip matching behavior unchanged.
- Reject obsolete proposed candidates without changing confirmed transfers.
- Validate the behavior against the private July statements without logging financial details.

## Boundaries

- The two-calendar-day limit remains unchanged.
- Weekend-specific calendars and configurable provider settlement windows are outside this change.
- No personal transaction values or statement content are committed or logged.
