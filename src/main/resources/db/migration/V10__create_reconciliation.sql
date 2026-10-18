CREATE TABLE reconciliation_batches (
    id UUID PRIMARY KEY,
    status VARCHAR(24) NOT NULL CHECK (status IN ('MATCHED','BREAKS_FOUND')),
    report_row_count INTEGER NOT NULL CHECK (report_row_count >= 0),
    matched_count INTEGER NOT NULL CHECK (matched_count >= 0),
    break_count INTEGER NOT NULL CHECK (break_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reconciliation_batches_created_at ON reconciliation_batches(created_at DESC);

CREATE TABLE reconciliation_breaks (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES reconciliation_batches(id),
    type VARCHAR(40) NOT NULL CHECK (type IN ('PAYMENT_MISSING_FROM_REPORT','PAYMENT_NOT_FOUND','AMOUNT_MISMATCH','STATUS_MISMATCH')),
    payment_id UUID,
    report_payment_id UUID,
    expected_amount_minor_units BIGINT,
    reported_amount_minor_units BIGINT,
    expected_status VARCHAR(24),
    reported_status VARCHAR(24),
    detail VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reconciliation_breaks_batch ON reconciliation_breaks(batch_id,created_at);
