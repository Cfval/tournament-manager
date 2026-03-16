package com.cfval.tournament_manager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        int round,
        int position,
        TeamResponse teamA,
        TeamResponse teamB,
        TeamResponse winner,
        String status,
        LocalDateTime playedAt
) {}
