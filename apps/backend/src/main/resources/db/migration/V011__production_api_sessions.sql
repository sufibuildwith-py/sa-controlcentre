CREATE TABLE api_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token_hash CHAR(64) NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  CONSTRAINT api_sessions_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX api_sessions_user_active_idx
  ON api_sessions(user_id, expires_at)
  WHERE revoked_at IS NULL;
