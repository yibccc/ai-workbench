CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL CHECK (btrim(username) <> ''),
    password_hash TEXT NOT NULL,
    role VARCHAR(8) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_version BIGINT NOT NULL DEFAULT 0 CHECK (auth_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_user_accounts_username_ci ON user_accounts (lower(username));
CREATE INDEX idx_user_accounts_created_at ON user_accounts (created_at, id);
