package com.cfval.tournament_manager.repository;

import com.cfval.tournament_manager.model.Registration;
import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {
    boolean existsByTournamentAndTeam(Tournament tournament, Team team);
    long countByTournament(Tournament tournament);
    List<Registration> findByTournament(Tournament tournament);
}
