package com.college.events.service;

import com.college.events.model.LibraryGateLog;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** Pure synthetic/report projection. Authorization and snapshot selection belong to a separate service. */
final class LibraryReportRenderer {
    private static final DateTimeFormatter TIME_FMT=DateTimeFormatter.ofPattern("HH:mm:ss");
    private LibraryReportRenderer() { }

    static byte[] csv(List<LibraryGateLog> source) {
        List<LibraryGateLog> logs=ordered(source);
        StringBuilder sb=new StringBuilder("Log Date,Full Name,Roll Number/ID,Department,Semester,Academic Level,Purpose/QR Type,Entry Time,Exit Time,Total Duration,Status\n");
        for(LibraryGateLog log:logs) {
            sb.append(escapeCsv(log.getLogDate().toString())).append(',')
                    .append(escapeCsv(log.getUser().getName())).append(',')
                    .append(escapeCsv(log.getRollNumberOrEmpId())).append(',')
                    .append(escapeCsv(log.getDepartmentName())).append(',')
                    .append(escapeCsv(log.getUser().getSemester()==null?"N/A":log.getUser().getSemester())).append(',')
                    .append(escapeCsv(log.getAcademicLevel())).append(',')
                    .append(escapeCsv(log.getSessionType())).append(',')
                    .append(escapeCsv(log.getEntryTime().format(TIME_FMT))).append(',')
                    .append(escapeCsv(log.getExitTime()==null?"In-Session":log.getExitTime().format(TIME_FMT))).append(',')
                    .append(escapeCsv(log.getDurationFormatted()==null?"Active":log.getDurationFormatted())).append(',')
                    .append(escapeCsv(log.getStatus())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] xlsx(List<LibraryGateLog> source) {
        List<LibraryGateLog> logs=ordered(source);
        try(XSSFWorkbook workbook=new XSSFWorkbook();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Library Attendance");
            CellStyle header=workbook.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font=workbook.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());header.setFont(font);
            String[] columns={"Log Date","Full Name","Roll Number/ID","Department","Semester","Academic Level",
                    "Purpose/QR Type","Entry Time","Exit Time","Total Duration","Status"};
            Row heading=sheet.createRow(0);
            for(int i=0;i<columns.length;i++){Cell cell=heading.createCell(i);cell.setCellValue(columns[i]);cell.setCellStyle(header);}
            for(int rowIndex=0;rowIndex<logs.size();rowIndex++) {
                LibraryGateLog log=logs.get(rowIndex);Row row=sheet.createRow(rowIndex+1);
                String[] values={String.valueOf(log.getLogDate()),log.getUser().getName(),log.getRollNumberOrEmpId(),
                        log.getDepartmentName(),log.getUser().getSemester()==null?"N/A":log.getUser().getSemester(),
                        log.getAcademicLevel(),log.getSessionType(),log.getEntryTime().format(TIME_FMT),
                        log.getExitTime()==null?"In-Session":log.getExitTime().format(TIME_FMT),
                        log.getDurationFormatted()==null?"Active":log.getDurationFormatted(),log.getStatus()};
                for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]==null?"":values[i]);
            }
            sheet.createFreezePane(0,1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,logs.size(),0,columns.length-1));
            for(int i=0;i<columns.length;i++)sheet.autoSizeColumn(i);
            workbook.write(output);return output.toByteArray();
        } catch(java.io.IOException failure) { throw new IllegalStateException("Library XLSX rendering failed",failure); }
    }

    private static List<LibraryGateLog> ordered(List<LibraryGateLog> source) {
        return source.stream().sorted(Comparator.comparing(LibraryGateLog::getRollNumberOrEmpId,
                Comparator.nullsLast(String::compareTo))).toList();
    }

    private static String escapeCsv(String value) {
        if(value==null)return "";
        int firstContent=0;
        while(firstContent<value.length()&&(Character.isWhitespace(value.charAt(firstContent))||Character.isISOControl(value.charAt(firstContent))))firstContent++;
        if(firstContent<value.length()&&"=+-@".indexOf(value.charAt(firstContent))>=0)value="'"+value;
        if(value.contains(",")||value.contains("\"")||value.contains("\n")||value.contains("\r"))
            return "\""+value.replace("\"","\"\"")+"\"";
        return value;
    }
}
