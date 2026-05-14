CREATE TABLE IF NOT EXISTS auth_revoked_access_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
