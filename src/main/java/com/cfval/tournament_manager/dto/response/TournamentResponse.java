package com.cfval.tournament_manager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TournamentResponse(
        UUID id,
        String name,
        String status,
        int maxTeams,
        LocalDateTime startDate,
        String createdBy,
        LocalDateTime createdAt,
        long registeredTeams
) {}
