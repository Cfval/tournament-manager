package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.request.CreateTournamentRequest;
import com.cfval.tournament_manager.dto.response.TournamentResponse;
import com.cfval.tournament_manager.dto.response.TournamentSummaryResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.exception.ConflictException;
import com.cfval.tournament_manager.exception.ResourceNotFoundException;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.RegistrationRepository;
import com.cfval.tournament_manager.repository.TournamentRepository;
import com.cfval.tournament_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;

    @Transactional
    public TournamentResponse create(CreateTournamentRequest request, String creatorUsername) {
        User creator = loadUser(creatorUsername);

        if (tournamentRepository.existsByNameAndStatusNot(request.name(), TournamentStatus.FINISHED)) {
            throw new ConflictException("An active tournament named \"" + request.name() + "\" already exists");
        }

        Tournament tournament = new Tournament();
        tournament.setName(request.name());
        tournament.setMaxTeams(request.maxTeams());
        tournament.setStartDate(request.startDate());
        tournament.setCreatedBy(creator);
        tournament.setStatus(TournamentStatus.OPEN);

        tournamentRepository.save(tournament);
        return toResponse(tournament, 0);
    }

    @Transactional(readOnly = true)
    public List<TournamentSummaryResponse> findAll() {
        return tournamentRepository.findAll().stream()
                .map(t -> toSummary(t, registrationRepository.countByTournament(t)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentResponse findById(UUID id) {
        Tournament tournament = loadTournament(id);
        long count = registrationRepository.countByTournament(tournament);
        return toResponse(tournament, count);
    }

    @Transactional
    public TournamentResponse closeRegistrations(UUID id) {
        Tournament tournament = loadTournament(id);

        if (tournament.getStatus() != TournamentStatus.OPEN) {
            throw new BusinessException(
                    "Cannot close registrations: tournament is not OPEN (current status: " + tournament.getStatus() + ")"
            );
        }

        long registered = registrationRepository.countByTournament(tournament);
        if (registered < 2) {
            throw new BusinessException(
                    "Cannot close registrations: at least 2 teams must be registered (current: " + registered + ")"
            );
        }

        tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
        tournamentRepository.save(tournament);
        return toResponse(tournament, registered);
    }

    // --- helpers ---

    Tournament loadTournament(UUID id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found: " + id));
    }

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private TournamentResponse toResponse(Tournament t, long registeredTeams) {
        return new TournamentResponse(
                t.getId(),
                t.getName(),
                t.getStatus().name(),
                t.getMaxTeams(),
                t.getStartDate(),
                t.getCreatedBy().getUsername(),
                t.getCreatedAt(),
                registeredTeams
        );
    }

    private TournamentSummaryResponse toSummary(Tournament t, long registeredTeams) {
        return new TournamentSummaryResponse(
                t.getId(),
                t.getName(),
                t.getStatus().name(),
                t.getMaxTeams(),
                t.getStartDate(),
                registeredTeams
        );
    }
}
