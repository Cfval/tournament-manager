# Tournament Manager API

REST API for managing esports tournaments with single-elimination brackets, JWT authentication, and role-based access control.

## Stack
- Java 17, Spring Boot 4.0.x, Maven
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

## Database schema
Tables: users, tournaments, teams, registrations, matches
- All PKs are UUID (gen_random_uuid())
- matches.team_a_id, team_b_id, winner_id are nullable (assigned as bracket progresses)
- matches.next_match_id is a self-reference FK
- UNIQUE constraint on registrations(tournament_id, team_id)
- UNIQUE constraint on teams(owner_id, name)

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
- View tournament list, detail and bracket
- Register and login

### USER
- Everything anonymous can do
- Create teams and view own teams
- Register a team in an open tournament

### ADMIN
- Everything USER can do
- Create and manage tournaments
- Generate bracket
- Record match results (triggers automatic round advancement)

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
