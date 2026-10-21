CREATE TABLE merchants (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  name varchar(100) NOT NULL, email varchar(160) NOT NULL,
  apikey_hash varchar(64) NOT NULL, webhook_secret varchar(100) NOT NULL,
  webhook_url varchar(500) NOT NULL, balance bigint NOT NULL
);
CREATE UNIQUE INDEX uk_merchants_apikey_hash ON merchants(apikey_hash);

CREATE TABLE orders (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, amount bigint NOT NULL,
  currency varchar(3) NOT NULL, receipt varchar(100) NOT NULL, status varchar(20) NOT NULL
);
CREATE INDEX idx_orders_merchant_create_at ON orders(merchant_id, create_at);

CREATE TABLE payments (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, order_id varchar(48) NOT NULL,
  idempotency_key varchar(100) NOT NULL, method varchar(20) NOT NULL,
  scenario varchar(30) NOT NULL, status varchar(20) NOT NULL,
  amount bigint NOT NULL, fee bigint NOT NULL, refunded bigint NOT NULL, resolve_at timestamptz,
  CONSTRAINT uk_payments_merchant_idempotency UNIQUE(merchant_id, idempotency_key)
);
CREATE INDEX idx_payments_due ON payments(status, scenario, resolve_at);
CREATE INDEX idx_payments_merchant_create_at ON payments(merchant_id, create_at);

CREATE TABLE refunds (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, payment_id varchar(48) NOT NULL,
  idempotency_key varchar(100) NOT NULL, amount bigint NOT NULL, status varchar(20) NOT NULL,
  CONSTRAINT uk_refunds_merchant_idempotency UNIQUE(merchant_id, idempotency_key)
);
CREATE TABLE settlements (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, idempotency_key varchar(100) NOT NULL,
  amount bigint NOT NULL, status varchar(20) NOT NULL,
  CONSTRAINT uk_settlements_merchant_idempotency_key UNIQUE(merchant_id, idempotency_key)
);
CREATE TABLE transactions (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, reference_id varchar(48) NOT NULL,
  type varchar(20) NOT NULL, amount bigint NOT NULL, balance_after bigint NOT NULL
);
CREATE INDEX idx_transactions_merchant_create_at ON transactions(merchant_id, create_at);
CREATE INDEX idx_transactions_reference_type ON transactions(reference_id, type);

CREATE TABLE events (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, type varchar(60) NOT NULL,
  payload text NOT NULL, published_at timestamptz, consumed_at timestamptz
);
CREATE INDEX idx_events_pending ON events(published_at, create_at);

CREATE TABLE webhooks (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  event_id varchar(48) NOT NULL UNIQUE, merchant_id varchar(48) NOT NULL,
  url varchar(500) NOT NULL, signing_secret varchar(100) NOT NULL,
  event_type varchar(60) NOT NULL, payload text NOT NULL, signature varchar(71) NOT NULL,
  status varchar(20) NOT NULL, last_error varchar(500), attempt_count integer NOT NULL,
  response_code integer, next_attempt timestamptz, delivered_at timestamptz
);
CREATE INDEX idx_webhooks_due ON webhooks(status, next_attempt);
CREATE INDEX idx_webhooks_merchant_create_at ON webhooks(merchant_id, create_at);

CREATE TABLE checkout_sessions (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL, order_id varchar(48) NOT NULL,
  token varchar(80) NOT NULL UNIQUE, status varchar(20) NOT NULL,
  payment_id varchar(48), expires_at timestamptz NOT NULL
);
CREATE INDEX idx_checkout_sessions_merchant ON checkout_sessions(merchant_id, create_at);

CREATE TABLE reconciliation_issues (
  id varchar(255) PRIMARY KEY, create_at timestamptz NOT NULL,
  issue_key varchar(140) NOT NULL UNIQUE, merchant_id varchar(48) NOT NULL,
  reference_id varchar(48) NOT NULL, issue_type varchar(60) NOT NULL,
  status varchar(20) NOT NULL, details varchar(500) NOT NULL,
  expected_amount bigint, actual_amount bigint
);
CREATE INDEX idx_reconciliation_merchant_status ON reconciliation_issues(merchant_id, status);
