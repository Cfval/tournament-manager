package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.response.MatchResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.exception.ResourceNotFoundException;
import com.cfval.tournament_manager.model.Match;
import com.cfval.tournament_manager.model.MatchStatus;
import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.MatchRepository;
import com.cfval.tournament_manager.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    MatchRepository matchRepository;
    @Mock
    TournamentRepository tournamentRepository;

    @InjectMocks
    MatchService matchService;

    // --- helpers ---

    private Team buildTeam(String name) {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("user");
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName(name);
        team.setOwner(owner);
        team.setCreatedAt(LocalDateTime.now());
        return team;
    }

    private Match buildMatch(Team teamA, Team teamB, MatchStatus status) {
        Tournament tournament = new Tournament();
        tournament.setId(UUID.randomUUID());
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setTournament(tournament);
        match.setTeamA(teamA);
        match.setTeamB(teamB);
        match.setStatus(status);
        match.setRound(1);
        match.setPosition(1);
        return match;
    }

    // --- tests ---

    @Test
    void recordResult_setsWinnerCompletedAndAdvances() {
        Team teamA = buildTeam("TeamA");
        Team teamB = buildTeam("TeamB");
        Match match = buildMatch(teamA, teamB, MatchStatus.SCHEDULED);

        Match nextMatch = buildMatch(null, null, MatchStatus.PENDING);
        nextMatch.setRound(2);
        match.setNextMatch(nextMatch);
        match.setNextMatchPos(1);

        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));

        MatchResponse response = matchService.recordResult(match.getId(), teamA.getId());

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.winner().id()).isEqualTo(teamA.getId());
        assertThat(nextMatch.getTeamA()).isEqualTo(teamA);
    }

    @Test
    void recordResult_finalMatch_setsTournamentFinished() {
        Team teamA = buildTeam("TeamA");
        Team teamB = buildTeam("TeamB");
        Match finalMatch = buildMatch(teamA, teamB, MatchStatus.SCHEDULED);
        finalMatch.setNextMatch(null); // this is the final

        when(matchRepository.findById(finalMatch.getId())).thenReturn(Optional.of(finalMatch));

        matchService.recordResult(finalMatch.getId(), teamB.getId());

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.FINISHED);
    }

    @Test
    void recordResult_invalidWinner_throwsBusinessException() {
        Team teamA = buildTeam("TeamA");
        Team teamB = buildTeam("TeamB");
        Match match = buildMatch(teamA, teamB, MatchStatus.SCHEDULED);

        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));

        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> matchService.recordResult(match.getId(), randomId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("participating teams");
    }

    @Test
    void recordResult_alreadyCompleted_throwsBusinessException() {
        Team teamA = buildTeam("TeamA");
        Team teamB = buildTeam("TeamB");
        Match match = buildMatch(teamA, teamB, MatchStatus.COMPLETED);

        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.recordResult(match.getId(), teamA.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already");
    }

    @Test
    void recordResult_byeMatch_throwsBusinessException() {
        Team teamA = buildTeam("TeamA");
        Match match = buildMatch(teamA, null, MatchStatus.BYE);

        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.recordResult(match.getId(), teamA.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BYE");
    }

    @Test
    void recordResult_advancesToTeamBSlot() {
        Team teamA = buildTeam("TeamA");
        Team teamB = buildTeam("TeamB");
        Match match = buildMatch(teamA, teamB, MatchStatus.SCHEDULED);

        Match nextMatch = buildMatch(null, null, MatchStatus.PENDING);
        nextMatch.setRound(2);
        match.setNextMatch(nextMatch);
        match.setNextMatchPos(2); // winner goes to slot 2

        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));

        matchService.recordResult(match.getId(), teamA.getId());

        assertThat(nextMatch.getTeamB()).isEqualTo(teamA);
        assertThat(nextMatch.getTeamA()).isNull();
        assertThat(nextMatch.getStatus()).isNotEqualTo(MatchStatus.SCHEDULED);
    }

    @Test
    void recordResult_schedulesNextMatchWhenBothTeamsAssigned() {
        Team teamA = buildTeam("TeamA");
        Team teamB = buildTeam("TeamB");
        Match match = buildMatch(teamA, teamB, MatchStatus.SCHEDULED);

        Team existingOpponent = buildTeam("Opponent");
        Match nextMatch = buildMatch(existingOpponent, null, MatchStatus.PENDING);
        nextMatch.setRound(2);
        match.setNextMatch(nextMatch);
        match.setNextMatchPos(2); // winner fills slot 2, slot 1 already filled

        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));

        matchService.recordResult(match.getId(), teamA.getId());

        assertThat(nextMatch.getTeamA()).isEqualTo(existingOpponent);
        assertThat(nextMatch.getTeamB()).isEqualTo(teamA);
        assertThat(nextMatch.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
    }
}
