ALTER TABLE idempotency_records ADD COLUMN authorization_id VARCHAR(64);
ALTER TABLE idempotency_records ADD COLUMN owner_token VARCHAR(64);
ALTER TABLE idempotency_records ADD COLUMN lease_until TIMESTAMP;
CREATE INDEX idx_idempotency_state_lease ON idempotency_records(state, lease_until);
