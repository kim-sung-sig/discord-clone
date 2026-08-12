CREATE TABLE IF NOT EXISTS gateway_event_log (
    event_sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id UUID REFERENCES channels(id) ON DELETE SET NULL,
    payload_ref TEXT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_gateway_event_log_sequence
    ON gateway_event_log (event_sequence);

CREATE INDEX IF NOT EXISTS idx_gateway_event_log_expiry
    ON gateway_event_log (expires_at);

CREATE TABLE IF NOT EXISTS gateway_user_delivery (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_sequence BIGINT NOT NULL CHECK (user_sequence > 0),
    event_id UUID NOT NULL REFERENCES gateway_event_log(event_id) ON DELETE CASCADE,
    event_sequence BIGINT NOT NULL,
    stream_key VARCHAR(255) NOT NULL,
    visibility_version BIGINT NOT NULL DEFAULT 0 CHECK (visibility_version >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SKIPPED', 'DELIVERED')),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, user_sequence),
    UNIQUE (user_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_gateway_user_delivery_pending
    ON gateway_user_delivery (user_id, user_sequence)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS gateway_session_delivery (
    session_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    acknowledged_user_sequence BIGINT NOT NULL DEFAULT 0 CHECK (acknowledged_user_sequence >= 0),
    highest_granted_user_sequence BIGINT NOT NULL DEFAULT 0 CHECK (highest_granted_user_sequence >= 0),
    highest_delivered_user_sequence BIGINT NOT NULL DEFAULT 0 CHECK (highest_delivered_user_sequence >= 0),
    delivery_epoch BIGINT NOT NULL DEFAULT 1 CHECK (delivery_epoch > 0),
    owner_instance_id VARCHAR(128) NOT NULL,
    owner_lease_expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, user_id),
    CHECK (acknowledged_user_sequence <= highest_delivered_user_sequence),
    CHECK (highest_delivered_user_sequence <= highest_granted_user_sequence)
);

CREATE TABLE IF NOT EXISTS gateway_delivery_grant (
    session_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    delivery_epoch BIGINT NOT NULL CHECK (delivery_epoch > 0),
    user_sequence BIGINT NOT NULL CHECK (user_sequence > 0),
    event_id UUID NOT NULL REFERENCES gateway_event_log(event_id) ON DELETE CASCADE,
    delivery_token_hash CHAR(64) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, user_id, delivery_epoch, user_sequence)
);

CREATE INDEX IF NOT EXISTS idx_gateway_delivery_grant_session_epoch
    ON gateway_delivery_grant (session_id, user_id, delivery_epoch, user_sequence);
