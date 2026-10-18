CREATE TABLE payments (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    merchant_id VARCHAR(64) NOT NULL,
    amount_minor_units BIGINT NOT NULL CHECK (amount_minor_units > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    category VARCHAR(32) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN (
        'CREATED','AUTHORIZED','DECLINED','CAPTURED','VOIDED',
        'PARTIALLY_REFUNDED','REFUNDED','EXPIRED'
    )),
    decline_reason VARCHAR(64),
    authorization_hold_id UUID REFERENCES authorization_holds(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_idempotency UNIQUE (wallet_id, idempotency_key)
);
CREATE INDEX idx_payments_wallet_created_at ON payments(wallet_id, created_at DESC);
CREATE INDEX idx_payments_status_created_at ON payments(status, created_at);
