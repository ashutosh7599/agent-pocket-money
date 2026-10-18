ALTER TABLE payments ADD COLUMN gateway_scenario VARCHAR(16) NOT NULL DEFAULT 'APPROVE'
    CHECK (gateway_scenario IN ('APPROVE','DECLINE','TIMEOUT','OTP_REQUIRED'));
ALTER TABLE payments ADD COLUMN gateway_payment_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN network_transaction_id VARCHAR(64);
ALTER TABLE payments ADD COLUMN issuer_response_code VARCHAR(32);
ALTER TABLE payments ADD COLUMN approval_reason VARCHAR(64);
