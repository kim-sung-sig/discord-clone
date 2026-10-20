CREATE TABLE IF NOT EXISTS mfa_totp (
    user_id VARCHAR(36) PRIMARY KEY,
    secret_ciphertext VARCHAR(512) NOT NULL,
    confirmation_expires_at BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    consumed_step BIGINT NOT NULL,
    failures INTEGER NOT NULL,
    challenge_id VARCHAR(36),
    challenge_created_at BIGINT,
    challenge_expires_at BIGINT,
    pending_secret_ciphertext VARCHAR(512),
    pending_expires_at BIGINT
);

-- Non-destructive upgrade for existing local MFA databases.
ALTER TABLE mfa_totp ADD COLUMN IF NOT EXISTS webauthn_started_at BIGINT;
ALTER TABLE mfa_totp ADD COLUMN IF NOT EXISTS pending_secret_ciphertext VARCHAR(512);
ALTER TABLE mfa_totp ADD COLUMN IF NOT EXISTS pending_expires_at BIGINT;

-- Spring Security 7.1.1 native JDBC repository contract (PostgreSQL / H2).
CREATE TABLE IF NOT EXISTS user_entities (
    id VARCHAR(1000) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(200)
);
CREATE TABLE IF NOT EXISTS user_credentials (
    credential_id VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id VARCHAR(1000) NOT NULL REFERENCES user_entities(id),
    public_key BYTEA NOT NULL,
    signature_count BIGINT,
    uv_initialized BOOLEAN,
    backup_eligible BOOLEAN NOT NULL,
    authenticator_transports VARCHAR(1000),
    public_key_credential_type VARCHAR(100),
    backup_state BOOLEAN NOT NULL,
    attestation_object BYTEA,
    attestation_client_data_json BYTEA,
    created TIMESTAMP,
    last_used TIMESTAMP,
    label VARCHAR(1000) NOT NULL
);
CREATE INDEX IF NOT EXISTS user_credentials_owner ON user_credentials(user_entity_user_id);
