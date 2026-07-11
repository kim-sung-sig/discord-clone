CREATE TABLE custom_emojis (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL,
    name VARCHAR(32) NOT NULL,
    image_object_key TEXT NOT NULL,
    creator_id UUID NOT NULL,
    UNIQUE (guild_id, name)
);

CREATE TABLE stickers (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL,
    name VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    creator_id UUID NOT NULL,
    UNIQUE (guild_id, name)
);

CREATE TABLE message_reactions (
    channel_id UUID NOT NULL,
    message_id UUID NOT NULL,
    emoji_key VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (channel_id, message_id, emoji_key, user_id)
);
