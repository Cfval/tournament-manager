package com.cfval.tournament_manager.controller;

import com.cfval.tournament_manager.dto.request.CreateTeamRequest;
import com.cfval.tournament_manager.dto.request.RegisterTeamRequest;
import com.cfval.tournament_manager.dto.response.RegistrationResponse;
import com.cfval.tournament_manager.dto.response.TeamResponse;
import com.cfval.tournament_manager.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping("/api/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(@Valid @RequestBody CreateTeamRequest request, Principal principal) {
        return teamService.createTeam(request, principal.getName());
    }

    @GetMapping("/api/teams")
    public List<TeamResponse> getMyTeams(Principal principal) {
        return teamService.findMyTeams(principal.getName());
    }

    @PostMapping("/api/tournaments/{tournamentId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse registerTeam(
            @PathVariable UUID tournamentId,
            @Valid @RequestBody RegisterTeamRequest request,
            Principal principal) {
        return teamService.registerTeam(tournamentId, request, principal.getName());
    }
}
