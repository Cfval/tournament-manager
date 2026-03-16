# Tournament Manager API

Portfolio project. Goal: clean, defensible code for the European junior market.
A senior developer must be able to review this and see structure, reasoned decisions and good practices.

## Stack
- Java 21, Spring Boot 4.0.X, Maven
- PostgreSQL 16 (Docker on port 5433), Spring Data JPA, Flyway
- Spring Security + JWT
- Lombok
- Classic layered architecture: Controller → Service → Repository

## Project structure
com.cfval.tournament_manager
├── controller
├── service
├── repository
├── model
├── dto
│   ├── request
│   └── response
├── exception
└── security

## Current status
Phase 1 in progress. Docker + Flyway migration working.
Database schema already created via V1__create_tables.sql.
Starting JPA entities now.

## Database schema
Tables: users, tournaments, teams, registrations, matches
- All PKs are UUID (gen_random_uuid())
- matches.team_a_id, team_b_id, winner_id are nullable (assigned as bracket progresses)
- matches.next_match_id is a self-reference FK
- UNIQUE constraint on registrations(tournament_id, team_id)

## Entities
- User: id, username, email, passwordHash, role, createdAt
- Tournament: id, name, status, maxTeams, startDate, createdBy, createdAt
- Team: id, name, ownerId, createdAt
- Registration: id, tournamentId, teamId, registeredAt
- Match: id, tournamentId, round, position, teamA, teamB, winner, status, nextMatchId, nextMatchPos, playedAt

## Enums
- Role: ADMIN, USER
- TournamentStatus: OPEN, REGISTRATION_CLOSED, IN_PROGRESS, FINISHED
- MatchStatus: PENDING, SCHEDULED, COMPLETED, BYE

## Key decisions
- UUIDs as primary keys
- Match.nextMatchId + Match.nextMatchPosition enable automatic round advancement
- Round advancement is automatic — side effect of recording the last result of a round
- UNIQUE constraint on registrations(tournament_id, team_id) enforced at DB level
- DTOs always separate from entities — never expose JPA entities in the API
- Flyway for migrations, ddl-auto: validate
- PostgreSQL on port 5433 (5432 used by local PostgreSQL instance)
- Only ADMIN records match results

## Use cases

### Anonymous
- View tournament list
- View tournament detail
- View tournament bracket
- Register (POST /auth/register)
- Login (POST /auth/login)

### USER
- Everything anonymous can do
- Create a team
- View own teams
- Register a team in an open tournament

### ADMIN
- Everything USER can do
- Create a tournament
- Close registrations
- Generate bracket
- Record a match result (triggers automatic round advancement)

## API endpoints

### Auth
POST   /api/auth/register       Anonymous   Register new user
POST   /api/auth/login          Anonymous   Login, returns JWT

### Tournaments
GET    /api/tournaments                           All     List all tournaments
GET    /api/tournaments/{id}                      All     Tournament detail
POST   /api/tournaments                           ADMIN   Create tournament
PUT    /api/tournaments/{id}/close-registrations  ADMIN   Close registrations
POST   /api/tournaments/{id}/bracket              ADMIN   Generate bracket
GET    /api/tournaments/{id}/bracket              All     View bracket

### Teams & Registrations
POST   /api/teams                                 USER/ADMIN   Create team
GET    /api/teams                                 USER/ADMIN   List own teams
POST   /api/tournaments/{id}/registrations        USER/ADMIN   Register team in tournament

### Matches
POST   /api/matches/{id}/result                   ADMIN   Record result, triggers round advancement

## Execution plan

### Phase 1 — Foundation (current)
- [x] Initialize Spring Boot project with Maven
- [x] Configure Docker Compose with PostgreSQL
- [x] Write V1__create_tables.sql with full schema
- [x] Define JPA entities with annotations
- [x] Implement Spring Security + JWT filter
- [x] Working /auth/register and /auth/login endpoints

### Phase 2 — Core domain
- [ ] TournamentService: CRUD + status transitions with validations
- [ ] TeamService: create team, register in tournament with all business validations
- [ ] Unit tests for all business validations
- [ ] Swagger configured and documenting implemented endpoints

### Phase 3 — Bracket logic
- [ ] BracketService: full bracket generation with BYE handling
- [ ] MatchService: record result + automatic round advancement via nextMatchId
- [ ] Exhaustive unit tests for bracket (edge cases: powers of 2, odd numbers)
- [ ] Integration tests for the full tournament flow with Testcontainers

### Phase 4 — Polish & deploy
- [ ] Global error handling with @ControllerAdvice
- [ ] Input validation with Bean Validation (@Valid)
- [ ] Professional README on GitHub
- [ ] Full Docker + docker-compose setup
- [ ] Deploy to Render with live Swagger UI
```
