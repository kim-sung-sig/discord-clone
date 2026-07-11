CREATE TABLE threads (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL,
    parent_channel_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    archived BOOLEAN NOT NULL,
    auto_archive_minutes INTEGER NOT NULL CHECK (auto_archive_minutes > 0),
    last_activity_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE thread_messages (
    id UUID PRIMARY KEY,
    thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE forum_tags (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL,
    forum_channel_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE thread_post_tags (
    thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES forum_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (thread_id, tag_id)
);

CREATE INDEX threads_expiration_idx ON threads (archived, last_activity_at);
