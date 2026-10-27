UPDATE finance_transaction transaction
SET status = 'CONFIRMED',
    review_reason = NULL,
    resolution_method = 'AUTO_POSTED',
    updated_at = CURRENT_TIMESTAMP
FROM source_document document
WHERE transaction.document_id = document.id
  AND document.document_kind = 'STATEMENT'
  AND document.validation_status = 'VALID'
  AND transaction.status = 'DRAFT'
  AND transaction.review_reason IN ('UNCATEGORIZED', 'LOW_CATEGORY_CONFIDENCE');

UPDATE finance_transaction transaction
SET review_reason = NULL,
    resolution_method = COALESCE(transaction.resolution_method, 'AUTO_POSTED'),
    updated_at = CURRENT_TIMESTAMP
FROM source_document document
WHERE transaction.document_id = document.id
  AND document.document_kind = 'STATEMENT'
  AND document.validation_status = 'VALID'
  AND transaction.status = 'CONFIRMED'
  AND transaction.kind = 'TRANSFER_PENDING'
  AND transaction.review_reason = 'AMBIGUOUS_TRANSFER';

UPDATE finance_transaction transaction
SET merchant = substring(
      transaction.reference
      FROM '^[^[:space:]]+[[:space:]]+(.+)[[:space:]]+[^[:space:]]+$'
    ),
    updated_at = CURRENT_TIMESTAMP
FROM source_document document
WHERE transaction.document_id = document.id
  AND document.provider = 'TNG'
  AND transaction.reference IS NOT NULL
  AND transaction.merchant = transaction.provider_transaction_type
  AND substring(
        transaction.reference
        FROM '^[^[:space:]]+[[:space:]]+(.+)[[:space:]]+[^[:space:]]+$'
      ) IS NOT NULL;

UPDATE finance_transaction transaction
SET merchant = trim(regexp_replace(
      regexp_replace(transaction.merchant, '[[:space:]]+Baki Harian.*$', '', 'i'),
      '^PB[[:space:]]+DEBIT[[:space:]]+CARD[[:space:]]+DR[[:space:]]+[^[:space:]]+[[:space:]]+',
      '',
      'i'
    )),
    updated_at = CURRENT_TIMESTAMP
FROM source_document document
WHERE transaction.document_id = document.id
  AND document.provider = 'PUBLIC_BANK'
  AND transaction.merchant IS NOT NULL;

UPDATE transfer_candidate proposed
SET status = 'REJECTED'
WHERE proposed.status = 'PROPOSED'
  AND EXISTS (
    SELECT 1
    FROM transfer_candidate confirmed
    WHERE confirmed.user_id = proposed.user_id
      AND confirmed.status = 'CONFIRMED'
      AND confirmed.id <> proposed.id
      AND (
        confirmed.debit_transaction_id IN (
          proposed.debit_transaction_id, proposed.credit_transaction_id
        )
        OR confirmed.credit_transaction_id IN (
          proposed.debit_transaction_id, proposed.credit_transaction_id
        )
      )
  );

WITH document_stats AS (
  SELECT
    transaction.document_id,
    COUNT(*) FILTER (
      WHERE transaction.status = 'CONFIRMED'
        AND transaction.resolution_method = 'AUTO_POSTED'
        AND transaction.kind NOT IN ('TRANSFER', 'TRANSFER_PENDING')
    )::INTEGER AS auto_posted_count,
    COUNT(*) FILTER (
      WHERE transaction.status = 'CONFIRMED'
        AND transaction.kind IN ('TRANSFER', 'TRANSFER_PENDING')
    )::INTEGER AS transfer_count,
    COUNT(*) FILTER (WHERE transaction.status = 'DUPLICATE')::INTEGER AS duplicate_count,
    COUNT(*) FILTER (
      WHERE transaction.status = 'DRAFT' AND transaction.review_reason IS NOT NULL
    )::INTEGER AS exception_count,
    COUNT(*) FILTER (
      WHERE transaction.status IN ('CONFIRMED', 'DUPLICATE', 'EXCLUDED')
    )::INTEGER AS resolved_count
  FROM finance_transaction transaction
  WHERE transaction.document_id IS NOT NULL
  GROUP BY transaction.document_id
)
UPDATE source_document document
SET auto_posted_count = stats.auto_posted_count,
    transfer_count = stats.transfer_count,
    duplicate_count = stats.duplicate_count,
    exception_count = stats.exception_count,
    resolved_count = stats.resolved_count,
    status = CASE
      WHEN stats.exception_count = 0 THEN 'CONFIRMED'
      WHEN stats.resolved_count > 0 THEN 'PARTIALLY_POSTED'
      ELSE 'NEEDS_REVIEW'
    END
FROM document_stats stats
WHERE document.id = stats.document_id
  AND document.document_kind = 'STATEMENT'
  AND document.validation_status = 'VALID';
