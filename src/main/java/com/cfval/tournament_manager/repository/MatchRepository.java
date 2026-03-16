package com.cfval.tournament_manager.repository;

import com.cfval.tournament_manager.model.Match;
import com.cfval.tournament_manager.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByTournamentOrderByRoundAscPositionAsc(Tournament tournament);
}
