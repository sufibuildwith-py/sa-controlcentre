ALTER TABLE api_sessions ADD COLUMN finance_granted_at TIMESTAMPTZ;
ALTER TABLE api_sessions ADD COLUMN finance_expires_at TIMESTAMPTZ;
ALTER TABLE api_sessions ADD COLUMN finance_revoked_at TIMESTAMPTZ;
