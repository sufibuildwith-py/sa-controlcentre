CREATE TABLE eve_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE eve_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES eve_sessions(id) ON DELETE CASCADE,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE eve_trace_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES eve_sessions(id) ON DELETE CASCADE,
  message_id UUID REFERENCES eve_messages(id) ON DELETE SET NULL,
  seq INT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  label VARCHAR(255) NOT NULL,
  detail TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE eve_memory (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  memory_type VARCHAR(64) NOT NULL,
  term VARCHAR(160) NOT NULL,
  canonical_type VARCHAR(64) NOT NULL,
  canonical_id UUID,
  canonical_name VARCHAR(255),
  confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
  source VARCHAR(64) NOT NULL DEFAULT 'OPERATOR_EXPLICIT',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT eve_memory_term_uniq UNIQUE (memory_type, term)
);

CREATE INDEX eve_messages_session_idx ON eve_messages(session_id, created_at ASC);
CREATE INDEX eve_trace_session_idx ON eve_trace_events(session_id, seq ASC);
CREATE INDEX eve_sessions_updated_idx ON eve_sessions(updated_at DESC);
CREATE INDEX eve_memory_term_idx ON eve_memory(term);
