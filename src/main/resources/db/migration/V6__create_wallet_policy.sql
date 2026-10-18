ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (status IN (
 'CREATED','AUTHORIZED','PENDING_APPROVAL','DECLINED','CAPTURED','VOIDED',
 'PARTIALLY_REFUNDED','REFUNDED','EXPIRED'
));

CREATE TABLE wallet_policy_rules (
 id UUID PRIMARY KEY, wallet_id UUID NOT NULL REFERENCES wallets(id),
 rule_type VARCHAR(32) NOT NULL CHECK (rule_type IN ('MAX_TRANSACTION_AMOUNT','DAILY_SPEND_LIMIT','MONTHLY_SPEND_LIMIT','CATEGORY_BUDGET','VELOCITY_LIMIT','MANUAL_APPROVAL_THRESHOLD','MERCHANT_ALLOWLIST')),
 rule_key VARCHAR(64), amount_limit_minor_units BIGINT CHECK (amount_limit_minor_units >= 0),
 velocity_count INTEGER CHECK (velocity_count > 0), window_seconds BIGINT CHECK (window_seconds > 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_wallet_policy_rules_wallet ON wallet_policy_rules(wallet_id,rule_type);
CREATE TABLE policy_evaluations (
 id UUID PRIMARY KEY, wallet_id UUID NOT NULL REFERENCES wallets(id), payment_id UUID NOT NULL REFERENCES payments(id),
 decision VARCHAR(24) NOT NULL CHECK (decision IN ('APPROVED','DECLINED','PENDING_APPROVAL')),
 reason_code VARCHAR(64), evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_policy_evaluations_wallet_created ON policy_evaluations(wallet_id,evaluated_at DESC);
