CREATE TABLE authorization_holds (
 id UUID PRIMARY KEY, wallet_id UUID NOT NULL REFERENCES wallets(id),
 amount_minor_units BIGINT NOT NULL CHECK (amount_minor_units > 0),
 status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','RELEASED','EXPIRED')),
 expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_authorization_holds_wallet_active_expiry ON authorization_holds(wallet_id,status,expires_at);
CREATE INDEX idx_authorization_holds_expiry ON authorization_holds(status,expires_at);
