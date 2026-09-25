CREATE TABLE hq_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  parent_id UUID REFERENCES hq_categories(id), name VARCHAR(120) NOT NULL,
  description VARCHAR(500), sort_order INTEGER NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, name)
);

CREATE TABLE hq_units (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  name VARCHAR(80) NOT NULL, symbol VARCHAR(24) NOT NULL, decimal_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, name)
);

CREATE TABLE hq_locations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  parent_id UUID REFERENCES hq_locations(id), name VARCHAR(140) NOT NULL,
  location_type VARCHAR(32) NOT NULL CHECK (location_type IN ('HEADQUARTERS','WAREHOUSE','STORAGE_ZONE','PRODUCTION_LOCATION','TRANSIT','WORKSHOP','EXTERNAL_CUSTODY','OTHER')),
  production_id UUID REFERENCES productions(id), active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, name)
);

CREATE TABLE hq_equipment (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  name VARCHAR(180) NOT NULL, internal_code VARCHAR(80), description TEXT,
  category_id UUID REFERENCES hq_categories(id), unit_id UUID NOT NULL REFERENCES hq_units(id),
  tracking_mode VARCHAR(24) NOT NULL CHECK (tracking_mode IN ('QUANTITY','SERIALIZED','CONSUMABLE')),
  minimum_reserve NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (minimum_reserve >= 0),
  default_location_id UUID REFERENCES hq_locations(id),
  ownership_default VARCHAR(24) NOT NULL CHECK (ownership_default IN ('SA_OWNED','RENTED','VENDOR_SUPPLIED','CLIENT_SUPPLIED','OTHER')),
  active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, internal_code)
);

CREATE TABLE hq_serialized_assets (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_code VARCHAR(100) NOT NULL,
  serial_number VARCHAR(160), location_id UUID REFERENCES hq_locations(id),
  condition VARCHAR(32) NOT NULL DEFAULT 'GOOD' CHECK (condition IN ('GOOD','INSPECTION_REQUIRED','DAMAGED','UNDER_REPAIR','UNUSABLE','MISSING','RETIRED')),
  availability VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' CHECK (availability IN ('AVAILABLE','RESERVED','PICKING','STAGED','DISPATCHED','DEPLOYED','IN_TRANSIT','RETURNING','INSPECTION','UNAVAILABLE')),
  ownership VARCHAR(24) NOT NULL CHECK (ownership IN ('SA_OWNED','RENTED','VENDOR_SUPPLIED','CLIENT_SUPPLIED','OTHER')),
  notes VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), retired_at TIMESTAMPTZ,
  UNIQUE (organization_id, asset_code), UNIQUE (id, equipment_id)
);

CREATE TABLE hq_inventory_positions (
  organization_id UUID NOT NULL, equipment_id UUID NOT NULL REFERENCES hq_equipment(id),
  location_id UUID NOT NULL REFERENCES hq_locations(id), ownership VARCHAR(24) NOT NULL,
  condition VARCHAR(32) NOT NULL DEFAULT 'GOOD', physical_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (physical_quantity >= 0),
  unavailable_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (unavailable_quantity >= 0 AND unavailable_quantity <= physical_quantity),
  version BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (organization_id, equipment_id, location_id, ownership, condition)
);

CREATE TABLE hq_reservations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  production_id UUID NOT NULL REFERENCES productions(id), status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('DRAFT','CONFIRMED','PARTIALLY_FULFILLED','FULFILLED','CANCELLED','EXPIRED')),
  starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(180) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);
CREATE TABLE hq_reservation_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), reservation_id UUID NOT NULL REFERENCES hq_reservations(id),
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id),
  production_location_id UUID REFERENCES hq_locations(id), quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0), notes VARCHAR(500)
);

CREATE TABLE hq_dispatches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, reference VARCHAR(40) NOT NULL,
  production_id UUID NOT NULL REFERENCES productions(id), source_location_id UUID NOT NULL REFERENCES hq_locations(id),
  destination_location_id UUID NOT NULL REFERENCES hq_locations(id), scheduled_at TIMESTAMPTZ,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PLANNED','PICKING','STAGED','DISPATCHED','RECEIVED_ON_SITE','CANCELLED')),
  notes VARCHAR(1000), confirmed_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(180) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE (organization_id, reference)
);
CREATE TABLE hq_dispatch_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), dispatch_id UUID NOT NULL REFERENCES hq_dispatches(id),
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id),
  reservation_line_id UUID REFERENCES hq_reservation_lines(id), quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0), notes VARCHAR(500)
);

CREATE TABLE hq_transfers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, reference VARCHAR(40) NOT NULL,
  source_production_id UUID REFERENCES productions(id), destination_production_id UUID REFERENCES productions(id),
  source_location_id UUID NOT NULL REFERENCES hq_locations(id), destination_location_id UUID NOT NULL REFERENCES hq_locations(id),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
  notes VARCHAR(1000), confirmed_at TIMESTAMPTZ, created_by VARCHAR(180) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, reference), CHECK (source_location_id <> destination_location_id)
);
CREATE TABLE hq_transfer_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), transfer_id UUID NOT NULL REFERENCES hq_transfers(id),
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id),
  quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0)
);

CREATE TABLE hq_returns (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, reference VARCHAR(40) NOT NULL,
  production_id UUID NOT NULL REFERENCES productions(id), source_location_id UUID NOT NULL REFERENCES hq_locations(id),
  destination_location_id UUID NOT NULL REFERENCES hq_locations(id), status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PARTIAL','RECONCILED','CANCELLED')),
  notes VARCHAR(1000), confirmed_at TIMESTAMPTZ, created_by VARCHAR(180) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, reference)
);
CREATE TABLE hq_return_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), return_id UUID NOT NULL REFERENCES hq_returns(id),
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id), expected_quantity NUMERIC(18,3) NOT NULL CHECK (expected_quantity > 0),
  returned_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0),
  transferred_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (transferred_quantity >= 0),
  consumed_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (consumed_quantity >= 0),
  damaged_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (damaged_quantity >= 0),
  missing_quantity NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (missing_quantity >= 0),
  CHECK (returned_quantity + transferred_quantity + consumed_quantity + damaged_quantity + missing_quantity <= expected_quantity)
);

CREATE TABLE hq_issues (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  issue_type VARCHAR(24) NOT NULL CHECK (issue_type IN ('DAMAGE','MISSING')),
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id),
  quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0), location_id UUID REFERENCES hq_locations(id),
  production_id UUID REFERENCES productions(id), severity VARCHAR(24), status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
  resolution VARCHAR(40), notes VARCHAR(1500), reported_by VARCHAR(180) NOT NULL,
  reported_at TIMESTAMPTZ NOT NULL DEFAULT now(), resolved_at TIMESTAMPTZ
);

CREATE TABLE hq_maintenance (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id),
  maintenance_type VARCHAR(80) NOT NULL, quantity NUMERIC(18,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
  due_at TIMESTAMPTZ, notes VARCHAR(1500), created_by VARCHAR(180) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ
);

CREATE TABLE hq_inventory_movements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL,
  movement_type VARCHAR(32) NOT NULL CHECK (movement_type IN ('STOCK_IN','ALLOCATED','DISPATCHED','TRANSFERRED','RETURNED','CONSUMED','SENT_TO_REPAIR','RETURNED_FROM_REPAIR','MARKED_MISSING','FOUND','RETIRED','ADJUSTMENT','REVERSAL','SYSTEM_RECOVERY')),
  equipment_id UUID NOT NULL REFERENCES hq_equipment(id), asset_id UUID REFERENCES hq_serialized_assets(id),
  quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0), source_location_id UUID REFERENCES hq_locations(id),
  destination_location_id UUID REFERENCES hq_locations(id), production_id UUID REFERENCES productions(id),
  reservation_id UUID REFERENCES hq_reservations(id), dispatch_id UUID REFERENCES hq_dispatches(id),
  transfer_id UUID REFERENCES hq_transfers(id), return_id UUID REFERENCES hq_returns(id), issue_id UUID REFERENCES hq_issues(id),
  maintenance_id UUID REFERENCES hq_maintenance(id), idempotency_key UUID NOT NULL,
  recorded_by VARCHAR(180) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(), reason VARCHAR(500), notes VARCHAR(1000),
  reversal_of_movement_id UUID REFERENCES hq_inventory_movements(id), UNIQUE (organization_id, idempotency_key, equipment_id, movement_type)
);

CREATE TABLE hq_attention (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), organization_id UUID NOT NULL, attention_key VARCHAR(180) NOT NULL,
  attention_type VARCHAR(48) NOT NULL, severity VARCHAR(16) NOT NULL CHECK (severity IN ('INFO','WARNING','CRITICAL')),
  title VARCHAR(180) NOT NULL, detail VARCHAR(800) NOT NULL, entity_type VARCHAR(40), entity_id UUID,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), resolved_at TIMESTAMPTZ, UNIQUE (organization_id, attention_key)
);

CREATE INDEX hq_equipment_search_idx ON hq_equipment (organization_id, active, lower(name));
CREATE INDEX hq_positions_equipment_idx ON hq_inventory_positions (organization_id, equipment_id);
CREATE INDEX hq_movements_time_idx ON hq_inventory_movements (organization_id, recorded_at DESC);
CREATE INDEX hq_movements_equipment_idx ON hq_inventory_movements (organization_id, equipment_id, recorded_at DESC);
CREATE INDEX hq_reservation_window_idx ON hq_reservations (organization_id, starts_at, ends_at) WHERE status IN ('CONFIRMED','PARTIALLY_FULFILLED');
CREATE INDEX hq_attention_open_idx ON hq_attention (organization_id, status, severity, created_at DESC);
CREATE INDEX hq_dispatch_status_idx ON hq_dispatches (organization_id, status, scheduled_at);
CREATE INDEX hq_issue_open_idx ON hq_issues (organization_id, status, issue_type);
