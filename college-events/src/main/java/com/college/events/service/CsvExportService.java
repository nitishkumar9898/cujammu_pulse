package com.college.events.service;

import com.college.events.model.Event;
import com.college.events.model.Registration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CsvExportService {

    private final RegistrationService registrationService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] exportRegistrations(Event event) throws IOException {
        List<Registration> registrations = registrationService.findByEvent(event);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                 .setHeader("Sr.No", "Student Name", "Email", "Department", "Phone",
                            "Status", "QR Token", "Registered At")
                 .build())) {

            int i = 1;
            for (Registration r : registrations) {
                printer.printRecord(
                    i++,
                    r.getUser().getName(),
                    r.getUser().getEmail(),
                    r.getUser().getDepartment(),
                    r.getUser().getPhone(),
                    r.getStatus().name(),
                    r.getQrToken(),
                    r.getRegisteredAt() != null ? r.getRegisteredAt().format(FMT) : ""
                );
            }
        }

        return out.toByteArray();
    }
}
