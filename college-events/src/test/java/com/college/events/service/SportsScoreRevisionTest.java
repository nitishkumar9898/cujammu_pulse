package com.college.events.service;

import com.college.events.dto.SportsDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SportsScoreRevisionTest {
    @Test
    void liveMatchWritesCreatedAndAcceptedCorrectionSnapshots() {
        SportsLiveMatchRepository matches = mock(SportsLiveMatchRepository.class);
        SportsMatchScoreRevisionRepository revisions = mock(SportsMatchScoreRevisionRepository.class);
        SportsScoreCommandReceiptRepository commandReceipts = mock(SportsScoreCommandReceiptRepository.class);
        SportsCommentaryRepository commentary = mock(SportsCommentaryRepository.class);
        DomainAccess access = mock(DomainAccess.class);
        FeatureFlagService flags = mock(FeatureFlagService.class);
        Event event = Event.builder().id(7L).status(EventStatus.APPROVED).build();
        when(access.requireEventManager(7L)).thenReturn(event);
        when(matches.saveAndFlush(any(SportsLiveMatch.class))).thenAnswer(invocation -> {
            SportsLiveMatch m = invocation.getArgument(0);
            if (m.getId() == null) m.setId(10L);
            else m.setRevision(m.getRevision() + 1L);
            return m;
        });
        LiveSportsScoreService service = new LiveSportsScoreService(matches, revisions, commandReceipts, commentary, access, flags);
        String createKey = UUID.randomUUID().toString();
        service.createMatch(new SportsDtos.CreateMatch(7L, "football", "Inter-department", "CS", "EE", "Ground 1", createKey));
        SportsLiveMatch stored = SportsLiveMatch.builder().id(10L).eventId(7L).teamA("CS").teamB("EE")
                .sportType("FOOTBALL").tournamentName("Inter-department").status("SCHEDULED").build();
        when(matches.findForUpdate(10L)).thenReturn(Optional.of(stored));
        String updateKey = UUID.randomUUID().toString();
        service.updateScore(10L, new SportsDtos.ScoreUpdate("1", "0", "LIVE", "CS ahead", null, null, 0L, updateKey));
        when(commandReceipts.findByAggregateTypeAndEventIdAndRequestKey("LIVE_MATCH", 7L, updateKey))
                .thenReturn(Optional.of(SportsScoreCommandReceipt.builder().aggregateType("LIVE_MATCH").aggregateId(10L)
                        .eventId(7L).requestKey(updateKey).payloadHash(hashForTest("1", "0", "LIVE", "CS ahead", null, null, null))
                        .acceptedRevision(1L).build()));
        service.updateScore(10L, new SportsDtos.ScoreUpdate("1", "0", "LIVE", "CS ahead", null, null, 1L, updateKey));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.updateScore(10L, new SportsDtos.ScoreUpdate("2", "0", "LIVE", "CS ahead", null, null, 1L, updateKey)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.updateScore(10L, new SportsDtos.ScoreUpdate("1", "0", "SUSPENDED", "Rain", null, null, 1L, UUID.randomUUID().toString())));
        String correctionKey = UUID.randomUUID().toString();
        service.updateScore(10L, new SportsDtos.ScoreUpdate("1", "0", "SUSPENDED", "Rain", null, null, 1L, correctionKey, "Rain interruption"));

        var captured = org.mockito.ArgumentCaptor.forClass(SportsMatchScoreRevision.class);
        verify(revisions, times(3)).saveAndFlush(captured.capture());
        assertEquals("CREATED", captured.getAllValues().get(0).getChangeType());
        assertEquals("CORRECTION", captured.getAllValues().get(1).getChangeType());
        assertEquals("CORRECTION", captured.getAllValues().get(2).getChangeType());
        assertEquals(2L, captured.getAllValues().get(2).getMatchRevision());
        assertEquals(correctionKey, captured.getAllValues().get(2).getRequestKey());
        assertEquals("Rain interruption", captured.getAllValues().get(2).getChangeReason());
    }

    @Test
    void fixtureWritesImmutableRevisionForAcceptedScore() {
        MatchFixtureRepository fixtures = mock(MatchFixtureRepository.class);
        EventRepository events = mock(EventRepository.class);
        FixtureScoreRevisionRepository revisions = mock(FixtureScoreRevisionRepository.class);
        SportsScoreCommandReceiptRepository commandReceipts = mock(SportsScoreCommandReceiptRepository.class);
        TeamRepository teams = mock(TeamRepository.class);
        DomainAccess access = mock(DomainAccess.class);
        FeatureFlagService flags = mock(FeatureFlagService.class);
        Event event = Event.builder().id(9L).status(EventStatus.APPROVED).build();
        when(access.requireEventManager(9L)).thenReturn(event);
        when(teams.findById(1L)).thenReturn(Optional.of(Team.builder().id(1L).name("A").event(event).build()));
        when(teams.findById(2L)).thenReturn(Optional.of(Team.builder().id(2L).name("B").event(event).build()));
        when(teams.findById(99L)).thenReturn(Optional.of(Team.builder().id(99L).name("Foreign")
                .event(Event.builder().id(10L).status(EventStatus.APPROVED).build()).build()));
        when(fixtures.saveAndFlush(any(MatchFixture.class))).thenAnswer(invocation -> {
            MatchFixture f = invocation.getArgument(0);
            if (f.getId() == null) f.setId(12L);
            else f.setRevision(f.getRevision() + 1L);
            return f;
        });
        TournamentBracketService service = new TournamentBracketService(fixtures, events, revisions, commandReceipts, teams, access, flags);
        String createKey = UUID.randomUUID().toString();
        service.createFixture(new SportsDtos.CreateFixture(9L, "Cup", 1, "Semi", 1, 1L, "A", 2L, "B",
                LocalDateTime.now().plusHours(1), "Court", createKey));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.createFixture(
                new SportsDtos.CreateFixture(9L, "Cup", 1, "Semi", 2, 1L, "Spoofed A", 2L, "B",
                        LocalDateTime.now().plusHours(1), "Court", UUID.randomUUID().toString())));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.createFixture(
                new SportsDtos.CreateFixture(9L, "Cup", 1, "Semi", 2, 1L, "A", 99L, "Foreign",
                        LocalDateTime.now().plusHours(1), "Court", UUID.randomUUID().toString())));
        MatchFixture stored = MatchFixture.builder().id(12L).eventId(9L).team1Id(1L).team1Name("A")
                .team2Id(2L).team2Name("B").status("SCHEDULED").build();
        when(fixtures.findForUpdate(12L)).thenReturn(Optional.of(stored));
        String updateKey = UUID.randomUUID().toString();
        service.updateScore(12L, new SportsDtos.FixtureScoreUpdate("2", "1", "LIVE", null, null, 0L, updateKey));
        String correctionKey = UUID.randomUUID().toString();
        service.updateScore(12L, new SportsDtos.FixtureScoreUpdate("2", "1", "SUSPENDED", null, null, 1L, correctionKey, "Weather interruption"));

        var captured = org.mockito.ArgumentCaptor.forClass(FixtureScoreRevision.class);
        verify(revisions, times(3)).saveAndFlush(captured.capture());
        assertEquals("CREATED", captured.getAllValues().get(0).getChangeType());
        assertEquals("CORRECTION", captured.getAllValues().get(1).getChangeType());
        assertEquals("CORRECTION", captured.getAllValues().get(2).getChangeType());
        assertEquals(2L, captured.getAllValues().get(2).getFixtureRevision());
        assertEquals("SUSPENDED", captured.getAllValues().get(2).getStatus());
        assertEquals("Weather interruption", captured.getAllValues().get(2).getChangeReason());
    }

    @Test
    void fixtureResultNeedsAnExplicitWinnerAndMovesThroughProvisionalBeforeFinal() {
        MatchFixtureRepository fixtures = mock(MatchFixtureRepository.class);
        EventRepository events = mock(EventRepository.class);
        FixtureScoreRevisionRepository revisions = mock(FixtureScoreRevisionRepository.class);
        SportsScoreCommandReceiptRepository receipts = mock(SportsScoreCommandReceiptRepository.class);
        DomainAccess access = mock(DomainAccess.class);
        FeatureFlagService flags = mock(FeatureFlagService.class);
        Event event = Event.builder().id(15L).status(EventStatus.APPROVED).build();
        when(access.requireEventManager(15L)).thenReturn(event);
        MatchFixture stored = MatchFixture.builder().id(21L).eventId(15L).team1Id(101L).team1Name("A")
                .team2Id(102L).team2Name("B").scoreTeam1("1").scoreTeam2("0").status("LIVE").build();
        when(fixtures.findForUpdate(21L)).thenReturn(Optional.of(stored));
        when(fixtures.saveAndFlush(any(MatchFixture.class))).thenAnswer(invocation -> {
            MatchFixture fixture = invocation.getArgument(0); fixture.setRevision(fixture.getRevision() + 1); return fixture;
        });
        TournamentBracketService service = new TournamentBracketService(fixtures, events, revisions, receipts, mock(TeamRepository.class), access, flags);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.updateScore(21L,
                new SportsDtos.FixtureScoreUpdate("1", "0", "PROVISIONAL_RESULT", null, null, 0L, UUID.randomUUID().toString())));
        String provisionalKey = UUID.randomUUID().toString();
        var provisionalCommand = new SportsDtos.FixtureScoreUpdate("1", "0", "PROVISIONAL_RESULT", 101L, "A", 0L, provisionalKey);
        service.updateScore(21L, provisionalCommand);
        service.updateScore(21L, provisionalCommand); // exact retry after a lost response uses the original revision
        assertEquals("PROVISIONAL_RESULT", stored.getStatus());
        assertEquals(101L, stored.getWinnerId());
        assertEquals("A", stored.getWinnerName());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.updateScore(21L,
                new SportsDtos.FixtureScoreUpdate("1", "0", "PROVISIONAL_RESULT", 101L, "B", 1L, UUID.randomUUID().toString(), "wrong winner label")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.updateScore(21L,
                new SportsDtos.FixtureScoreUpdate("1", "0", "LIVE", 101L, "A", 1L, UUID.randomUUID().toString(), "winner on live state")));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.updateScore(21L,
                new SportsDtos.FixtureScoreUpdate("1", "0", "COMPLETED", 101L, "A", 1L, UUID.randomUUID().toString(), "bad status")));
        service.updateScore(21L, new SportsDtos.FixtureScoreUpdate("1", "0", "FINAL", 101L, "A", 1L,
                UUID.randomUUID().toString(), "Referee confirmation"));
        assertEquals("FINAL", stored.getStatus());
        var snapshots = org.mockito.ArgumentCaptor.forClass(FixtureScoreRevision.class);
        verify(revisions, times(2)).saveAndFlush(snapshots.capture());
        assertEquals("PROVISIONAL_RESULT", snapshots.getAllValues().get(0).getStatus());
        assertEquals("FINAL", snapshots.getAllValues().get(1).getStatus());
        assertEquals("Referee confirmation", snapshots.getAllValues().get(1).getChangeReason());
    }

    @Test
    void historyCannotCrossEventScope() {
        SportsLiveMatchRepository matches = mock(SportsLiveMatchRepository.class);
        SportsMatchScoreRevisionRepository revisions = mock(SportsMatchScoreRevisionRepository.class);
        SportsScoreCommandReceiptRepository commandReceipts = mock(SportsScoreCommandReceiptRepository.class);
        FeatureFlagService flags = mock(FeatureFlagService.class);
        when(flags.isFeatureEnabled("MODULE_LIVE_SPORTS")).thenReturn(true);
        SportsLiveMatch match = SportsLiveMatch.builder().id(20L).eventId(7L).build();
        when(matches.findById(20L)).thenReturn(Optional.of(match));
        LiveSportsScoreService service = new LiveSportsScoreService(matches, revisions, commandReceipts, mock(SportsCommentaryRepository.class), mock(DomainAccess.class), flags);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getScoreHistory(8L, 20L));
        verifyNoInteractions(revisions);
    }

    @Test
    void fixtureListIsPublicOnlyAfterEventApproval() {
        MatchFixtureRepository fixtures=mock(MatchFixtureRepository.class);
        EventRepository events=mock(EventRepository.class);
        FeatureFlagService flags=mock(FeatureFlagService.class);
        DomainAccess access=mock(DomainAccess.class);
        when(flags.isFeatureEnabled("MODULE_TOURNAMENTS")).thenReturn(true);
        when(events.findById(30L)).thenReturn(Optional.of(Event.builder().id(30L).status(EventStatus.APPROVED).build()));
        when(events.findById(31L)).thenReturn(Optional.of(Event.builder().id(31L).status(EventStatus.PENDING).build()));
        when(access.requireEventManager(31L)).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        TournamentBracketService service=new TournamentBracketService(fixtures,events,mock(FixtureScoreRevisionRepository.class),
                mock(SportsScoreCommandReceiptRepository.class),mock(TeamRepository.class),access,flags);
        when(fixtures.findByEventIdOrderByRoundNumberAscMatchNumberAsc(30L)).thenReturn(java.util.List.of());

        assertEquals(0,service.getFixturesForEvent(30L).size());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
                ()->service.getFixturesForEvent(31L));
        verify(fixtures,never()).findByEventIdOrderByRoundNumberAscMatchNumberAsc(31L);
    }

    private static String hashForTest(String... values) {
        try {
            String raw = String.join("|", java.util.Arrays.stream(values).map(v -> v == null ? "" : v.trim()).toList());
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
