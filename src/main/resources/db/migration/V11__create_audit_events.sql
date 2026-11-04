CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(48) NOT NULL,
    entity_type VARCHAR(48) NOT NULL,
    entity_id UUID,
    request_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_created_at ON audit_events(created_at DESC);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type,entity_id,created_at DESC);

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
