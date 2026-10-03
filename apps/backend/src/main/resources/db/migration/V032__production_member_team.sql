ALTER TABLE production_members ADD COLUMN team_name VARCHAR(120);
CREATE INDEX production_members_team_name_idx ON production_members(team_name);
