CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    ledger_account_id UUID NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (ledger_account_id, currency) REFERENCES ledger_accounts(id, currency),
    UNIQUE (id, currency)
);

CREATE TABLE wallet_top_ups (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    journal_entry_id UUID NOT NULL UNIQUE REFERENCES journal_entries(id),
    idempotency_key VARCHAR(128) NOT NULL,
    amount_minor_units BIGINT NOT NULL CHECK (amount_minor_units > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wallet_top_up_idempotency UNIQUE (wallet_id, idempotency_key),
    FOREIGN KEY (wallet_id, currency) REFERENCES wallets(id, currency)
);

CREATE INDEX idx_wallet_top_ups_created_at ON wallet_top_ups(wallet_id, created_at DESC);
