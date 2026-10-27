ALTER TABLE source_document
    ADD COLUMN document_kind VARCHAR(20) NOT NULL DEFAULT 'SLIP',
    ADD COLUMN period_start DATE,
    ADD COLUMN period_end DATE,
    ADD COLUMN row_count INTEGER NOT NULL DEFAULT 0 CHECK (row_count >= 0),
    ADD COLUMN resolved_count INTEGER NOT NULL DEFAULT 0 CHECK (resolved_count >= 0),
    ADD COLUMN validation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN validation_message VARCHAR(500);

ALTER TABLE source_document
    ADD CONSTRAINT chk_statement_resolution CHECK (resolved_count <= row_count);

ALTER TABLE finance_transaction
    ADD COLUMN source_page INTEGER CHECK (source_page IS NULL OR source_page > 0),
    ADD COLUMN source_sequence INTEGER CHECK (source_sequence IS NULL OR source_sequence > 0),
    ADD COLUMN date_precision VARCHAR(20) NOT NULL DEFAULT 'EXACT',
    ADD COLUMN statement_balance NUMERIC(14,2),
    ADD COLUMN review_note VARCHAR(500),
    ADD COLUMN exclusion_reason VARCHAR(120);

CREATE UNIQUE INDEX uq_transaction_document_sequence
    ON finance_transaction(document_id, source_sequence)
    WHERE document_id IS NOT NULL AND source_sequence IS NOT NULL;

CREATE INDEX idx_transaction_review_queue
    ON finance_transaction(user_id, status, occurred_at DESC);
