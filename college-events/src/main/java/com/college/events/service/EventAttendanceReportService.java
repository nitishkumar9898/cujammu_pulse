package com.college.events.service;

import com.college.events.model.*;
import com.college.events.repository.*;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Scoped final attendance summary. It exposes evidence outcomes, never QR payloads or private contacts. */
@Service
@RequiredArgsConstructor
public class EventAttendanceReportService {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final long MAX_SYNC_REGISTRATIONS = 5_000;
    private static final long MAX_SYNC_EVIDENCE_ROWS = 15_000;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final EventQrAttendanceEvidenceRepository qrEvidence;
    private final ManualAttendanceEvidenceRepository manualEvidence;
    private final DomainAccess access;
    private final FeatureFlagService features;
    private final AuditService audit;
    private final EventOperationsService eventOperations;

    // The runtime flag is locked before the projection is read, so a report cannot
    // race an administrator disabling the Events module. Keep this transaction
    // write-capable for PostgreSQL/Mongo-compatible lock semantics.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public byte[] csv(long eventId) throws IOException {
        ReportContext context = authorizedContext(eventId, "CSV");
        List<AttendanceRow> rows = rows(context.event(), context.cutoff());
        String checksum = checksum(rows.stream().map(EventAttendanceReportService::attendanceFields).toList());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF); out.write(0xBB); out.write(0xBF);
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeManifest(writer, context, rows.size(), checksum);
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(
                     "Verified Identifier", "Student Name", "Department", "Registration Status",
                     "Evidence Source", "Entry Time", "Checkpoint Time", "Result").build())) {
            for (AttendanceRow row : rows) printer.printRecord(row.identifier(), row.name(), row.department(), row.registrationStatus(),
                    row.source(), instant(row.entryAt()), instant(row.checkpointAt()), row.result());
            }
        }
        byte[] report = out.toByteArray();
        auditExport(eventId, "ATTENDANCE_CSV", context, rows.size());
        return report;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public byte[] xlsx(long eventId) throws IOException {
        ReportContext context = authorizedContext(eventId, "XLSX");
        List<AttendanceRow> rows = rows(context.event(), context.cutoff());
        String checksum = checksum(rows.stream().map(EventAttendanceReportService::attendanceFields).toList());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance");
            CellStyle header = workbook.createCellStyle();
            Font headerFont = workbook.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont); header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            String[] headers = {"Verified Identifier", "Student Name", "Department", "Registration Status", "Evidence Source", "Entry Time", "Checkpoint Time", "Result"};
            org.apache.poi.ss.usermodel.Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { h.createCell(i).setCellValue(headers[i]); h.getCell(i).setCellStyle(header); }
            Sheet manifest = workbook.createSheet("Report Manifest");
            String[][] metadata = {{"Operations revision", Long.toString(context.revision())}, {"Captured at UTC", instant(context.cutoff())},
                    {"Timezone", "UTC"}, {"Quality", "PROVISIONAL"}, {"Rows", Integer.toString(rows.size())}, {"Checksum algorithm", "SHA-256"},
                    {"Checksum scope", "Ordered exported data columns; UTF-8 length-prefixed fields; 0xFF record boundary"}, {"Checksum", checksum},
                    {"Sort version", "cujammu-natural-identifier-v1"}, {"Sort", "Verified identifier natural order; numeric runs compare numerically, text case-insensitively; leading zeros preserved"}};
            for (int i = 0; i < metadata.length; i++) { org.apache.poi.ss.usermodel.Row meta = manifest.createRow(i); meta.createCell(0).setCellValue(metadata[i][0]); meta.createCell(1).setCellValue(metadata[i][1]); }
            manifest.autoSizeColumn(0); manifest.autoSizeColumn(1);
            for (int i = 0; i < rows.size(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1); AttendanceRow value = rows.get(i);
                String[] values = {value.identifier(), value.name(), value.department(), value.registrationStatus(), value.source(), instant(value.entryAt()), instant(value.checkpointAt()), value.result()};
                for (int c = 0; c < values.length; c++) row.createCell(c, CellType.STRING).setCellValue(values[c]);
                CellStyle status = workbook.createCellStyle(); Font statusFont = workbook.createFont(); statusFont.setBold(true);
                statusFont.setColor(color(value.result())); status.setFont(statusFont); row.getCell(7).setCellStyle(status);
            }
            sheet.createFreezePane(0, 1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rows.size()), 0, headers.length - 1));
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); workbook.write(out);
            byte[] report = out.toByteArray();
            auditExport(eventId, "ATTENDANCE_XLSX", context, rows.size());
            return report;
        }
    }

    /** Accepted action ledger only. Rejected attempts, credentials and evidence proofs never leave through this export. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public byte[] scanLogCsv(long eventId) throws IOException {
        ReportContext context = authorizedContext(eventId, "SCAN_LOG");
        List<ScanLogRow> rows = new ArrayList<>();
        qrEvidence.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eventId, context.cutoff()).forEach(evidence -> rows.add(new ScanLogRow(
                "QR-" + evidence.getId(), evidence.getRegistration().getUser().getVerifiedEnrollmentNo(),
                evidence.getAction(), evidence.getSource(), evidence.getOperator().getId(), evidence.getOccurredAt())));
        manualEvidence.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eventId, context.cutoff()).forEach(evidence -> rows.add(new ScanLogRow(
                "MANUAL-" + evidence.getId(), evidence.getVerifiedIdentifierSnapshot(), evidence.getAction(),
                "MANUAL", evidence.getOperator().getId(), evidence.getOccurredAt())));
        rows.sort(Comparator.comparing(ScanLogRow::identifier, EventAttendanceReportService::compareIdentifier)
                .thenComparing(ScanLogRow::occurredAt).thenComparing(ScanLogRow::scanId));
        String checksum = checksum(rows.stream().map(row -> List.of(row.scanId(), row.identifier(), row.action(), row.source(),
                Objects.toString(row.operatorId(), ""), instant(row.occurredAt()))).toList());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xEF); out.write(0xBB); out.write(0xBF);
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writeManifest(writer, context, rows.size(), checksum);
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(
                     "Scan ID", "Verified Identifier", "Action", "Evidence Source", "Operator Account ID", "Accepted At").build())) {
            for (ScanLogRow row : rows) printer.printRecord(formulaSafe(row.scanId()), safe(row.identifier()),
                    formulaSafe(row.action()), formulaSafe(row.source()), row.operatorId(), instant(row.occurredAt()));
            }
        }
        byte[] report = out.toByteArray();
        auditExport(eventId, "ATTENDANCE_SCAN_LOG_CSV", context, rows.size());
        return report;
    }

    private ReportContext authorizedContext(long eventId, String action) {
        features.requireEnabledForUpdate("MODULE_EVENTS");
        long revision = eventOperations.requireAttendanceExportCapability(eventId, action);
        Event event = events.findById(eventId).orElseThrow(DomainAccess::denied);
        long registrationsCount = registrations.countByEvent(event);
        long evidenceCount = qrEvidence.countByEventId(eventId) + manualEvidence.countByEventId(eventId);
        if (registrationsCount > MAX_SYNC_REGISTRATIONS || evidenceCount > MAX_SYNC_EVIDENCE_ROWS)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "This event exceeds the synchronous report limit; use the reviewed bounded report workflow when available.");
        return new ReportContext(event, revision, Instant.now());
    }

    private void auditExport(long eventId, String format, ReportContext context, int rowCount) {
        User actor = access.currentUser();
        audit.logAction(actor.getEmail(), actor.getRole().name(), "ATTENDANCE_REPORT_EXPORTED",
                "eventId=" + eventId + ";format=" + format + ";operationsRevision=" + context.revision()
                        + ";capturedAt=" + context.cutoff() + ";rows=" + rowCount + ";quality=PROVISIONAL");
    }

    private void writeManifest(OutputStreamWriter writer, ReportContext context, int rowCount, String checksum) throws IOException {
        writer.write("# CUJAMMU-PULSE attendance report\r\n");
        writer.write("# operationsRevision," + context.revision() + "\r\n");
        writer.write("# capturedAtUtc," + instant(context.cutoff()) + "\r\n");
        writer.write("# timezone,UTC\r\n");
        writer.write("# quality,PROVISIONAL\r\n");
        writer.write("# rows," + rowCount + "\r\n");
        writer.write("# checksumAlgorithm,SHA-256\r\n");
        writer.write("# checksumScope,ordered data columns; UTF-8 length-prefixed fields; 0xFF record boundary\r\n");
        writer.write("# checksum," + checksum + "\r\n");
        writer.write("# sortVersion,cujammu-natural-identifier-v1\r\n");
    }

    private static List<String> attendanceFields(AttendanceRow row) {
        return List.of(row.identifier(), row.name(), row.department(), row.registrationStatus(),
                row.source(), instant(row.entryAt()), instant(row.checkpointAt()), row.result());
    }

    /** Hashes the canonical ordered data fields using length-prefix framing to avoid delimiter ambiguity. */
    private static String checksum(List<List<String>> records) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (List<String> record : records) {
                for (String field : record) {
                    byte[] value = Objects.toString(field, "").getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
                    digest.update(value);
                }
                digest.update((byte) 0xFF);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private List<AttendanceRow> rows(Event authoritative, Instant cutoff) {
        long eventId = authoritative.getId();
        Map<Long, List<EventQrAttendanceEvidence>> qr = qrEvidence.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eventId, cutoff).stream()
                .collect(Collectors.groupingBy(e -> e.getRegistration().getId()));
        Map<Long, List<ManualAttendanceEvidence>> manual = manualEvidence.findByEventIdAndOccurredAtLessThanEqualOrderByOccurredAtAscIdAsc(eventId, cutoff).stream()
                .collect(Collectors.groupingBy(e -> e.getRegistration().getId()));
        return registrations.findByEventOrderByRegisteredAtAsc(authoritative).stream().map(reg -> toRow(reg, qr.getOrDefault(reg.getId(), List.of()), manual.getOrDefault(reg.getId(), List.of())))
                .sorted(Comparator.comparing(AttendanceRow::identifier, (a, b) -> compareIdentifier(stripFormulaPrefix(a), stripFormulaPrefix(b)))
                        .thenComparingLong(AttendanceRow::registrationId)).toList();
    }

    private AttendanceRow toRow(Registration registration, List<EventQrAttendanceEvidence> qr, List<ManualAttendanceEvidence> manual) {
        String identifier = registration.getUser().getVerifiedEnrollmentNo();
        boolean entry = qr.stream().anyMatch(e -> "ENTRY".equals(e.getAction())) || manual.stream().anyMatch(e -> "ENTRY".equals(e.getAction()));
        EventQrAttendanceEvidence checkpoint = qr.stream().filter(e -> "ATTENDANCE_CHECKPOINT".equals(e.getAction())).findFirst().orElse(null);
        EventQrAttendanceEvidence qrEntry = qr.stream().filter(e -> "ENTRY".equals(e.getAction())).findFirst().orElse(null);
        ManualAttendanceEvidence manualEntry = manual.stream().filter(e -> "ENTRY".equals(e.getAction())).findFirst().orElse(null);
        String source = qr.isEmpty() && manual.isEmpty() ? "NONE" : (qr.isEmpty() ? "MANUAL" : (manual.isEmpty() ? "QR_OWNER_CONFIRMED" : "QR_OWNER_CONFIRMED+MANUAL"));
        String result = registration.getStatus() == RegistrationStatus.CANCELLED ? "CANCELLED" : checkpoint != null && entry ? "ATTENDANCE_CONFIRMED" : entry ? "ENTRY_ONLY" : registration.getStatus() == RegistrationStatus.ATTENDED ? "UNVERIFIED_LEGACY_ATTENDED" : "ABSENT";
        return new AttendanceRow(registration.getId(), formulaSafe(identifier == null ? "" : identifier), safe(registration.getUser().getName()), safe(registration.getUser().getDepartment()),
                registration.getStatus().name(), source, qrEntry != null ? qrEntry.getOccurredAt() : manualEntry != null ? manualEntry.getOccurredAt() : null,
                checkpoint == null ? null : checkpoint.getOccurredAt(), result);
    }

    private static String safe(String value) { return value == null ? "" : formulaSafe(value); }
    private static String formulaSafe(String value) {
        if (value == null) return "";
        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            if (!(Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == 0xFEFF)) break;
            index++;
        }
        return index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0 ? "'" + value : value;
    }
    private static String stripFormulaPrefix(String value) { return value != null && value.startsWith("'") ? value.substring(1) : value; }
    private static String instant(Instant value) { return value == null ? "" : ISO.format(value); }
    private static int compareIdentifier(String left, String right) {
        if (left == null || left.isBlank()) return right == null || right.isBlank() ? 0 : 1;
        if (right == null || right.isBlank()) return -1;
        List<String> a = tokens(left), b = tokens(right);
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) { String x = a.get(i), y = b.get(i); int c;
            if (x.chars().allMatch(Character::isDigit) && y.chars().allMatch(Character::isDigit)) c = new BigInteger(x).compareTo(new BigInteger(y));
            else c = x.compareToIgnoreCase(y);
            if (c != 0) return c;
        }
        return Integer.compare(a.size(), b.size()) != 0 ? Integer.compare(a.size(), b.size()) : left.compareTo(right);
    }
    private static List<String> tokens(String value) { List<String> out = new ArrayList<>(); java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+|\\D+").matcher(value); while (m.find()) out.add(m.group()); return out; }
    private static short color(String result) { return switch (result) { case "ATTENDANCE_CONFIRMED" -> IndexedColors.GREEN.getIndex(); case "ENTRY_ONLY", "UNVERIFIED_LEGACY_ATTENDED" -> IndexedColors.ORANGE.getIndex(); case "CANCELLED", "ABSENT" -> IndexedColors.RED.getIndex(); default -> IndexedColors.BLACK.getIndex(); }; }
    private record AttendanceRow(long registrationId, String identifier, String name, String department, String registrationStatus, String source, Instant entryAt, Instant checkpointAt, String result) {}
    private record ScanLogRow(String scanId, String identifier, String action, String source, Long operatorId, Instant occurredAt) {}
    private record ReportContext(Event event, long revision, Instant cutoff) {}
}
