package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.request.CreateTournamentRequest;
import com.cfval.tournament_manager.dto.response.TournamentResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.exception.ResourceNotFoundException;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.RegistrationRepository;
import com.cfval.tournament_manager.repository.TournamentRepository;
import com.cfval.tournament_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock TournamentRepository tournamentRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock UserRepository userRepository;

    @InjectMocks TournamentService service;

    private User admin;
    private Tournament openTournament;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");

        openTournament = new Tournament();
        openTournament.setId(UUID.randomUUID());
        openTournament.setName("Test Cup");
        openTournament.setStatus(TournamentStatus.OPEN);
        openTournament.setMaxTeams(8);
        openTournament.setStartDate(LocalDateTime.now().plusDays(10));
        openTournament.setCreatedBy(admin);
        openTournament.setCreatedAt(LocalDateTime.now());
    }

    // --- create ---

    @Test
    void create_savesTournamentAndReturnsResponse() {
        CreateTournamentRequest request = new CreateTournamentRequest(
                "Test Cup", 8, LocalDateTime.now().plusDays(10));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(tournamentRepository.save(any())).thenAnswer(inv -> {
            Tournament t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(LocalDateTime.now());
            return t;
        });

        TournamentResponse response = service.create(request, "admin");

        assertThat(response.name()).isEqualTo("Test Cup");
        assertThat(response.maxTeams()).isEqualTo(8);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.createdBy()).isEqualTo("admin");
        verify(tournamentRepository).save(any(Tournament.class));
    }

    // --- closeRegistrations ---

    @Test
    void closeRegistrations_whenOpenAndEnoughTeams_changesStatusToRegistrationClosed() {
        when(tournamentRepository.findById(openTournament.getId())).thenReturn(Optional.of(openTournament));
        when(registrationRepository.countByTournament(openTournament)).thenReturn(4L);
        when(tournamentRepository.save(openTournament)).thenReturn(openTournament);

        TournamentResponse response = service.closeRegistrations(openTournament.getId());

        assertThat(response.status()).isEqualTo("REGISTRATION_CLOSED");
        verify(tournamentRepository).save(openTournament);
    }

    @Test
    void closeRegistrations_whenNotOpen_throwsBusinessException() {
        openTournament.setStatus(TournamentStatus.IN_PROGRESS);
        when(tournamentRepository.findById(openTournament.getId())).thenReturn(Optional.of(openTournament));

        assertThatThrownBy(() -> service.closeRegistrations(openTournament.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not OPEN");
    }

    @Test
    void closeRegistrations_withFewerThanTwoTeams_throwsBusinessException() {
        when(tournamentRepository.findById(openTournament.getId())).thenReturn(Optional.of(openTournament));
        when(registrationRepository.countByTournament(openTournament)).thenReturn(1L);

        assertThatThrownBy(() -> service.closeRegistrations(openTournament.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least 2 teams");
    }

    @Test
    void closeRegistrations_withZeroTeams_throwsBusinessException() {
        when(tournamentRepository.findById(openTournament.getId())).thenReturn(Optional.of(openTournament));
        when(registrationRepository.countByTournament(openTournament)).thenReturn(0L);

        assertThatThrownBy(() -> service.closeRegistrations(openTournament.getId()))
                .isInstanceOf(BusinessException.class);
    }

    // --- findById ---

    @Test
    void findById_whenExists_returnsResponse() {
        when(tournamentRepository.findById(openTournament.getId())).thenReturn(Optional.of(openTournament));
        when(registrationRepository.countByTournament(openTournament)).thenReturn(3L);

        TournamentResponse response = service.findById(openTournament.getId());

        assertThat(response.id()).isEqualTo(openTournament.getId());
        assertThat(response.registeredTeams()).isEqualTo(3L);
    }

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(tournamentRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
