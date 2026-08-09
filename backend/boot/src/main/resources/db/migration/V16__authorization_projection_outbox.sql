CREATE TABLE IF NOT EXISTS guild_authorization_versions (
    guild_id UUID PRIMARY KEY REFERENCES guilds(id) ON DELETE CASCADE,
    permission_version BIGINT NOT NULL CHECK (permission_version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS authorization_projection_outbox (
    event_id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    event_kind VARCHAR(32) NOT NULL DEFAULT 'PROJECTION_UPDATED'
        CHECK (event_kind IN ('PROJECTION_UPDATED', 'WATERMARK_ADVANCED')),
    subject_id UUID REFERENCES users(id) ON DELETE CASCADE,
    resource_type VARCHAR(16) CHECK (resource_type IN ('GUILD', 'CHANNEL')),
    resource_id UUID,
    permission_bits BIGINT,
    permission_version BIGINT NOT NULL CHECK (permission_version >= 0),
    audience VARCHAR(32) NOT NULL CHECK (audience IN ('MESSAGE', 'WEBSOCKET', 'GATEWAY', 'NOTIFICATION')),
    correlation_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    claim_token UUID,
    claimed_until TIMESTAMPTZ
);

ALTER TABLE authorization_projection_outbox
    ADD CONSTRAINT authorization_projection_outbox_payload_ck CHECK (
        (event_kind = 'PROJECTION_UPDATED'
            AND subject_id IS NOT NULL
            AND resource_type IS NOT NULL
            AND resource_id IS NOT NULL
            AND permission_bits IS NOT NULL
            AND correlation_id IS NOT NULL)
        OR
        (event_kind = 'WATERMARK_ADVANCED'
            AND subject_id IS NULL
            AND resource_type IS NULL
            AND resource_id IS NULL
            AND permission_bits IS NULL
            AND correlation_id IS NULL)
    );

CREATE INDEX IF NOT EXISTS idx_authorization_projection_outbox_unpublished
    ON authorization_projection_outbox (occurred_at, event_id)
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_authorization_projection_outbox_guild_version
    ON authorization_projection_outbox (guild_id, permission_version, audience);

CREATE TABLE IF NOT EXISTS consumer_inbox (
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS authorization_projection (
    guild_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    resource_id UUID NOT NULL,
    permission_bits BIGINT NOT NULL,
    permission_version BIGINT NOT NULL CHECK (permission_version >= 0),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (guild_id, subject_id, resource_type, resource_id)
);

CREATE TABLE IF NOT EXISTS authorization_watermark (
    guild_id UUID PRIMARY KEY,
    permission_version BIGINT NOT NULL CHECK (permission_version >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);
