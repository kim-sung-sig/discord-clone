CREATE TABLE IF NOT EXISTS user_global_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(64) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role)
);

CREATE INDEX IF NOT EXISTS idx_user_global_roles_role ON user_global_roles(role);
