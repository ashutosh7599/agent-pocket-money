ALTER TABLE authorization_holds DROP CONSTRAINT authorization_holds_status_check;
ALTER TABLE authorization_holds ADD CONSTRAINT authorization_holds_status_check
    CHECK (status IN ('ACTIVE', 'RELEASED', 'CAPTURED', 'EXPIRED'));

ALTER TABLE payments ADD COLUMN merchant_ledger_account_id UUID REFERENCES ledger_accounts(id);
ALTER TABLE payments ADD COLUMN amount_refunded_minor_units BIGINT NOT NULL DEFAULT 0 CHECK (amount_refunded_minor_units >= 0);
ALTER TABLE payments ADD COLUMN capture_idempotency_key VARCHAR(128);
ALTER TABLE payments ADD COLUMN void_idempotency_key VARCHAR(128);

CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    journal_entry_id UUID NOT NULL UNIQUE REFERENCES journal_entries(id),
    idempotency_key VARCHAR(128) NOT NULL,
    amount_minor_units BIGINT NOT NULL CHECK (amount_minor_units > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_refund_idempotency UNIQUE(payment_id,idempotency_key)
);
CREATE INDEX idx_payment_refunds_payment_created ON payment_refunds(payment_id,created_at DESC);
