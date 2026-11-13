package com.college.events.service;

import com.college.events.model.*;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsvExportSafetyTest {
    @Test void registrationExportNeutralizesSpreadsheetExpressionsAndRetainsIdentifierZeros() throws Exception {
        RegistrationService registrations = mock(RegistrationService.class);
        Event event = Event.builder().id(10L).build();
        User user = User.builder().name("  =HYPERLINK(\"https://example.invalid\")")
                .email("+1+1").department("\t@SUM(1,1)").phone("0012345678").build();
        when(registrations.findByEvent(event)).thenReturn(List.of(Registration.builder().user(user)
                .status(RegistrationStatus.REGISTERED).build()));
        String output = new String(new CsvExportService(registrations).exportRegistrations(event), StandardCharsets.UTF_8);
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(output))) {
            var rows = parser.getRecords();
            assertEquals("'  =HYPERLINK(\"https://example.invalid\")", rows.get(1).get(1));
            assertEquals("'+1+1", rows.get(1).get(2));
            assertEquals("'\t@SUM(1,1)", rows.get(1).get(3));
            assertEquals("0012345678", rows.get(1).get(4));
        }
    }
    @Test void sanitizerPreservesOrdinaryTextAndNeutralizesEveryDangerousPrefix() {
        for (String value : List.of("=1+1", "+1", "-1", "@SUM(1)", "\ttext", "\rtext", "\ntext", "  =1")) {
            assertEquals("'" + value, CsvCellSafety.literal(value));
        }
        assertEquals("00123", CsvCellSafety.literal("00123"));
        assertEquals("A,B\"C", CsvCellSafety.literal("A,B\"C"));
        assertEquals("", CsvCellSafety.literal(null));
    }
}
