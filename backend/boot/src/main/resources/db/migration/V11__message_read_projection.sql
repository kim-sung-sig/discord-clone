CREATE TABLE IF NOT EXISTS message_read_projection (
    message_id UUID PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    mention_tokens TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    pinned BOOLEAN NOT NULL,
    deleted BOOLEAN NOT NULL,
    edited BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_read_projection_channel_cursor
    ON message_read_projection(guild_id, channel_id, created_at DESC, message_id DESC);

CREATE INDEX IF NOT EXISTS idx_message_read_projection_search
    ON message_read_projection(guild_id, channel_id, created_at DESC, message_id DESC)
    WHERE deleted = false;

INSERT INTO message_read_projection(
    message_id,
    guild_id,
    channel_id,
    author_id,
    content,
    mention_tokens,
    pinned,
    deleted,
    edited,
    created_at,
    updated_at
)
SELECT
    message.id,
    message.guild_id,
    message.channel_id,
    message.author_id,
    message.content,
    COALESCE(mentions.tokens, ARRAY[]::TEXT[]),
    message.pinned,
    message.deleted,
    message.edited,
    message.created_at,
    message.updated_at
FROM messages message
LEFT JOIN LATERAL (
    SELECT array_agg(
        CASE mention.mention_type
            WHEN 'USER' THEN mention.target_id::text
            WHEN 'ROLE' THEN mention.target_id::text
            WHEN 'CHANNEL' THEN mention.target_id::text
            WHEN 'SPECIAL' THEN lower(mention.special_kind)
        END
        ORDER BY mention.position
    ) AS tokens
    FROM message_mention_targets mention
    WHERE mention.message_id = message.id
) mentions ON true
ON CONFLICT (message_id) DO NOTHING;
