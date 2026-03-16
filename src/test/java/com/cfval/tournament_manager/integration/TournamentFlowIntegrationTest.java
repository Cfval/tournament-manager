package com.cfval.tournament_manager.integration;

/*
 * NOTE — Docker socket access on Windows
 * ----------------------------------------
 * Testcontainers requires a directly accessible Docker daemon socket.
 * Docker Desktop 4.x on Windows routes all pipe connections through a proxy
 * that returns a 400 response, which Testcontainers 1.20.4 does not handle.
 *
 * To run this test locally on Windows, either:
 *   a) Enable "Expose daemon on tcp://localhost:2375 without TLS" in
 *      Docker Desktop → Settings → General, and set
 *      DOCKER_HOST=tcp://localhost:2375 before running Maven; or
 *   b) Run from IntelliJ/WSL2 where Docker Desktop registers a proper socket.
 *
 * In CI (Linux runners with Docker), this test runs without any extra config.
 */

import com.cfval.tournament_manager.dto.request.CreateTeamRequest;
import com.cfval.tournament_manager.dto.request.CreateTournamentRequest;
import com.cfval.tournament_manager.dto.request.LoginRequest;
import com.cfval.tournament_manager.dto.request.RecordResultRequest;
import com.cfval.tournament_manager.dto.request.RegisterRequest;
import com.cfval.tournament_manager.dto.request.RegisterTeamRequest;
import com.cfval.tournament_manager.dto.response.AuthResponse;
import com.cfval.tournament_manager.dto.response.BracketResponse;
import com.cfval.tournament_manager.dto.response.MatchResponse;
import com.cfval.tournament_manager.dto.response.TeamResponse;
import com.cfval.tournament_manager.dto.response.TournamentResponse;
import com.cfval.tournament_manager.model.Role;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TournamentFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private RestClient client;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new RuntimeException("HTTP " + resp.getStatusCode() + " on " + req.getURI());
                })
                .build();

        // Admins cannot self-register via the public API (role is always USER).
        // Create the admin user directly to simulate a seeded admin account.
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@test.com");
            admin.setPasswordHash(passwordEncoder.encode("adminpass"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }

        adminToken = login("admin", "adminpass");

        // Register a regular user via the public API if not already present
        if (userRepository.findByUsername("player").isEmpty()) {
            client.post().uri("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RegisterRequest("player", "player@test.com", "playerpass"))
                    .retrieve()
                    .toBodilessEntity();
        }
        userToken = login("player", "playerpass");
    }

    @Test
    void fullTournamentFlow() {
        // 1 — Create tournament (admin)
        TournamentResponse tournament = post(
                "/api/tournaments",
                new CreateTournamentRequest("Integration Cup", 4, LocalDateTime.now().plusDays(7)),
                TournamentResponse.class,
                adminToken
        );
        assertThat(tournament.status()).isEqualTo("OPEN");
        UUID tournamentId = tournament.id();

        // 2 — Create 4 teams (as player) and register them
        for (int i = 0; i < 4; i++) {
            TeamResponse team = post(
                    "/api/teams",
                    new CreateTeamRequest("Team " + i),
                    TeamResponse.class,
                    userToken
            );
            post(
                    "/api/tournaments/" + tournamentId + "/registrations",
                    new RegisterTeamRequest(team.id()),
                    Object.class,
                    userToken
            );
        }

        // 3 — Close registrations (admin)
        TournamentResponse closed = put(
                "/api/tournaments/" + tournamentId + "/close-registrations",
                TournamentResponse.class,
                adminToken
        );
        assertThat(closed.status()).isEqualTo("REGISTRATION_CLOSED");

        // 4 — Generate bracket (admin): 4 teams → 2 rounds, 3 matches
        BracketResponse bracket = post(
                "/api/tournaments/" + tournamentId + "/bracket",
                null,
                BracketResponse.class,
                adminToken
        );
        assertThat(bracket.totalRounds()).isEqualTo(2);
        assertThat(bracket.matches()).hasSize(3);

        // 5 — Verify bracket is viewable by anyone
        BracketResponse publicBracket = get(
                "/api/tournaments/" + tournamentId + "/bracket",
                BracketResponse.class,
                null
        );
        assertThat(publicBracket.matches()).hasSize(3);

        // 6 — Record round-1 results (teamA wins each match)
        List<MatchResponse> round1 = bracket.matches().stream()
                .filter(m -> m.round() == 1).toList();
        assertThat(round1).hasSize(2);

        for (MatchResponse match : round1) {
            MatchResponse result = post(
                    "/api/matches/" + match.id() + "/result",
                    new RecordResultRequest(match.teamA().id()),
                    MatchResponse.class,
                    adminToken
            );
            assertThat(result.status()).isEqualTo("COMPLETED");
        }

        // 7 — Final must now be SCHEDULED with both teams assigned
        BracketResponse updated = get(
                "/api/tournaments/" + tournamentId + "/bracket",
                BracketResponse.class,
                null
        );
        MatchResponse finalMatch = updated.matches().stream()
                .filter(m -> m.round() == 2).findFirst().orElseThrow();
        assertThat(finalMatch.status()).isEqualTo("SCHEDULED");
        assertThat(finalMatch.teamA()).isNotNull();
        assertThat(finalMatch.teamB()).isNotNull();

        // 8 — Record final result → tournament must be FINISHED
        MatchResponse finalResult = post(
                "/api/matches/" + finalMatch.id() + "/result",
                new RecordResultRequest(finalMatch.teamA().id()),
                MatchResponse.class,
                adminToken
        );
        assertThat(finalResult.status()).isEqualTo("COMPLETED");
        assertThat(finalResult.winner().id()).isEqualTo(finalMatch.teamA().id());

        TournamentResponse finished = get(
                "/api/tournaments/" + tournamentId,
                TournamentResponse.class,
                null
        );
        assertThat(finished.status()).isEqualTo("FINISHED");
    }

    // --- HTTP helpers ---

    private String login(String username, String password) {
        return client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(username, password))
                .retrieve()
                .body(AuthResponse.class)
                .token();
    }

    private <T> T post(String uri, Object body, Class<T> responseType, String token) {
        var spec = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
        if (token != null) spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) spec = spec.body(body);
        return spec.retrieve().body(responseType);
    }

    private <T> T put(String uri, Class<T> responseType, String token) {
        return client.put().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(responseType);
    }

    private <T> T get(String uri, Class<T> responseType, String token) {
        var spec = client.get().uri(uri);
        if (token != null) spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return spec.retrieve().body(responseType);
    }
}
