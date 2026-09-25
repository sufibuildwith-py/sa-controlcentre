CREATE TABLE navigator_organizations (
  id UUID PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE navigator_pairing_invites (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  code_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  redeemed_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  created_by_ref UUID
);
CREATE INDEX idx_navigator_pairings_expiry ON navigator_pairing_invites(expires_at);

CREATE TABLE navigator_devices (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  platform VARCHAR(16) NOT NULL,
  device_label VARCHAR(120),
  registered_at TIMESTAMPTZ NOT NULL,
  last_seen_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_navigator_devices_employee ON navigator_devices(organization_id, employee_ref);

CREATE TABLE navigator_location_sessions (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  device_id UUID NOT NULL REFERENCES navigator_devices(id),
  consent_version VARCHAR(32) NOT NULL,
  trigger VARCHAR(16) NOT NULL DEFAULT 'EMPLOYEE' CHECK (trigger IN ('EMPLOYEE','SIMULATOR')),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  end_reason VARCHAR(32) CHECK (end_reason IS NULL OR end_reason IN ('EMPLOYEE_STOPPED','DEVICE_REVOKED','SESSION_EXPIRED','SYSTEM_RECOVERY')),
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_navigator_sessions_device ON navigator_location_sessions(device_id, started_at DESC);

CREATE TABLE navigator_location_points (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  device_id UUID NOT NULL REFERENCES navigator_devices(id),
  session_id UUID NOT NULL REFERENCES navigator_location_sessions(id),
  sequence_no BIGINT NOT NULL,
  latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  accuracy_meters DOUBLE PRECISION CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
  speed_mps DOUBLE PRECISION CHECK (speed_mps IS NULL OR speed_mps >= 0),
  heading_degrees DOUBLE PRECISION CHECK (heading_degrees IS NULL OR heading_degrees BETWEEN 0 AND 360),
  recorded_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  UNIQUE(device_id, session_id, sequence_no)
);
CREATE INDEX idx_navigator_points_employee_time ON navigator_location_points(organization_id, employee_ref, recorded_at DESC);
CREATE INDEX idx_navigator_points_session_sequence ON navigator_location_points(session_id, sequence_no);
CREATE INDEX idx_navigator_points_recorded_at ON navigator_location_points(recorded_at);

CREATE TABLE navigator_latest_locations (
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  device_id UUID NOT NULL REFERENCES navigator_devices(id),
  session_id UUID NOT NULL REFERENCES navigator_location_sessions(id),
  latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
  longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
  accuracy_meters DOUBLE PRECISION CHECK (accuracy_meters IS NULL OR accuracy_meters >= 0),
  recorded_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(organization_id, employee_ref)
);

CREATE TABLE navigator_security_events (
  id UUID PRIMARY KEY,
  organization_id UUID,
  event_type VARCHAR(80) NOT NULL,
  subject_ref UUID,
  evidence_json TEXT,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_navigator_security_events_created ON navigator_security_events(created_at);
