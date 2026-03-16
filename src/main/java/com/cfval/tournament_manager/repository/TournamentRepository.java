package com.cfval.tournament_manager.repository;

import com.cfval.tournament_manager.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {
}
