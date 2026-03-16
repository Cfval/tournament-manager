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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock TeamRepository teamRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock UserRepository userRepository;
    @Mock TournamentService tournamentService;

    @InjectMocks TeamService service;

    private User owner;
    private User otherUser;
    private User adminUser;
    private Team team;
    private Tournament openTournament;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("owner");
        owner.setRole(Role.USER);

        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        otherUser.setRole(Role.USER);

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setUsername("admin");
        adminUser.setRole(Role.ADMIN);

        team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("Alpha Team");
        team.setOwner(owner);
        team.setCreatedAt(LocalDateTime.now());

        openTournament = new Tournament();
        openTournament.setId(UUID.randomUUID());
        openTournament.setName("Test Cup");
        openTournament.setStatus(TournamentStatus.OPEN);
        openTournament.setMaxTeams(8);
        openTournament.setStartDate(LocalDateTime.now().plusDays(10));
        openTournament.setCreatedBy(adminUser);
    }

    // --- createTeam ---

    @Test
    void createTeam_savesAndReturnsTeamResponse() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(teamRepository.save(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        TeamResponse response = service.createTeam(new CreateTeamRequest("Alpha Team"), "owner");

        assertThat(response.name()).isEqualTo("Alpha Team");
        assertThat(response.ownerUsername()).isEqualTo("owner");
        verify(teamRepository).save(any(Team.class));
    }

    // --- findMyTeams ---

    @Test
    void findMyTeams_returnsOnlyOwnerTeams() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(teamRepository.findByOwner(owner)).thenReturn(List.of(team));

        List<TeamResponse> teams = service.findMyTeams("owner");

        assertThat(teams).hasSize(1);
        assertThat(teams.get(0).name()).isEqualTo("Alpha Team");
    }

    // --- registerTeam ---

    @Test
    void registerTeam_ownerCanRegisterOwnTeam() {
        UUID tournamentId = openTournament.getId();
        RegisterTeamRequest request = new RegisterTeamRequest(team.getId());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(registrationRepository.existsByTournamentAndTeam(openTournament, team)).thenReturn(false);
        when(registrationRepository.countByTournament(openTournament)).thenReturn(2L);
        when(registrationRepository.save(any())).thenAnswer(inv -> {
            Registration r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRegisteredAt(LocalDateTime.now());
            return r;
        });

        RegistrationResponse response = service.registerTeam(tournamentId, request, "owner");

        assertThat(response.tournamentId()).isEqualTo(tournamentId);
        assertThat(response.team().id()).isEqualTo(team.getId());
        verify(registrationRepository).save(any(Registration.class));
    }

    @Test
    void registerTeam_adminCanRegisterAnyTeam() {
        UUID tournamentId = openTournament.getId();
        RegisterTeamRequest request = new RegisterTeamRequest(team.getId());

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(registrationRepository.existsByTournamentAndTeam(openTournament, team)).thenReturn(false);
        when(registrationRepository.countByTournament(openTournament)).thenReturn(0L);
        when(registrationRepository.save(any())).thenAnswer(inv -> {
            Registration r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRegisteredAt(LocalDateTime.now());
            return r;
        });

        RegistrationResponse response = service.registerTeam(tournamentId, request, "admin");

        assertThat(response).isNotNull();
        verify(registrationRepository).save(any(Registration.class));
    }

    @Test
    void registerTeam_nonOwnerCannotRegisterOthersTeam() {
        UUID tournamentId = openTournament.getId();
        RegisterTeamRequest request = new RegisterTeamRequest(team.getId());

        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service.registerTeam(tournamentId, request, "other"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("your own team");
    }

    @Test
    void registerTeam_whenTournamentNotOpen_throwsBusinessException() {
        openTournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
        UUID tournamentId = openTournament.getId();
        RegisterTeamRequest request = new RegisterTeamRequest(team.getId());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service.registerTeam(tournamentId, request, "owner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not open for registration");
    }

    @Test
    void registerTeam_whenAlreadyRegistered_throwsConflictException() {
        UUID tournamentId = openTournament.getId();
        RegisterTeamRequest request = new RegisterTeamRequest(team.getId());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(registrationRepository.existsByTournamentAndTeam(openTournament, team)).thenReturn(true);

        assertThatThrownBy(() -> service.registerTeam(tournamentId, request, "owner"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerTeam_whenTournamentFull_throwsBusinessException() {
        UUID tournamentId = openTournament.getId();
        openTournament.setMaxTeams(4);
        RegisterTeamRequest request = new RegisterTeamRequest(team.getId());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
        when(registrationRepository.existsByTournamentAndTeam(openTournament, team)).thenReturn(false);
        when(registrationRepository.countByTournament(openTournament)).thenReturn(4L);

        assertThatThrownBy(() -> service.registerTeam(tournamentId, request, "owner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("full");
    }

    @Test
    void registerTeam_whenTeamNotFound_throwsResourceNotFoundException() {
        UUID tournamentId = openTournament.getId();
        UUID unknownTeamId = UUID.randomUUID();
        RegisterTeamRequest request = new RegisterTeamRequest(unknownTeamId);

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(tournamentService.loadTournament(tournamentId)).thenReturn(openTournament);
        when(teamRepository.findById(unknownTeamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerTeam(tournamentId, request, "owner"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
