CREATE TABLE IF NOT EXISTS message_publication_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    message_id UUID NOT NULL,
    author_type VARCHAR(16) NOT NULL,
    author_id UUID NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    guild_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    claim_token UUID,
    claimed_at TIMESTAMPTZ,
    claim_expires_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(256),
    dead_lettered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_message_publication_outbox_claimable
    ON message_publication_outbox(occurred_at, event_id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
