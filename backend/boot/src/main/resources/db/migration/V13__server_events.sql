CREATE TABLE IF NOT EXISTS server_events (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(160) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS server_event_interested_members (
    event_id UUID NOT NULL REFERENCES server_events(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, member_id)
);

CREATE TABLE IF NOT EXISTS server_event_signals (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES server_events(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES users(id),
    type VARCHAR(64) NOT NULL,
    detail TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_server_events_guild_start
    ON server_events(guild_id, starts_at, id);

CREATE INDEX IF NOT EXISTS idx_server_event_signals_created
    ON server_event_signals(created_at DESC, id);
