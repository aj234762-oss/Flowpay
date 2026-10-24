CREATE TABLE dashboard_users (
  id varchar(255) PRIMARY KEY,
  created_at timestamptz NOT NULL,
  merchant_id varchar(48) NOT NULL,
  email varchar(160) NOT NULL UNIQUE,
  password_hash varchar(100) NOT NULL,
  role varchar(16) NOT NULL
);
CREATE INDEX idx_dashboard_users_merchant ON dashboard_users(merchant_id, created_at);

CREATE TABLE dashboard_sessions (
  id varchar(255) PRIMARY KEY,
  created_at timestamptz NOT NULL,
  token_hash varchar(64) NOT NULL UNIQUE,
  user_id varchar(48) NOT NULL,
  merchant_id varchar(48) NOT NULL,
  expires_at timestamptz NOT NULL
);
CREATE INDEX idx_dashboard_sessions_expiry ON dashboard_sessions(expires_at);
