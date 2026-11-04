CREATE TABLE outbox_events (
 id UUID PRIMARY KEY, aggregate_type VARCHAR(64) NOT NULL, aggregate_id UUID NOT NULL,
 event_type VARCHAR(96) NOT NULL, payload TEXT NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PUBLISHED','DEAD_LETTER')),
 attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_error VARCHAR(256),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending_due ON outbox_events(status,next_attempt_at,created_at);

CREATE TABLE received_webhooks (
 id UUID PRIMARY KEY, event_id UUID NOT NULL UNIQUE, event_type VARCHAR(96) NOT NULL,
 signature_valid BOOLEAN NOT NULL, payload TEXT NOT NULL, processed BOOLEAN NOT NULL DEFAULT false,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(), processed_at TIMESTAMPTZ
);
CREATE INDEX idx_received_webhooks_received_at ON received_webhooks(received_at DESC);
