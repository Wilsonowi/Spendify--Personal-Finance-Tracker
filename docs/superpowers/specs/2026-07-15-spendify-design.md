# Spendify Design Specification

## Purpose

Spendify is a privacy-first personal finance application for importing Public
Bank and TNG eWallet transaction slips in batches. It extracts draft
transactions, asks the user to verify uncertain fields, reconciles internal
wallet top-ups, learns private merchant category rules, and provides monthly
budget analytics without double-counting transfers.

## Boundaries

- MYR and Asia/Kuala_Lumpur are the first-release locale.
- Public Bank and TNG eWallet are the supported source adapters.
- Original documents remain encrypted until their owner deletes them.
- OCR runs inside the local deployment and does not retain uploaded bytes.
- External AI is optional and receives sanitized merchant text only.
- Live bank APIs, financial advice, forecasting, and shared-user learning are
  excluded from the first release.

## Architecture

- React/TypeScript responsive PWA for review and analysis.
- Spring Boot modular monolith for identity, ingestion, transactions,
  reconciliation, categorization, budgets, and analytics.
- PostgreSQL as the source of truth and database-backed job queue.
- MinIO for encrypted source documents.
- Private FastAPI/PaddleOCR sidecar for text recognition.

## Correctness Rules

- Imported documents create drafts and never affect analytics before review.
- Exact file hashes are rejected; probable duplicate transactions are flagged.
- Internal transfers require explicit user confirmation and are one-to-one.
- Confirmed transfer pairs are excluded from both income and expense totals.
- Category resolution is private alias, seeded merchant, keyword rule,
  optional AI suggestion, then Uncategorized.

## Security Rules

- Passwords use Argon2id and authenticated sessions use secure cookies.
- Every source document uses AES-256-GCM with a random per-file data key.
- Authorization is checked on every document and finance resource.
- Financial APIs and documents use no-store cache policy.
- Secrets and personal fixtures are ignored and excluded from public history.

