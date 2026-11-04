ALTER TABLE payments ADD COLUMN approval_decision_key VARCHAR(128);
ALTER TABLE payments ADD COLUMN approval_decision VARCHAR(16)
    CHECK (approval_decision IN ('APPROVED','REJECTED'));

CREATE TABLE spend_mandates (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    purpose VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    max_amount_minor_units BIGINT NOT NULL CHECK (max_amount_minor_units > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    category VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','CONSUMED')),
    consumed_payment_id UUID UNIQUE REFERENCES payments(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((status = 'ACTIVE' AND consumed_payment_id IS NULL) OR
           (status = 'CONSUMED' AND consumed_payment_id IS NOT NULL))
);
CREATE INDEX idx_spend_mandates_wallet_status ON spend_mandates(wallet_id,status,expires_at);
