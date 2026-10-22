CREATE TABLE moderation_questions (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    prompt TEXT NOT NULL CHECK (length(trim(prompt)) > 0),
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE
);
CREATE INDEX idx_moderation_questions_guild ON moderation_questions(guild_id, ordinal);
CREATE TABLE moderation_answers (
    question_id UUID NOT NULL REFERENCES moderation_questions(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    id UUID NOT NULL,
    label TEXT NOT NULL CHECK (length(trim(label)) > 0),
    role_ids UUID[] NOT NULL,
    PRIMARY KEY (question_id, ordinal)
);
CREATE TABLE moderation_rules (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL CHECK (type IN ('KEYWORD', 'SPAM')),
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    keywords TEXT[] NOT NULL,
    enabled BOOLEAN NOT NULL,
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE
);
CREATE INDEX idx_moderation_rules_guild ON moderation_rules(guild_id, ordinal);
CREATE TABLE moderation_audit_logs (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL,
    actor_id UUID NOT NULL,
    target_id UUID NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE
);
CREATE INDEX idx_moderation_audit_guild ON moderation_audit_logs(guild_id, ordinal DESC);
CREATE INDEX idx_moderation_audit_filters ON moderation_audit_logs(guild_id, action, actor_id, target_id, ordinal DESC);
CREATE TABLE moderation_security_alerts (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL,
    target_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE
);
CREATE INDEX idx_moderation_alert_guild ON moderation_security_alerts(guild_id, ordinal DESC);
-- Audit targets and report message/channel IDs intentionally survive resource deletion.
CREATE TABLE moderation_message_reports (
    id UUID PRIMARY KEY,
    guild_id UUID NOT NULL REFERENCES guilds(id) ON DELETE CASCADE,
    channel_id UUID NOT NULL,
    message_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    moderator_id UUID,
    resolution TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    ordinal BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE
);
CREATE INDEX idx_moderation_report_guild ON moderation_message_reports(guild_id, ordinal);
CREATE INDEX idx_moderation_report_status ON moderation_message_reports(guild_id, status, ordinal);
CREATE INDEX idx_moderation_report_message ON moderation_message_reports(guild_id, message_id, ordinal);
