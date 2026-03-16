CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE tournaments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    max_teams INT NOT NULL,
    start_date TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    team_id UUID NOT NULL REFERENCES teams(id),
    registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_registration UNIQUE (tournament_id, team_id)
);

CREATE TABLE matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id UUID NOT NULL REFERENCES tournaments(id),
    round INT NOT NULL,
    position INT NOT NULL,
    team_a_id UUID REFERENCES teams(id),
    team_b_id UUID REFERENCES teams(id),
    winner_id UUID REFERENCES teams(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    next_match_id UUID REFERENCES matches(id),
    next_match_pos INT,
    played_at TIMESTAMP
);