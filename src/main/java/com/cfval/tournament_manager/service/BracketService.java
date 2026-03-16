package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.response.BracketResponse;
import com.cfval.tournament_manager.dto.response.MatchResponse;
import com.cfval.tournament_manager.dto.response.TeamResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.model.Match;
import com.cfval.tournament_manager.model.MatchStatus;
import com.cfval.tournament_manager.model.Registration;
import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.repository.MatchRepository;
import com.cfval.tournament_manager.repository.RegistrationRepository;
import com.cfval.tournament_manager.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BracketService {

    private final TournamentService tournamentService;
    private final RegistrationRepository registrationRepository;
    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;

    public BracketResponse generateBracket(UUID tournamentId) {
        Tournament tournament = tournamentService.loadTournament(tournamentId);

        if (tournament.getStatus() != TournamentStatus.REGISTRATION_CLOSED) {
            throw new BusinessException(
                    "Bracket can only be generated when status is REGISTRATION_CLOSED (current: "
                            + tournament.getStatus() + ")"
            );
        }
        if (matchRepository.existsByTournament(tournament)) {
            throw new BusinessException("Bracket already generated for this tournament");
        }

        List<Team> teams = registrationRepository.findByTournament(tournament).stream()
                .map(Registration::getTeam)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(teams);

        int n = teams.size();
        int p = nextPowerOf2(n);
        int byes = p - n;
        int rounds = Integer.numberOfTrailingZeros(p);

        // Step 1 — create all match shells before wiring links;
        // BYE advancement reads nextMatch immediately, so the target must exist in memory first.
        LinkedHashMap<String, Match> matchMap = new LinkedHashMap<>();
        for (int r = 1; r <= rounds; r++) {
            int matchesInRound = p >> r;
            for (int pos = 1; pos <= matchesInRound; pos++) {
                Match match = new Match();
                match.setTournament(tournament);
                match.setRound(r);
                match.setPosition(pos);
                match.setStatus(MatchStatus.PENDING);
                matchMap.put(r + ":" + pos, match);
            }
        }

        // Step 2 — wire nextMatch links
        for (int r = 1; r < rounds; r++) {
            int matchesInRound = p >> r;
            for (int pos = 1; pos <= matchesInRound; pos++) {
                Match match = matchMap.get(r + ":" + pos);
                int nextPos = (pos + 1) / 2; // ceil(pos/2)
                Match nextMatch = matchMap.get((r + 1) + ":" + nextPos);
                match.setNextMatch(nextMatch);
                match.setNextMatchPos(pos % 2 == 1 ? 1 : 2);
            }
        }

        // Step 3 — assign teams to round 1
        int teamIndex = 0;
        for (int pos = 1; pos <= byes; pos++) {
            Team team = teams.get(teamIndex++);
            Match byeMatch = matchMap.get("1:" + pos);
            byeMatch.setTeamA(team);
            byeMatch.setTeamB(null);
            byeMatch.setWinner(team);
            byeMatch.setStatus(MatchStatus.BYE);
            // Propagate BYE winner to the next round immediately
            if (byeMatch.getNextMatch() != null) {
                advanceToNextSlot(byeMatch.getNextMatch(), team, byeMatch.getNextMatchPos());
            }
        }
        for (int pos = byes + 1; pos <= p / 2; pos++) {
            Match match = matchMap.get("1:" + pos);
            match.setTeamA(teams.get(teamIndex++));
            match.setTeamB(teams.get(teamIndex++));
            match.setStatus(MatchStatus.SCHEDULED);
        }

        // Step 4 — after BYE propagation, promote any round 2+ match that already has both teams
        for (int r = 2; r <= rounds; r++) {
            int matchesInRound = p >> r;
            for (int pos = 1; pos <= matchesInRound; pos++) {
                Match match = matchMap.get(r + ":" + pos);
                if (match.getTeamA() != null && match.getTeamB() != null) {
                    match.setStatus(MatchStatus.SCHEDULED);
                }
            }
        }

        matchRepository.saveAll(matchMap.values());
        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournamentRepository.save(tournament);

        return toBracketResponse(tournament, new ArrayList<>(matchMap.values()), rounds);
    }

    @Transactional(readOnly = true)
    public BracketResponse getBracket(UUID tournamentId) {
        Tournament tournament = tournamentService.loadTournament(tournamentId);
        List<Match> matches = matchRepository.findByTournamentOrderByRoundAscPositionAsc(tournament);
        if (matches.isEmpty()) {
            throw new BusinessException("No bracket generated for tournament: " + tournamentId);
        }
        int rounds = matches.stream().mapToInt(Match::getRound).max().orElse(0);
        return toBracketResponse(tournament, matches, rounds);
    }

    // --- private helpers ---

    private int nextPowerOf2(int n) {
        if (n <= 1) return 1;
        return Integer.highestOneBit(n - 1) << 1;
    }

    private void advanceToNextSlot(Match match, Team team, int slot) {
        if (slot == 1) {
            match.setTeamA(team);
        } else {
            match.setTeamB(team);
        }
    }

    private BracketResponse toBracketResponse(Tournament tournament, List<Match> matches, int rounds) {
        List<MatchResponse> matchResponses = matches.stream()
                .map(this::toMatchResponse)
                .toList();
        return new BracketResponse(tournament.getId(), rounds, matchResponses);
    }

    MatchResponse toMatchResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getRound(),
                match.getPosition(),
                match.getTeamA() != null ? toTeamResponse(match.getTeamA()) : null,
                match.getTeamB() != null ? toTeamResponse(match.getTeamB()) : null,
                match.getWinner() != null ? toTeamResponse(match.getWinner()) : null,
                match.getStatus().name(),
                match.getPlayedAt()
        );
    }

    private TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getOwner().getUsername(),
                team.getCreatedAt()
        );
    }
}
