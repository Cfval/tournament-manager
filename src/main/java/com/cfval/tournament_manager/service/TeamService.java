package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.request.CreateTeamRequest;
import com.cfval.tournament_manager.dto.request.RegisterTeamRequest;
import com.cfval.tournament_manager.dto.response.RegistrationResponse;
import com.cfval.tournament_manager.dto.response.TeamResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.exception.ConflictException;
import com.cfval.tournament_manager.exception.ResourceNotFoundException;
import com.cfval.tournament_manager.model.Registration;
import com.cfval.tournament_manager.model.Role;
import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.RegistrationRepository;
import com.cfval.tournament_manager.repository.TeamRepository;
import com.cfval.tournament_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final TournamentService tournamentService;

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request, String ownerUsername) {
        User owner = loadUser(ownerUsername);

        Team team = new Team();
        team.setName(request.name());
        team.setOwner(owner);

        teamRepository.save(team);
        return toTeamResponse(team);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> findMyTeams(String username) {
        User user = loadUser(username);
        return teamRepository.findByOwner(user).stream()
                .map(this::toTeamResponse)
                .toList();
    }

    @Transactional
    public RegistrationResponse registerTeam(UUID tournamentId, RegisterTeamRequest request, String username) {
        User user = loadUser(username);
        Tournament tournament = tournamentService.loadTournament(tournamentId);
        Team team = teamRepository.findById(request.teamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + request.teamId()));

        validateRegistration(user, tournament, team);

        Registration registration = new Registration();
        registration.setTournament(tournament);
        registration.setTeam(team);

        registrationRepository.save(registration);
        return toRegistrationResponse(registration);
    }

    private void validateRegistration(User user, Tournament tournament, Team team) {
        if (tournament.getStatus() != TournamentStatus.OPEN) {
            throw new BusinessException("Tournament is not open for registration");
        }
        if (!team.getOwner().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new BusinessException("You can only register your own team");
        }
        if (registrationRepository.existsByTournamentAndTeam(tournament, team)) {
            throw new ConflictException("Team is already registered in this tournament");
        }
        long registered = registrationRepository.countByTournament(tournament);
        if (registered >= tournament.getMaxTeams()) {
            throw new BusinessException("Tournament is full (" + tournament.getMaxTeams() + " teams)");
        }
    }

    // --- helpers ---

    private User loadUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    TeamResponse toTeamResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getOwner().getUsername(),
                team.getCreatedAt()
        );
    }

    private RegistrationResponse toRegistrationResponse(Registration registration) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getTournament().getId(),
                toTeamResponse(registration.getTeam()),
                registration.getRegisteredAt()
        );
    }
}
