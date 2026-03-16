package com.cfval.tournament_manager.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterTeamRequest(
        @NotNull UUID teamId
) {}
