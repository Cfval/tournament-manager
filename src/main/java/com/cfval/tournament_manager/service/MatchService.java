package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.response.MatchResponse;
import com.cfval.tournament_manager.dto.response.TeamResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.exception.ResourceNotFoundException;
import com.cfval.tournament_manager.model.Match;
import com.cfval.tournament_manager.model.MatchStatus;
import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.repository.MatchRepository;
import com.cfval.tournament_manager.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchService {

    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;

    public MatchResponse recordResult(UUID matchId, UUID winnerId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw new BusinessException("Match result already recorded");
        }
        if (match.getStatus() == MatchStatus.BYE) {
            throw new BusinessException("Cannot record result for a BYE match");
        }

        boolean winnerIsTeamA = match.getTeamA() != null && match.getTeamA().getId().equals(winnerId);
        boolean winnerIsTeamB = match.getTeamB() != null && match.getTeamB().getId().equals(winnerId);
        if (!winnerIsTeamA && !winnerIsTeamB) {
            throw new BusinessException("Winner must be one of the participating teams");
        }

        Team winner = winnerIsTeamA ? match.getTeamA() : match.getTeamB();
        match.setWinner(winner);
        match.setStatus(MatchStatus.COMPLETED);
        match.setPlayedAt(LocalDateTime.now());

        if (match.getNextMatch() != null) {
            Match nextMatch = match.getNextMatch();
            advanceToNextSlot(nextMatch, winner, match.getNextMatchPos());
            // Both slots filled → the next match can now be played
            if (nextMatch.getTeamA() != null && nextMatch.getTeamB() != null) {
                nextMatch.setStatus(MatchStatus.SCHEDULED);
            }
        } else {
            // This was the final — close the tournament
            Tournament tournament = match.getTournament();
            tournament.setStatus(TournamentStatus.FINISHED);
            tournamentRepository.save(tournament);
        }

        return toMatchResponse(match);
    }

    // --- private helpers ---

    private void advanceToNextSlot(Match match, Team team, int slot) {
        if (slot == 1) {
            match.setTeamA(team);
        } else {
            match.setTeamB(team);
        }
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
