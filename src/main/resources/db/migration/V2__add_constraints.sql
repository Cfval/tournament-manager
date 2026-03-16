ALTER TABLE teams
    ADD CONSTRAINT uq_team_owner_name UNIQUE (owner_id, name);
