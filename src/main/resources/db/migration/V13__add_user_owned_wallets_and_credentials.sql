CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Existing sandbox wallets belong to a system owner so they remain usable by
-- administrator credentials after ownership checks are introduced.
INSERT INTO app_users (id, username, active)
VALUES ('00000000-0000-0000-0000-000000000001', 'system', TRUE);

CREATE TABLE api_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'AGENT')),
    wallet_scope UUID,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    key_id VARCHAR(12) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((role = 'USER' AND wallet_scope IS NULL) OR
           (role = 'AGENT' AND wallet_scope IS NOT NULL))
);
CREATE INDEX idx_api_credentials_user ON api_credentials(user_id, created_at DESC);

ALTER TABLE wallets ADD COLUMN owner_id UUID;
UPDATE wallets SET owner_id = '00000000-0000-0000-0000-000000000001';
ALTER TABLE wallets ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE wallets ADD CONSTRAINT fk_wallet_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
CREATE INDEX idx_wallets_owner ON wallets(owner_id, created_at DESC);
