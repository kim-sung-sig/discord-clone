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
