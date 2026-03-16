package com.cfval.tournament_manager.repository;

import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    List<Team> findByOwner(User owner);
    boolean existsByOwnerAndName(User owner, String name);
}
