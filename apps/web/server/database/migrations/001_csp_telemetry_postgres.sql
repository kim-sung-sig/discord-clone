BEGIN;

CREATE TABLE IF NOT EXISTS csp_telemetry (
  id BIGSERIAL PRIMARY KEY,
  received_at TIMESTAMPTZ NOT NULL,
  request_id TEXT NOT NULL,
  document_uri_origin TEXT NOT NULL,
  blocked_uri_origin TEXT NOT NULL,
  violated_directive TEXT NOT NULL,
  effective_directive TEXT NOT NULL,
  disposition TEXT NOT NULL,
  user_agent_hash TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_csp_telemetry_received_at
ON csp_telemetry(received_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_csp_telemetry_effective_directive
ON csp_telemetry(effective_directive);

CREATE TABLE IF NOT EXISTS csp_telemetry_retention_metrics (
  metric TEXT PRIMARY KEY,
  value BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS csp_rate_limit_telemetry (
  id BIGSERIAL PRIMARY KEY,
  received_at TIMESTAMPTZ NOT NULL,
  subject_hash TEXT NOT NULL,
  reset_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_csp_rate_limit_telemetry_received_at
ON csp_rate_limit_telemetry(received_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS csp_alert_transitions (
  id BIGSERIAL PRIMARY KEY,
  observed_at TIMESTAMPTZ NOT NULL,
  active BOOLEAN NOT NULL,
  reasons_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_csp_alert_transitions_observed_at
ON csp_alert_transitions(observed_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS csp_alert_acknowledgements (
  fingerprint TEXT PRIMARY KEY,
  reason TEXT NOT NULL,
  acknowledged_by TEXT NOT NULL,
  acknowledged_at TIMESTAMPTZ NOT NULL,
  snooze_until TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_csp_alert_acknowledgements_acknowledged_at
ON csp_alert_acknowledgements(acknowledged_at DESC);

CREATE TABLE IF NOT EXISTS csp_alert_incident_events (
  id BIGSERIAL PRIMARY KEY,
  fingerprint TEXT NOT NULL,
  event_type TEXT NOT NULL,
  status TEXT NOT NULL,
  actor TEXT NOT NULL,
  assigned_to TEXT,
  reason TEXT,
  occurred_at TIMESTAMPTZ NOT NULL,
  snooze_until TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_csp_alert_incident_events_fingerprint_occurred_at
ON csp_alert_incident_events(fingerprint, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_csp_alert_incident_events_occurred_at
ON csp_alert_incident_events(occurred_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS security_dashboard_operator_tokens (
  token_hash TEXT PRIMARY KEY,
  actor TEXT NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_security_dashboard_operator_tokens_expires_at
ON security_dashboard_operator_tokens(expires_at);

CREATE TABLE IF NOT EXISTS security_dashboard_operator_token_audit (
  id BIGSERIAL PRIMARY KEY,
  action TEXT NOT NULL,
  token_hash_prefix TEXT NOT NULL,
  actor TEXT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  reason TEXT
);

CREATE INDEX IF NOT EXISTS idx_security_dashboard_operator_token_audit_occurred_at
ON security_dashboard_operator_token_audit(occurred_at DESC, id DESC);

COMMIT;
