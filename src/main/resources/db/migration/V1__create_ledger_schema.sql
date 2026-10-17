CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    account_type VARCHAR(16) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, currency)
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    transaction_reference VARCHAR(128) NOT NULL,
    description VARCHAR(500) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_journal_entries_transaction_reference ON journal_entries(transaction_reference);
CREATE INDEX idx_journal_entries_posted_at ON journal_entries(posted_at);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    account_id UUID NOT NULL,
    side VARCHAR(6) NOT NULL CHECK (side IN ('DEBIT', 'CREDIT')),
    amount_minor_units BIGINT NOT NULL CHECK (amount_minor_units > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    FOREIGN KEY (account_id, currency) REFERENCES ledger_accounts(id, currency)
);

CREATE INDEX idx_journal_lines_account_id ON journal_lines(account_id);
CREATE INDEX idx_journal_lines_entry_id ON journal_lines(journal_entry_id);

-- Posting records and their lines are immutable; corrections must be new reversal entries.
CREATE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Ledger rows are append-only; post a reversal instead'
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER journal_lines_append_only
    BEFORE UPDATE OR DELETE ON journal_lines
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

-- Deferred until commit so the entry and all its lines can be inserted in one transaction.
CREATE FUNCTION assert_journal_entry_balanced() RETURNS trigger AS $$
DECLARE
    target_entry_id UUID;
    line_count BIGINT;
    debit_total NUMERIC;
    credit_total NUMERIC;
    currency_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'journal_entries' THEN
        target_entry_id := NEW.id;
    ELSE
        target_entry_id := NEW.journal_entry_id;
    END IF;

    SELECT count(*),
           coalesce(sum(amount_minor_units) FILTER (WHERE side = 'DEBIT'), 0),
           coalesce(sum(amount_minor_units) FILTER (WHERE side = 'CREDIT'), 0),
           count(DISTINCT currency)
      INTO line_count, debit_total, credit_total, currency_count
      FROM journal_lines
     WHERE journal_entry_id = target_entry_id;

    IF line_count < 2 OR debit_total <> credit_total OR currency_count <> 1 THEN
        RAISE EXCEPTION 'Journal entry % must have at least two lines with equal debits and credits in one currency', target_entry_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_entry_balance_check
    AFTER INSERT ON journal_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_journal_entry_balanced();

CREATE CONSTRAINT TRIGGER journal_lines_balance_check
    AFTER INSERT ON journal_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_journal_entry_balanced();
