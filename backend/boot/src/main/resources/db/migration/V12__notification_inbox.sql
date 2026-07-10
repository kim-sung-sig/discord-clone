CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    mentions_enabled BOOLEAN NOT NULL,
    direct_messages_enabled BOOLEAN NOT NULL,
    server_notifications_enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    guild_id UUID REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL,
    source_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    summary TEXT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, source_id, kind)
);

CREATE INDEX IF NOT EXISTS idx_notification_items_user_inbox
    ON notification_items(user_id, sequence DESC, created_at DESC);
