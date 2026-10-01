INSERT INTO hq_units (
  organization_id,
  name,
  symbol,
  decimal_allowed,
  active
)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'Square Foot',
  'sqft',
  TRUE,
  TRUE
)
ON CONFLICT (organization_id, name)
DO NOTHING;
