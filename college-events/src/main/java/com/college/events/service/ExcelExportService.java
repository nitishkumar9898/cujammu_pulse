package com.college.events.service;


import com.college.events.model.Event;
import com.college.events.model.Registration;
import com.college.events.model.RegistrationStatus;
import com.college.events.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {

    private final RegistrationService registrationService;
    private final AttendanceRepository attendanceRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public byte[] exportRegistrations(Event event) throws IOException {
        List<Registration> registrations = registrationService.findByEvent(event);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // --- Summary Sheet ---
            Sheet summarySheet = workbook.createSheet("Event Summary");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);

            Row titleRow = summarySheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("CUJAMMU EVENTHUB - Event Report");
            titleCell.setCellStyle(titleStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            String[][] summary = {
                {"Event Name", event.getTitle()},
                {"Venue", event.getVenue()},
                {"Date", event.getEventDate() != null ? event.getEventDate().format(FMT) : "TBD"},
                {"Max Seats", String.valueOf(event.getMaxSeats())},
                {"Category", event.getCategory() != null ? event.getCategory().getName() : "N/A"},
                {"Organizer", event.getOrganizer().getName()},
                {"Status", event.getStatus().name()},
                {"Total Registrations", String.valueOf(registrations.size())},
                {"Confirmed", String.valueOf(registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.REGISTERED || r.getStatus() == RegistrationStatus.ATTENDED).count())},
                {"Attended", String.valueOf(registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.ATTENDED).count())},
                {"Waitlisted", String.valueOf(registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).count())},
                {"Cancelled", String.valueOf(registrations.stream().filter(r -> r.getStatus() == RegistrationStatus.CANCELLED).count())},
            };
            for (int i = 0; i < summary.length; i++) {
                Row row = summarySheet.createRow(i + 2);
                Cell keyCell = row.createCell(0);
                keyCell.setCellValue(summary[i][0]);
                keyCell.setCellStyle(headerStyle);
                row.createCell(1).setCellValue(summary[i][1]);
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            // --- Registrations Sheet ---
            Sheet dataSheet = workbook.createSheet("Registrations");
            String[] headers = {"Sr.No", "Student Name", "Email", "Department", "Phone",
                "Enrollment No", "Status", "QR Token", "Registered At", "Attendance Time"};

            Row headerRow = dataSheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Registration r : registrations) {
                Row row = dataSheet.createRow(rowIdx);
                row.createCell(0).setCellValue(rowIdx);
                row.createCell(1).setCellValue(r.getUser().getName());
                row.createCell(2).setCellValue(r.getUser().getEmail());
                row.createCell(3).setCellValue(r.getUser().getDepartment() != null ? r.getUser().getDepartment() : "");
                row.createCell(4).setCellValue(r.getUser().getPhone() != null ? r.getUser().getPhone() : "");
                row.createCell(5).setCellValue(r.getUser().getEnrollmentNo() != null ? r.getUser().getEnrollmentNo() : "");
                row.createCell(6).setCellValue(r.getStatus().name());
                row.createCell(7).setCellValue(r.getQrToken());
                row.createCell(8).setCellValue(r.getRegisteredAt() != null ? r.getRegisteredAt().format(FMT) : "");

                // Attendance timestamp
                String attendanceTime = "";
                if (r.getStatus() == RegistrationStatus.ATTENDED) {
                    attendanceTime = attendanceRepository.findByRegistration(r)
                        .map(a -> a.getScannedAt() != null ? a.getScannedAt().format(FMT) : "Attended")
                        .orElse("Attended");
                }
                row.createCell(9).setCellValue(attendanceTime);

                // Color code by status
                CellStyle statusStyle = createStatusStyle(workbook, r.getStatus());
                row.getCell(6).setCellStyle(statusStyle);

                rowIdx++;
            }

            for (int i = 0; i < headers.length; i++) {
                dataSheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createStatusStyle(Workbook wb, RegistrationStatus status) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        switch (status) {
            case ATTENDED -> font.setColor(IndexedColors.BLUE.getIndex());
            case REGISTERED -> font.setColor(IndexedColors.GREEN.getIndex());
            case WAITLISTED -> font.setColor(IndexedColors.ORANGE.getIndex());
            case CANCELLED -> font.setColor(IndexedColors.RED.getIndex());
        }
        style.setFont(font);
        return style;
    }
}
