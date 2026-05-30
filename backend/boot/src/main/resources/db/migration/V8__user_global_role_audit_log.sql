CREATE TABLE IF NOT EXISTS user_global_role_audit_log (
    target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    result VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_global_role_audit_log_target_time
    ON user_global_role_audit_log(target_user_id, occurred_at);
