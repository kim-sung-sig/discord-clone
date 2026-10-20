CREATE TABLE dm_channels (
    id UUID PRIMARY KEY,
    first_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    second_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (first_user_id <> second_user_id),
    UNIQUE (first_user_id, second_user_id)
);

CREATE TABLE dm_messages (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES dm_channels(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    sequence BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, sequence)
);

CREATE INDEX idx_dm_messages_channel_sequence ON dm_messages(channel_id, sequence DESC);

CREATE TABLE dm_read_markers (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL REFERENCES dm_channels(id) ON DELETE CASCADE,
    last_read_sequence BIGINT NOT NULL DEFAULT 0 CHECK (last_read_sequence >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, channel_id)
);

CREATE TABLE social_blocks (
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
