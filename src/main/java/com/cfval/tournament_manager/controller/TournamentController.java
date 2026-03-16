package com.cfval.tournament_manager.controller;

import com.cfval.tournament_manager.dto.request.CreateTournamentRequest;
import com.cfval.tournament_manager.dto.response.TournamentResponse;
import com.cfval.tournament_manager.dto.response.TournamentSummaryResponse;
import com.cfval.tournament_manager.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public List<TournamentSummaryResponse> listAll() {
        return tournamentService.findAll();
    }

    @GetMapping("/{id}")
    public TournamentResponse getById(@PathVariable UUID id) {
        return tournamentService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TournamentResponse create(@Valid @RequestBody CreateTournamentRequest request, Principal principal) {
        return tournamentService.create(request, principal.getName());
    }

    @PutMapping("/{id}/close-registrations")
    @PreAuthorize("hasRole('ADMIN')")
    public TournamentResponse closeRegistrations(@PathVariable UUID id) {
        return tournamentService.closeRegistrations(id);
    }
}
