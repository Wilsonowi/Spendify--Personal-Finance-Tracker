ALTER TABLE source_document
    ADD COLUMN auto_posted_count INTEGER NOT NULL DEFAULT 0 CHECK (auto_posted_count >= 0),
    ADD COLUMN exception_count INTEGER NOT NULL DEFAULT 0 CHECK (exception_count >= 0),
    ADD COLUMN duplicate_count INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
    ADD COLUMN transfer_count INTEGER NOT NULL DEFAULT 0 CHECK (transfer_count >= 0);

ALTER TABLE finance_transaction
    ADD COLUMN categorization_confidence NUMERIC(4,3),
    ADD COLUMN categorization_source VARCHAR(30),
    ADD COLUMN review_reason VARCHAR(40),
    ADD COLUMN resolution_method VARCHAR(40),
    ADD COLUMN provider_transaction_type VARCHAR(80),
    ADD COLUMN reference_fingerprint VARCHAR(64),
    ADD COLUMN refund_of UUID REFERENCES finance_transaction(id) ON DELETE SET NULL;

CREATE INDEX idx_transaction_reference_fingerprint
    ON finance_transaction(user_id, account_id, reference_fingerprint)
    WHERE reference_fingerprint IS NOT NULL;

CREATE INDEX idx_transaction_monthly_analytics
    ON finance_transaction(user_id, status, occurred_at, kind, direction);

CREATE INDEX idx_transaction_review_reason
    ON finance_transaction(user_id, review_reason, occurred_at DESC)
    WHERE review_reason IS NOT NULL;

ALTER TABLE finance_transaction DROP CONSTRAINT finance_transaction_document_id_fkey;
ALTER TABLE finance_transaction
    ADD CONSTRAINT finance_transaction_document_id_fkey
    FOREIGN KEY (document_id) REFERENCES source_document(id) ON DELETE CASCADE;

ALTER TABLE finance_transaction DROP CONSTRAINT finance_transaction_duplicate_of_fkey;
ALTER TABLE finance_transaction
    ADD CONSTRAINT finance_transaction_duplicate_of_fkey
    FOREIGN KEY (duplicate_of) REFERENCES finance_transaction(id) ON DELETE SET NULL;

ALTER TABLE transfer_candidate DROP CONSTRAINT transfer_candidate_debit_transaction_id_fkey;
ALTER TABLE transfer_candidate
    ADD CONSTRAINT transfer_candidate_debit_transaction_id_fkey
    FOREIGN KEY (debit_transaction_id) REFERENCES finance_transaction(id) ON DELETE CASCADE;

ALTER TABLE transfer_candidate DROP CONSTRAINT transfer_candidate_credit_transaction_id_fkey;
ALTER TABLE transfer_candidate
    ADD CONSTRAINT transfer_candidate_credit_transaction_id_fkey
    FOREIGN KEY (credit_transaction_id) REFERENCES finance_transaction(id) ON DELETE CASCADE;
