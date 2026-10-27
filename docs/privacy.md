# Privacy Design

## Protected Data

- Uploaded bank and eWallet documents.
- Account ownership, transaction timestamps, amounts, merchants, and references.
- OCR text and extraction evidence.
- User-created merchant aliases and budgets.

## Controls

- Authenticated server-side sessions; no token is stored in browser storage.
- CSRF cookie/header protection for every state-changing request.
- User ownership checks at repository boundaries.
- Magic-byte upload validation and 10 MB per-file limit.
- Exact-hash duplicate checks scoped per user.
- AES-256-GCM content encryption and AES key wrapping.
- Internal-only database, object-storage, and OCR networks.
- Sanitized errors and evidence; full OCR output is not stored in transactions.
- `no-store` responses and static-shell-only PWA caching.

## Key Limitation

The local master key and encrypted object data must be backed up together.
Losing the key makes stored originals unrecoverable. Anyone controlling the
running backend process can access the key and request decrypted files, so host
security remains part of the trust model.

## Public Release Gate

Before any GitHub push, scan tracked files and Git history for `.env` data, real
slips, account numbers, API keys, generated databases, logs, object-store data,
and screenshots containing private transactions.

