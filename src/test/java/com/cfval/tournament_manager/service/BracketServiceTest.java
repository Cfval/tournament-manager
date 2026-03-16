package com.cfval.tournament_manager.service;

import com.cfval.tournament_manager.dto.response.BracketResponse;
import com.cfval.tournament_manager.dto.response.MatchResponse;
import com.cfval.tournament_manager.exception.BusinessException;
import com.cfval.tournament_manager.model.Match;
import com.cfval.tournament_manager.model.Registration;
import com.cfval.tournament_manager.model.Team;
import com.cfval.tournament_manager.model.Tournament;
import com.cfval.tournament_manager.model.TournamentStatus;
import com.cfval.tournament_manager.model.User;
import com.cfval.tournament_manager.repository.MatchRepository;
import com.cfval.tournament_manager.repository.RegistrationRepository;
import com.cfval.tournament_manager.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BracketServiceTest {

    @Mock
    TournamentService tournamentService;
    @Mock
    RegistrationRepository registrationRepository;
    @Mock
    MatchRepository matchRepository;
    @Mock
    TournamentRepository tournamentRepository;

    @InjectMocks
    BracketService bracketService;

    // --- helpers ---

    private Tournament buildTournament(UUID id, TournamentStatus status) {
        Tournament t = new Tournament();
        t.setId(id);
        t.setStatus(status);
        User creator = new User();
        creator.setId(UUID.randomUUID());
        creator.setUsername("admin");
        t.setCreatedBy(creator);
        return t;
    }

    private List<Registration> buildRegistrations(Tournament tournament, int count) {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUsername("owner");
        List<Registration> regs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Team team = new Team();
            team.setId(UUID.randomUUID());
            team.setName("Team" + i);
            team.setOwner(owner);
            team.setCreatedAt(LocalDateTime.now());
            Registration reg = new Registration();
            reg.setTournament(tournament);
            reg.setTeam(team);
            regs.add(reg);
        }
        return regs;
    }

    private void stubSaveAll() {
        when(matchRepository.saveAll(any())).thenReturn(List.of());
    }

    // --- tests ---

    @Test
    void generateBracket_power2_createsCorrectMatchStructure() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);
        when(matchRepository.existsByTournament(tournament)).thenReturn(false);
        when(registrationRepository.findByTournament(tournament)).thenReturn(buildRegistrations(tournament, 4));
        stubSaveAll();

        BracketResponse response = bracketService.generateBracket(tid);

        // 4 teams: p=4, rounds=2 → 2 round-1 matches + 1 final = 3 total
        assertThat(response.totalRounds()).isEqualTo(2);
        assertThat(response.matches()).hasSize(3);

        List<MatchResponse> round1 = response.matches().stream()
                .filter(m -> m.round() == 1).toList();
        assertThat(round1).hasSize(2);
        assertThat(round1).allMatch(m -> m.status().equals("SCHEDULED"));
        assertThat(round1).allMatch(m -> m.teamA() != null && m.teamB() != null);
    }

    @Test
    void generateBracket_nonPowerOf2_createsByesCorrectly() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);
        when(matchRepository.existsByTournament(tournament)).thenReturn(false);
        when(registrationRepository.findByTournament(tournament)).thenReturn(buildRegistrations(tournament, 6));
        stubSaveAll();

        BracketResponse response = bracketService.generateBracket(tid);

        // 6 teams: p=8, byes=2 → 4+2+1 = 7 matches, 2 BYEs in round 1
        assertThat(response.matches()).hasSize(7);

        List<MatchResponse> byes = response.matches().stream()
                .filter(m -> m.status().equals("BYE")).toList();
        assertThat(byes).hasSize(2);
        byes.forEach(bye -> {
            assertThat(bye.teamA()).isNotNull();
            assertThat(bye.teamB()).isNull();
            assertThat(bye.winner()).isNotNull();
            assertThat(bye.winner().id()).isEqualTo(bye.teamA().id());
        });
    }

    @Test
    void generateBracket_2teams_createsSingleFinalMatch() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);
        when(matchRepository.existsByTournament(tournament)).thenReturn(false);
        when(registrationRepository.findByTournament(tournament)).thenReturn(buildRegistrations(tournament, 2));
        stubSaveAll();

        BracketResponse response = bracketService.generateBracket(tid);

        assertThat(response.totalRounds()).isEqualTo(1);
        assertThat(response.matches()).hasSize(1);
        MatchResponse match = response.matches().get(0);
        assertThat(match.status()).isEqualTo("SCHEDULED");
        assertThat(match.teamA()).isNotNull();
        assertThat(match.teamB()).isNotNull();
    }

    @Test
    void generateBracket_notRegistrationClosed_throwsBusinessException() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.OPEN);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);

        assertThatThrownBy(() -> bracketService.generateBracket(tid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REGISTRATION_CLOSED");
    }

    @Test
    void generateBracket_bracketAlreadyExists_throwsBusinessException() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);
        when(matchRepository.existsByTournament(tournament)).thenReturn(true);

        assertThatThrownBy(() -> bracketService.generateBracket(tid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already");
    }

    @Test
    void generateBracket_setsNextMatchLinks() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);
        when(matchRepository.existsByTournament(tournament)).thenReturn(false);
        when(registrationRepository.findByTournament(tournament)).thenReturn(buildRegistrations(tournament, 4));
        stubSaveAll();

        bracketService.generateBracket(tid);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Match>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(matchRepository).saveAll(captor.capture());

        List<Match> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);

        Match r1p1 = saved.stream()
                .filter(m -> m.getRound() == 1 && m.getPosition() == 1).findFirst().orElseThrow();
        Match r1p2 = saved.stream()
                .filter(m -> m.getRound() == 1 && m.getPosition() == 2).findFirst().orElseThrow();
        Match finalMatch = saved.stream()
                .filter(m -> m.getRound() == 2 && m.getPosition() == 1).findFirst().orElseThrow();

        assertThat(r1p1.getNextMatch()).isSameAs(finalMatch);
        assertThat(r1p1.getNextMatchPos()).isEqualTo(1);
        assertThat(r1p2.getNextMatch()).isSameAs(finalMatch);
        assertThat(r1p2.getNextMatchPos()).isEqualTo(2);
    }

    @Test
    void generateBracket_setsStatusToInProgress() {
        UUID tid = UUID.randomUUID();
        Tournament tournament = buildTournament(tid, TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentService.loadTournament(tid)).thenReturn(tournament);
        when(matchRepository.existsByTournament(tournament)).thenReturn(false);
        when(registrationRepository.findByTournament(tournament)).thenReturn(buildRegistrations(tournament, 4));
        stubSaveAll();

        bracketService.generateBracket(tid);

        ArgumentCaptor<Tournament> captor = ArgumentCaptor.forClass(Tournament.class);
        verify(tournamentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
    }
}
