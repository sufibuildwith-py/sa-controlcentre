CREATE TABLE eve_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES eve_sessions(id) ON DELETE CASCADE,
  message_id UUID REFERENCES eve_messages(id) ON DELETE SET NULL,
  intent VARCHAR(64) NOT NULL,
  summary TEXT NOT NULL,
  risk_tier VARCHAR(32) NOT NULL,
  confirmation_required BOOLEAN NOT NULL DEFAULT true,
  plan_hash VARCHAR(64) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  confirmed_at TIMESTAMPTZ,
  executed_at TIMESTAMPTZ
);

CREATE TABLE eve_plan_actions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id UUID NOT NULL REFERENCES eve_plans(id) ON DELETE CASCADE,
  seq INT NOT NULL,
  domain VARCHAR(64) NOT NULL,
  command_type VARCHAR(64) NOT NULL,
  target_entity_id UUID,
  target_entity_name VARCHAR(255),
  parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
  estimated_effect TEXT,
  required_permission VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  canonical_record_id UUID,
  execution_result JSONB,
  verification_result JSONB,
  idempotency_key UUID UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_eve_plan_actions_plan_seq UNIQUE (plan_id, seq)
);

CREATE INDEX eve_plans_session_idx ON eve_plans(session_id, created_at DESC);
CREATE INDEX eve_plans_status_idx ON eve_plans(status);
CREATE INDEX eve_plan_actions_plan_idx ON eve_plan_actions(plan_id, seq ASC);
CREATE INDEX eve_plan_actions_idempotency_idx ON eve_plan_actions(idempotency_key);
CREATE INDEX eve_plan_actions_canonical_idx ON eve_plan_actions(canonical_record_id);
