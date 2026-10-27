CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE financial_account (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, provider, display_name)
);

CREATE TABLE category (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    name VARCHAR(60) NOT NULL,
    parent_id UUID REFERENCES category(id),
    system_category BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_system_category_name ON category(name) WHERE user_id IS NULL;
CREATE UNIQUE INDEX uq_user_category_name ON category(user_id, name) WHERE user_id IS NOT NULL;

CREATE TABLE source_document (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    encryption_iv VARCHAR(32) NOT NULL,
    wrapped_key VARCHAR(128) NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    provider VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, sha256)
);

CREATE TABLE ingestion_job (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL UNIQUE REFERENCES source_document(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE finance_transaction (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES financial_account(id),
    document_id UUID REFERENCES source_document(id) ON DELETE SET NULL,
    category_id UUID REFERENCES category(id),
    direction VARCHAR(10) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'MYR',
    occurred_at TIMESTAMPTZ NOT NULL,
    merchant VARCHAR(160),
    reference VARCHAR(120),
    extraction_confidence NUMERIC(4,3),
    raw_evidence TEXT,
    status VARCHAR(20) NOT NULL,
    duplicate_of UUID REFERENCES finance_transaction(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_user_time ON finance_transaction(user_id, occurred_at DESC);
CREATE INDEX idx_transaction_match ON finance_transaction(user_id, amount, direction, occurred_at);

CREATE TABLE merchant_alias_rule (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    normalized_alias VARCHAR(180) NOT NULL,
    canonical_merchant VARCHAR(160) NOT NULL,
    category_id UUID NOT NULL REFERENCES category(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, normalized_alias)
);

CREATE TABLE transfer_candidate (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    debit_transaction_id UUID NOT NULL REFERENCES finance_transaction(id),
    credit_transaction_id UUID NOT NULL REFERENCES finance_transaction(id),
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    evidence VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (debit_transaction_id, credit_transaction_id)
);

CREATE UNIQUE INDEX uq_confirmed_transfer_debit
    ON transfer_candidate(debit_transaction_id) WHERE status = 'CONFIRMED';
CREATE UNIQUE INDEX uq_confirmed_transfer_credit
    ON transfer_candidate(credit_transaction_id) WHERE status = 'CONFIRMED';

CREATE TABLE budget (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES category(id),
    month_start DATE NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, category_id, month_start)
);

INSERT INTO category (id, name, system_category) VALUES
('00000000-0000-0000-0000-000000000001', 'Food & Dining', TRUE),
('00000000-0000-0000-0000-000000000002', 'Groceries', TRUE),
('00000000-0000-0000-0000-000000000003', 'Transport', TRUE),
('00000000-0000-0000-0000-000000000004', 'Bills & Utilities', TRUE),
('00000000-0000-0000-0000-000000000005', 'Shopping', TRUE),
('00000000-0000-0000-0000-000000000006', 'Health', TRUE),
('00000000-0000-0000-0000-000000000007', 'Education', TRUE),
('00000000-0000-0000-0000-000000000008', 'Entertainment', TRUE),
('00000000-0000-0000-0000-000000000009', 'Travel', TRUE),
('00000000-0000-0000-0000-000000000010', 'Fees', TRUE),
('00000000-0000-0000-0000-000000000011', 'Uncategorized', TRUE);
