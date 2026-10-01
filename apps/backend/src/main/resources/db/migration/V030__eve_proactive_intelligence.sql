CREATE TABLE eve_signals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  signal_type VARCHAR(64) NOT NULL,
  source_domain VARCHAR(64) NOT NULL,
  canonical_entity_type VARCHAR(64) NOT NULL,
  canonical_entity_id UUID NOT NULL,
  canonical_version INT,
  actor_id VARCHAR(120),
  correlation_id VARCHAR(120),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  failure_reason TEXT
);

CREATE TABLE eve_suggestions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  title VARCHAR(255) NOT NULL,
  summary TEXT NOT NULL,
  source_signal_id UUID REFERENCES eve_signals(id) ON DELETE SET NULL,
  target_domain VARCHAR(64) NOT NULL,
  canonical_entity_type VARCHAR(64) NOT NULL,
  canonical_entity_id UUID NOT NULL,
  canonical_entity_name VARCHAR(255),
  evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
  dedupe_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ,
  dismissed_at TIMESTAMPTZ,
  resolved_at TIMESTAMPTZ,
  dismissed_by VARCHAR(120),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX eve_signals_type_idx ON eve_signals(signal_type, status);
CREATE INDEX eve_signals_entity_idx ON eve_signals(canonical_entity_type, canonical_entity_id);
CREATE INDEX eve_signals_occurred_idx ON eve_signals(occurred_at DESC);

CREATE INDEX eve_suggestions_status_idx ON eve_suggestions(status, created_at DESC);
CREATE INDEX eve_suggestions_entity_idx ON eve_suggestions(canonical_entity_type, canonical_entity_id);
CREATE INDEX eve_suggestions_dedupe_idx ON eve_suggestions(dedupe_key);
CREATE INDEX eve_suggestions_type_idx ON eve_suggestions(type);
CREATE UNIQUE INDEX uq_eve_active_suggestion_dedupe ON eve_suggestions(dedupe_key) WHERE status = 'ACTIVE';
