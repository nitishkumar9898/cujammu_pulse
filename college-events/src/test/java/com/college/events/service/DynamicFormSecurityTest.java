package com.college.events.service;

import com.college.events.dto.DynamicFormDtos;
import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.StringReader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamicFormSecurityTest {
    private final CustomEventFormFieldRepository fields = mock(CustomEventFormFieldRepository.class);
    private final CustomEventFormResponseRepository responses = mock(CustomEventFormResponseRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final DynamicFormService service = new DynamicFormService(fields, responses, new DomainAccess(users, events));
    private final User owner = User.builder().id(1L).email("owner@example.invalid").role(Role.ORGANIZER).enabled(true).build();
    private final User stranger = User.builder().id(2L).email("other@example.invalid").role(Role.ORGANIZER).enabled(true).build();

    @BeforeEach void setup() {
        when(events.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).organizer(owner).build()));
        when(users.findById(1L)).thenReturn(Optional.of(owner));
        when(users.findById(2L)).thenReturn(Optional.of(stranger));
        signIn(owner);
    }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    private void signIn(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }
    private DynamicFormDtos.Field field(String name) {
        return new DynamicFormDtos.Field(name, "Label", "TEXT", null, false, null);
    }

    @Test void unrelatedOrganizerCannotReplaceSchemaOrExportPrivateAnswers() {
        signIn(stranger);
        assertThrows(AccessDeniedException.class, () -> service.configureFormSchema(10L, List.of(field("size"))));
        assertThrows(AccessDeniedException.class, () -> service.getAllResponses(10L));
        assertThrows(AccessDeniedException.class, () -> service.exportResponsesCsv(10L));
        verifyNoInteractions(fields, responses);
    }

    @Test void unauthenticatedAndDisabledOwnerCannotReachPrivateRows() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> service.exportResponsesCsv(10L));
        signIn(owner);
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).enabled(false).build()));
        assertThrows(AccessDeniedException.class, () -> service.configureFormSchema(10L, List.of()));
        verifyNoInteractions(fields, responses);
    }

    @Test void requestCannotReassignExistingForeignFieldIdentityOrOrdering() throws Exception {
        var commands = new ObjectMapper().readValue("[{\"id\":999,\"eventId\":77,\"fieldOrder\":999,"
                + "\"fieldName\":\"size\",\"fieldLabel\":\"Size\",\"fieldType\":\"TEXT\"}]",
                new TypeReference<List<DynamicFormDtos.Field>>() { });
        when(fields.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.configureFormSchema(10L, commands);
        assertEquals(1, saved.size());
        assertNull(saved.get(0).getId());
        assertEquals(10L, saved.get(0).getEventId());
        assertEquals(1, saved.get(0).getFieldOrder());
        verify(fields).deleteByEventId(10L);
    }

    @Test void oversizedDuplicateAndUnsupportedSchemaCannotDeleteCurrentSchema() {
        assertThrows(IllegalArgumentException.class, () -> service.configureFormSchema(10L, Collections.nCopies(51, field("size"))));
        assertThrows(IllegalArgumentException.class, () -> service.configureFormSchema(10L, List.of(field("size"), field("size"))));
        assertThrows(IllegalArgumentException.class, () -> service.configureFormSchema(10L,
                List.of(new DynamicFormDtos.Field("size", "Size", "SCRIPT", null, false, null))));
        verifyNoInteractions(fields, responses);
    }

    @Test void facultyOwnerCanManageItsOwnSchema() {
        owner.setRole(Role.FACULTY);
        when(fields.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("size", service.configureFormSchema(10L, List.of(field("size"))).get(0).getFieldName());
    }

    @Test void ownResponseAndSubmissionCannotImpersonateAnotherAccount() {
        assertThrows(AccessDeniedException.class, () -> service.getUserResponse(10L, 2L));
        assertThrows(AccessDeniedException.class, () -> service.submitForm(10L, stranger, null, null, null, null, Map.of()));
        verifyNoInteractions(fields, responses);
    }

    @Test void exportedHeadersAndValuesRemainLiteralCsvCells() throws Exception {
        when(fields.findByEventIdOrderByFieldOrderAsc(10L)).thenReturn(List.of(
                CustomEventFormField.builder().fieldName("answer").fieldLabel("=HYPERLINK(\"https://example.invalid\")").build()));
        when(responses.findByEventId(10L)).thenReturn(List.of(CustomEventFormResponse.builder().id(3L).userId(4L)
                .participantName("  =1+1").participantEmail("ordinary@example.invalid").participantRollNo("0012")
                .participantDepartment("+1+1").participantSemester("@SUM(1,1)")
                .responsesJson("{\"answer\":\"\\t=1+1\"}").build()));
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(service.exportResponsesCsv(10L)))) {
            var rows = parser.getRecords();
            assertTrue(rows.get(0).get(8).startsWith("'="));
            assertEquals("'  =1+1", rows.get(1).get(2));
            assertEquals("ordinary@example.invalid", rows.get(1).get(3));
            assertEquals("0012", rows.get(1).get(4));
            assertEquals("'+1+1", rows.get(1).get(5));
            assertEquals("'@SUM(1,1)", rows.get(1).get(6));
            assertEquals("'\t=1+1", rows.get(1).get(8));
        }
    }

    @Test void submissionRejectsOversizedAndUnknownAnswersBeforePersistence() {
        when(fields.findByEventIdOrderByFieldOrderAsc(10L)).thenReturn(List.of(
                CustomEventFormField.builder().fieldName("size").required(true).build()));
        assertThrows(IllegalArgumentException.class, () -> service.submitForm(10L, owner, "x".repeat(101), null, null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.submitForm(10L, owner, null, null, null, null, Map.of("size", "x".repeat(2001))));
        assertThrows(IllegalArgumentException.class, () -> service.submitForm(10L, owner, null, null, null, null, Map.of("foreign", "value")));
        assertThrows(IllegalArgumentException.class, () -> service.submitForm(10L, owner, null, null, null, null, Map.of()));
        verify(responses, never()).save(any());
    }

    @Test void typedSubmissionRejectsNestedAnswerObjectsAndAcceptsBoundaryLength() throws Exception {
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class, () -> new ObjectMapper().readValue(
                "{\"answers\":{\"size\":{\"id\":1}}}", DynamicFormDtos.Submit.class));
        when(fields.findByEventIdOrderByFieldOrderAsc(10L)).thenReturn(List.of(CustomEventFormField.builder().fieldName("size").required(true).build()));
        when(responses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var saved = service.submitForm(10L, owner, "x".repeat(100), "0".repeat(50), "x".repeat(80), "x".repeat(30), Map.of("size", "x".repeat(2000)));
        assertEquals(100, saved.getParticipantName().length());
        assertEquals("0".repeat(50), saved.getParticipantRollNo());
    }
}
