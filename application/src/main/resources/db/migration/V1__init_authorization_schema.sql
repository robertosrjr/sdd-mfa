-- Schema inicial para Autorização de Operações com Step-Up e Idempotência Durável

CREATE TABLE operation_authorizations (
    authorization_id VARCHAR(64) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    actor_id VARCHAR(128),
    intent_version BIGINT NOT NULL,
    intent_digest VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_operation_authorizations PRIMARY KEY (authorization_id)
);

CREATE INDEX idx_op_auth_op_id ON operation_authorizations (operation_id);
CREATE INDEX idx_op_auth_subject ON operation_authorizations (subject_id);

CREATE TABLE transaction_approvals (
    approval_id VARCHAR(64) NOT NULL,
    authorization_id VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    intent_digest VARCHAR(64) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    decided_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    CONSTRAINT pk_transaction_approvals PRIMARY KEY (approval_id),
    CONSTRAINT fk_approvals_authorization FOREIGN KEY (authorization_id) REFERENCES operation_authorizations (authorization_id)
);

CREATE INDEX idx_approvals_auth_id ON transaction_approvals (authorization_id);
CREATE INDEX idx_approvals_subject ON transaction_approvals (subject_id);

CREATE TABLE idempotency_records (
    scope VARCHAR(128) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    operation_id VARCHAR(128) NOT NULL,
    business_result_ref VARCHAR(256),
    response_status INT,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (scope, key_hash)
);

CREATE INDEX idx_idempotency_expires ON idempotency_records (expires_at);

CREATE TABLE audit_events (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    actor_id VARCHAR(128),
    operation_id VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    policy_version VARCHAR(64),
    correlation_id VARCHAR(128) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    details VARCHAR(1024),
    CONSTRAINT pk_audit_events PRIMARY KEY (event_id)
);

CREATE INDEX idx_audit_timestamp ON audit_events (timestamp);
CREATE INDEX idx_audit_correlation ON audit_events (correlation_id);

