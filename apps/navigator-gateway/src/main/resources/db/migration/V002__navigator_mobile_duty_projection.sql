CREATE UNIQUE INDEX uq_navigator_active_duty_employee
  ON navigator_location_sessions(organization_id, employee_ref)
  WHERE ended_at IS NULL;
CREATE UNIQUE INDEX uq_navigator_active_duty_device
  ON navigator_location_sessions(device_id)
  WHERE ended_at IS NULL;

CREATE TABLE navigator_mobile_projections (
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  production_ref UUID,
  production_title VARCHAR(180),
  location_name VARCHAR(180),
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (organization_id, employee_ref)
);

CREATE TABLE navigator_mobile_messages (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES navigator_organizations(id),
  employee_ref UUID NOT NULL,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(1600) NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL,
  read_at TIMESTAMPTZ
);
CREATE INDEX idx_navigator_mobile_messages_employee
  ON navigator_mobile_messages(organization_id, employee_ref, sent_at DESC);
