CREATE TABLE outbound_delivery_attempts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  outbound_message_id UUID NOT NULL REFERENCES outbound_messages(id) ON DELETE CASCADE,
  attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
  provider_message_id VARCHAR(255) UNIQUE,
  status VARCHAR(24) NOT NULL CHECK (status IN ('SENDING','SENT','DELIVERED','READ','FAILED')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  accepted_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  read_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ,
  last_error VARCHAR(1000),
  CONSTRAINT outbound_delivery_attempt_number_uk UNIQUE(outbound_message_id, attempt_number)
);

CREATE INDEX outbound_delivery_attempt_message_idx
  ON outbound_delivery_attempts(outbound_message_id, attempt_number);

INSERT INTO outbound_delivery_attempts(
  outbound_message_id,attempt_number,provider_message_id,status,started_at,
  accepted_at,delivered_at,read_at,failed_at,last_error)
SELECT id,GREATEST(attempt_count,1),provider_message_id,
       CASE WHEN status='QUEUED' THEN 'FAILED' ELSE status END,
       queued_at,sent_at,delivered_at,read_at,failed_at,last_error
FROM outbound_messages
WHERE attempt_count>0 OR provider_message_id IS NOT NULL;
