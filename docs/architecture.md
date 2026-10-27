# Architecture

## Modules

| Module | Responsibility | Depends on |
|---|---|---|
| Identity | Registration, authentication, user boundary | PostgreSQL sessions |
| Ingestion | Validation, hashing, encryption, upload jobs, statement parsing | MinIO, PostgreSQL, PDFBox |
| OCR | Ephemeral text recognition | PaddleOCR |
| Finance | Automatic posting, exceptions, refunds, manual records | PostgreSQL |
| Categorization | Private aliases, seeded keywords, optional AI | Finance categories |
| Reconciliation | Unique auto-matches and explainable ambiguous proposals | Finance accounts |
| Analytics | Net monthly totals, refunds, categories, merchants, budgets | Posted transactions |

## Ingestion States

```mermaid
stateDiagram-v2
    [*] --> UPLOADED
    UPLOADED --> PROCESSING
    PROCESSING --> CONFIRMED: all rows resolved
    PROCESSING --> PARTIALLY_POSTED: clean rows plus exceptions
    PROCESSING --> NEEDS_REVIEW: exceptions only
    PROCESSING --> FAILED
    PARTIALLY_POSTED --> CONFIRMED: exceptions resolved
    NEEDS_REVIEW --> CONFIRMED
    FAILED --> [*]: delete or enter manually
    CONFIRMED --> [*]
```

Documents and transactions have separate states. A slip creates one draft. A
statement creates ordered rows after complete-document validation. Structurally
sound rows post automatically; an unknown category remains a visible,
provisional `Uncategorized` value rather than blocking the ledger. Suspicious,
contradictory, or potentially duplicated rows remain exceptions.
`PARTIALLY_POSTED` means posted rows are already available while exceptions stay
outside analytics. Invalid documents post nothing. Analytics query only
`CONFIRMED` transactions.

## Data Model

```mermaid
erDiagram
    APP_USER ||--o{ FINANCIAL_ACCOUNT : owns
    APP_USER ||--o{ SOURCE_DOCUMENT : uploads
    SOURCE_DOCUMENT ||--|| INGESTION_JOB : queues
    SOURCE_DOCUMENT ||--o{ FINANCE_TRANSACTION : supports
    FINANCIAL_ACCOUNT ||--o{ FINANCE_TRANSACTION : records
    CATEGORY ||--o{ FINANCE_TRANSACTION : classifies
    APP_USER ||--o{ MERCHANT_ALIAS_RULE : learns
    CATEGORY ||--o{ MERCHANT_ALIAS_RULE : resolves
    FINANCE_TRANSACTION ||--o{ TRANSFER_CANDIDATE : debit
    FINANCE_TRANSACTION ||--o{ TRANSFER_CANDIDATE : credit
    CATEGORY ||--o{ BUDGET : limits
```

Money uses `NUMERIC(14,2)`. Confirmed transfer indexes prevent a transaction
from joining more than one confirmed pair. `(document_id, source_sequence)` is
unique, and statement resolution cannot exceed its parsed row count.
