package com.cfval.tournament_manager.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateTournamentRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @NotNull @Min(2) @Max(64) Integer maxTeams,
        @NotNull @Future(message = "Start date must be in the future") LocalDateTime startDate
) {}
