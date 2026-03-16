package com.cfval.tournament_manager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegistrationResponse(
        UUID id,
        UUID tournamentId,
        TeamResponse team,
        LocalDateTime registeredAt
) {}
