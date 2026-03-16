package com.cfval.tournament_manager.repository;

import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {
    boolean existsByNameAndStatusNot(String name, TournamentStatus status);
}
