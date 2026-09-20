CREATE TABLE notification_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type VARCHAR(48) NOT NULL UNIQUE,
  channel VARCHAR(24) NOT NULL DEFAULT 'WHATSAPP' CHECK (channel IN ('WHATSAPP')),
  enabled BOOLEAN NOT NULL DEFAULT true,
  delay_minutes INTEGER NOT NULL DEFAULT 0 CHECK (delay_minutes >= 0),
  template_key VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type VARCHAR(80) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id UUID,
  payload_json JSONB NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','PROCESSED','FAILED')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processing_started_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  last_error VARCHAR(1000)
);
CREATE INDEX outbox_claim_idx ON outbox_events(status, available_at, created_at);

CREATE TABLE outbound_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  employee_id UUID NOT NULL REFERENCES employees(id),
  channel VARCHAR(24) NOT NULL DEFAULT 'WHATSAPP' CHECK (channel IN ('WHATSAPP')),
  category VARCHAR(32) NOT NULL CHECK (category IN ('ASSIGNMENTS','MEETINGS','ATTENDANCE','PAYROLL','TASKS','LEAVE','MANUAL')),
  template_key VARCHAR(80) NOT NULL,
  template_variables_json JSONB NOT NULL,
  body_preview VARCHAR(2000) NOT NULL,
  related_type VARCHAR(48),
  related_id UUID,
  requires_response BOOLEAN NOT NULL DEFAULT false,
  response VARCHAR(32),
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','SENDING','SENT','DELIVERED','READ','FAILED')),
  provider_message_id VARCHAR(255),
  idempotency_key VARCHAR(255) NOT NULL UNIQUE,
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error VARCHAR(1000),
  queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  read_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX outbound_message_claim_idx ON outbound_messages(status, next_attempt_at, queued_at);
CREATE INDEX outbound_message_employee_idx ON outbound_messages(employee_id, queued_at DESC);
CREATE INDEX outbound_message_provider_idx ON outbound_messages(provider_message_id) WHERE provider_message_id IS NOT NULL;
CREATE INDEX outbound_message_related_idx ON outbound_messages(related_type, related_id);

CREATE TABLE outbound_message_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  outbound_message_id UUID NOT NULL REFERENCES outbound_messages(id) ON DELETE CASCADE,
  event_type VARCHAR(40) NOT NULL,
  detail VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX outbound_message_events_idx ON outbound_message_events(outbound_message_id, created_at);

CREATE TABLE webhook_receipts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_event_id VARCHAR(255) NOT NULL UNIQUE,
  event_type VARCHAR(48) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ
);

INSERT INTO notification_rules(event_type,delay_minutes,template_key) VALUES
 ('PRODUCTION_ASSIGNED',0,'sa_production_assignment'),
 ('PRODUCTION_UPDATED',0,'sa_schedule_changed'),
 ('EVENT_CHANGED',0,'sa_schedule_changed'),
 ('EVENT_REMINDER_24H',1440,'sa_event_reminder'),
 ('EVENT_REMINDER_2H',120,'sa_event_reminder'),
 ('TASK_ASSIGNED',0,'sa_task_assigned'),
 ('TASK_DUE_24H',1440,'sa_task_due'),
 ('MEETING_CREATED',0,'sa_meeting_invitation'),
 ('MEETING_UPDATED',0,'sa_meeting_invitation'),
 ('MEETING_REMINDER',120,'sa_meeting_reminder'),
 ('ATTENDANCE_MISSING',0,'sa_attendance_missing'),
 ('LEAVE_APPROVED',0,'sa_leave_status'),
 ('LEAVE_REJECTED',0,'sa_leave_status'),
 ('SALARY_PROCESSED',0,'sa_salary_processed'),
 ('MANUAL_NOTICE',0,'sa_manual_notice');
