package com.cfval.tournament_manager.dto.response;

public record AuthResponse(
        String token,
        String username,
        String role
) {}
