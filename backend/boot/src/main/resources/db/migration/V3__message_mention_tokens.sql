CREATE TABLE IF NOT EXISTS message_mention_tokens (
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    mention TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (message_id, position),
    UNIQUE (message_id, mention)
);
