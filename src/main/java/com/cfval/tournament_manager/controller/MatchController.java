package com.cfval.tournament_manager.controller;

import com.cfval.tournament_manager.dto.request.RecordResultRequest;
import com.cfval.tournament_manager.dto.response.MatchResponse;
import com.cfval.tournament_manager.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/{id}/result")
    @PreAuthorize("hasRole('ADMIN')")
    public MatchResponse recordResult(
            @PathVariable UUID id,
            @Valid @RequestBody RecordResultRequest request) {
        return matchService.recordResult(id, request.winnerId());
    }
}
