CREATE TABLE IF NOT EXISTS message_mention_targets (
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    mention_type VARCHAR(16) NOT NULL,
    target_id UUID,
    special_kind VARCHAR(16),
    PRIMARY KEY (message_id, position),
    CHECK (
        (mention_type IN ('USER', 'ROLE', 'CHANNEL') AND target_id IS NOT NULL AND special_kind IS NULL)
        OR (mention_type = 'SPECIAL' AND target_id IS NULL AND special_kind IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS message_idempotency_keys (
    author_type VARCHAR(16) NOT NULL,
    author_id UUID NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (author_type, author_id, target_type, guild_id, channel_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_message_idempotency_expires
    ON message_idempotency_keys(expires_at);

CREATE TABLE IF NOT EXISTS message_publication_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    author_type VARCHAR(16) NOT NULL,
    author_id UUID NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    guild_id UUID NOT NULL,
    channel_id UUID NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_message_publication_outbox_unpublished
    ON message_publication_outbox(occurred_at, event_id)
    WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS message_publication_outbox_mentions (
    event_id UUID NOT NULL REFERENCES message_publication_outbox(event_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    mention_type VARCHAR(16) NOT NULL,
    target_id UUID,
    special_kind VARCHAR(16),
    PRIMARY KEY (event_id, position),
    CHECK (
        (mention_type IN ('USER', 'ROLE', 'CHANNEL') AND target_id IS NOT NULL AND special_kind IS NULL)
        OR (mention_type = 'SPECIAL' AND target_id IS NULL AND special_kind IS NOT NULL)
    )
);
