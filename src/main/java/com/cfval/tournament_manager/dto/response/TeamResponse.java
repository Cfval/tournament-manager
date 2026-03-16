package com.cfval.tournament_manager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamResponse(
        UUID id,
        String name,
        String ownerUsername,
        LocalDateTime createdAt
) {}
