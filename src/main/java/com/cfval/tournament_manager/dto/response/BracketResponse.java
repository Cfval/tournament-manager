package com.cfval.tournament_manager.dto.response;

import java.util.List;
import java.util.UUID;

public record BracketResponse(
        UUID tournamentId,
        int totalRounds,
        List<MatchResponse> matches
) {}
