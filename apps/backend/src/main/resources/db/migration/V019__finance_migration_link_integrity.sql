-- Source memberships cannot allocate more than either side of the evidence.
CREATE FUNCTION finance_check_migration_link() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE domain_fact finance_migration_facts%ROWTYPE;
DECLARE owner_fact finance_migration_facts%ROWTYPE;
DECLARE domain_used NUMERIC(19,2);
DECLARE owner_used NUMERIC(19,2);
BEGIN
  SELECT * INTO domain_fact FROM finance_migration_facts WHERE id=NEW.domain_fact_id FOR UPDATE;
  SELECT * INTO owner_fact FROM finance_migration_facts WHERE id=NEW.owner_fact_id FOR UPDATE;
  IF domain_fact.id IS NULL OR owner_fact.id IS NULL OR domain_fact.batch_id<>NEW.batch_id OR owner_fact.batch_id<>NEW.batch_id
     OR domain_fact.source_role<>'DOMAIN' OR owner_fact.source_role<>'OWNER'
     OR domain_fact.direction<>owner_fact.direction THEN
    RAISE EXCEPTION 'Migration link source roles, batch or cash direction conflict';
  END IF;
  SELECT coalesce(sum(allocated_amount),0) INTO domain_used FROM finance_migration_links WHERE domain_fact_id=NEW.domain_fact_id;
  SELECT coalesce(sum(allocated_amount),0) INTO owner_used FROM finance_migration_links WHERE owner_fact_id=NEW.owner_fact_id;
  IF domain_used>domain_fact.amount OR owner_used>owner_fact.amount THEN
    RAISE EXCEPTION 'Migration source allocation exceeds the source amount';
  END IF;
  RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER finance_migration_link_amount_check
AFTER INSERT OR UPDATE ON finance_migration_links
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_check_migration_link();
