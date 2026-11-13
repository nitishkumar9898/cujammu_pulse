package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class EventAttendanceReportServiceTest {
    EventRepository events = mock(EventRepository.class);
    RegistrationRepository registrations = mock(RegistrationRepository.class);
    EventQrAttendanceEvidenceRepository qr = mock(EventQrAttendanceEvidenceRepository.class);
    ManualAttendanceEvidenceRepository manual = mock(ManualAttendanceEvidenceRepository.class);
    DomainAccess access = mock(DomainAccess.class);
    FeatureFlagService flags = mock(FeatureFlagService.class);
    AuditService audit = mock(AuditService.class);
    EventOperationsService eventOperations = mock(EventOperationsService.class);
    EventAttendanceReportService service = new EventAttendanceReportService(events, registrations, qr, manual, access, flags, audit, eventOperations);

    @Test
    void csvSortsNaturalIdentifiersPreservesLeadingZerosAndDefendsFormulaCells() throws Exception {
        User organizer = user(1L, "organizer@example.invalid", Role.ORGANIZER, "STAFF");
        Event event = Event.builder().id(7L).title("Synthetic event").description("Description").organizer(organizer)
                .venue("Hall").eventDate(LocalDateTime.now().plusDays(1)).maxSeats(100).status(EventStatus.APPROVED).build();
        User ten = user(2L, "ten@example.invalid", Role.STUDENT, "CS-10");
        User two = user(3L, "two@example.invalid", Role.STUDENT, "CS-2");
        User leading = user(4L, "leading@example.invalid", Role.STUDENT, "0012");
        User formula = user(5L, "formula@example.invalid", Role.STUDENT, " \t=BAD");
        Registration rTen = registration(10L, event, ten, RegistrationStatus.REGISTERED);
        Registration rTwo = registration(11L, event, two, RegistrationStatus.REGISTERED);
        Registration rLeading = registration(12L, event, leading, RegistrationStatus.REGISTERED);
        Registration rFormula = registration(13L, event, formula, RegistrationStatus.REGISTERED);
        when(eventOperations.requireAttendanceExportCapability(7L, "CSV")).thenReturn(0L);
        when(access.currentUser()).thenReturn(organizer);
        when(events.findById(7L)).thenReturn(Optional.of(event));
        when(registrations.findByEventOrderByRegisteredAtAsc(event)).thenReturn(List.of(rTen, rFormula, rTwo, rLeading));
        when(qr.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eq(7L), any(Instant.class))).thenReturn(List.of());
        when(manual.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eq(7L), any(Instant.class))).thenReturn(List.of());

        String csv = new String(service.csv(7L), StandardCharsets.UTF_8);

        assertTrue(csv.indexOf("CS-2") < csv.indexOf("CS-10"));
        assertTrue(csv.contains("' \t=BAD"));
        assertTrue(csv.contains("# quality,PROVISIONAL"));
        assertTrue(csv.contains("# operationsRevision,0"));
        assertTrue(csv.contains("# timezone,UTC"));
        assertTrue(csv.contains("# sortVersion,cujammu-natural-identifier-v1"));
        assertTrue(csv.matches("(?s).*# checksum,[0-9a-f]{64}\\r\\n.*"));
        assertTrue(csv.contains("ABSENT"));
        assertFalse(csv.contains("email@example"));
    }

    @Test
    void oversizedSynchronousExportFailsBeforeLoadingAttendanceRows() {
        User organizer = user(1L, "organizer@example.invalid", Role.ORGANIZER, "STAFF");
        Event event = Event.builder().id(7L).title("Synthetic event").description("Description").organizer(organizer)
                .venue("Hall").eventDate(LocalDateTime.now().plusDays(1)).maxSeats(100).status(EventStatus.APPROVED).build();
        when(eventOperations.requireAttendanceExportCapability(7L, "CSV")).thenReturn(4L);
        when(events.findById(7L)).thenReturn(Optional.of(event));
        when(registrations.countByEvent(event)).thenReturn(5_001L);

        var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.csv(7L));

        assertEquals(413, failure.getStatusCode().value());
        verify(registrations, never()).findByEventOrderByRegisteredAtAsc(event);
        verify(audit, never()).logAction(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void xlsxContainsTypedIdentifierCellsAndStatusSheet() throws Exception {
        User organizer = user(1L, "organizer@example.invalid", Role.ORGANIZER, "STAFF");
        Event event = Event.builder().id(7L).title("Synthetic event").description("Description").organizer(organizer)
                .venue("Hall").eventDate(LocalDateTime.now().plusDays(1)).maxSeats(100).status(EventStatus.APPROVED).build();
        User student = user(2L, "student@example.invalid", Role.STUDENT, "0007");
        Registration registration = registration(10L, event, student, RegistrationStatus.REGISTERED);
        when(eventOperations.requireAttendanceExportCapability(7L, "XLSX")).thenReturn(0L);
        when(access.currentUser()).thenReturn(organizer);
        when(events.findById(7L)).thenReturn(Optional.of(event));
        when(registrations.findByEventOrderByRegisteredAtAsc(event)).thenReturn(List.of(registration));
        when(qr.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eq(7L), any(Instant.class))).thenReturn(List.of());
        when(manual.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eq(7L), any(Instant.class))).thenReturn(List.of());

        byte[] workbook = service.xlsx(7L);

        assertTrue(workbook.length > 1000);
        assertEquals((byte) 0x50, workbook[0]); // ZIP/XLSX signature
        assertEquals((byte) 0x4B, workbook[1]);
        try (var parsed = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(workbook))) {
            assertNotNull(parsed.getSheet("Report Manifest"));
            var manifest = parsed.getSheet("Report Manifest");
            assertEquals("PROVISIONAL", manifest.getRow(3).getCell(1).getStringCellValue());
            assertEquals("UTC", manifest.getRow(2).getCell(1).getStringCellValue());
            assertEquals("SHA-256", manifest.getRow(5).getCell(1).getStringCellValue());
            assertTrue(manifest.getRow(7).getCell(1).getStringCellValue().matches("[0-9a-f]{64}"));
        }
    }

    @Test
    void scanLogExportsOnlyAcceptedActionsSortedByVerifiedIdentifierAndAuditsFormat() throws Exception {
        User organizer = user(1L, "organizer@example.invalid", Role.ORGANIZER, "STAFF");
        User operator = user(9L, "private-operator@example.invalid", Role.STUDENT, "VOL-9");
        Event event = Event.builder().id(7L).title("Synthetic event").description("Description").organizer(organizer)
                .venue("Hall").eventDate(LocalDateTime.now().plusDays(1)).maxSeats(100).status(EventStatus.APPROVED).build();
        User ten = user(2L, "private-ten@example.invalid", Role.STUDENT, "CS-10");
        User two = user(3L, "private-two@example.invalid", Role.STUDENT, "CS-2");
        Registration rTen = registration(10L, event, ten, RegistrationStatus.REGISTERED);
        Registration rTwo = registration(11L, event, two, RegistrationStatus.REGISTERED);
        Instant first = Instant.parse("2026-10-18T10:00:00Z");
        var laterTwo = EventQrAttendanceEvidence.builder().id(20L).event(event).registration(rTwo).operator(operator)
                .action("ATTENDANCE_CHECKPOINT").source("QR").occurredAt(first.plusSeconds(10)).build();
        var earlierTen = EventQrAttendanceEvidence.builder().id(21L).event(event).registration(rTen).operator(operator)
                .action("ENTRY").source("QR").occurredAt(first).build();
        var manualTwo = ManualAttendanceEvidence.builder().id(30L).event(event).registration(rTwo).operator(operator)
                .action("ENTRY").idempotencyKey("01234567-89ab-4cde-8fab-0123456789ab")
                .verifiedIdentifierSnapshot("CS-2").reasonCode("DEVICE_FAILURE").occurredAt(first.plusSeconds(20)).build();
        when(eventOperations.requireAttendanceExportCapability(7L, "SCAN_LOG")).thenReturn(0L);
        when(access.currentUser()).thenReturn(organizer);
        when(events.findById(7L)).thenReturn(Optional.of(event));
        when(qr.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eq(7L), any(Instant.class))).thenReturn(List.of(earlierTen, laterTwo));
        when(manual.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eq(7L), any(Instant.class))).thenReturn(List.of(manualTwo));

        String csv = new String(service.scanLogCsv(7L), StandardCharsets.UTF_8);

        assertTrue(csv.indexOf("CS-2") < csv.indexOf("CS-10"));
        assertTrue(csv.contains("QR-20"));
        assertTrue(csv.contains("MANUAL-30"));
        assertTrue(csv.contains("Operator Account ID"));
        assertFalse(csv.contains("private-operator@example.invalid"));
        assertFalse(csv.contains("private-two@example.invalid"));
        assertFalse(csv.contains("DEVICE_FAILURE"));
        assertFalse(csv.contains("credential"));
        verify(audit).logAction(eq("organizer@example.invalid"), eq("ORGANIZER"), eq("ATTENDANCE_REPORT_EXPORTED"),
                argThat(details -> details.startsWith("eventId=7;format=ATTENDANCE_SCAN_LOG_CSV;operationsRevision=")));
    }

    private User user(long id, String email, Role role, String identifier) {
        return User.builder().id(id).name("Synthetic " + id).email(email).role(role)
                .verifiedEnrollmentNo(identifier).department("CS").enabled(true).build();
    }
    private Registration registration(long id, Event event, User user, RegistrationStatus status) {
        return Registration.builder().id(id).event(event).user(user).status(status).build();
    }
}
