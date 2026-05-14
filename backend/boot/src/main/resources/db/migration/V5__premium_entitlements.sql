CREATE TABLE IF NOT EXISTS premium_entitlements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    guild_id UUID NOT NULL REFERENCES guilds(id),
    feature_key VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_subscription_id VARCHAR(160) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT premium_entitlements_provider_subscription_unique UNIQUE(provider, provider_subscription_id)
);

CREATE INDEX IF NOT EXISTS idx_premium_entitlements_user_feature
    ON premium_entitlements(user_id, feature_key);

CREATE INDEX IF NOT EXISTS idx_premium_entitlements_guild
    ON premium_entitlements(guild_id);
