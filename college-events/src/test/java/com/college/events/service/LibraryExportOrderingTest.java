package com.college.events.service;

import com.college.events.model.LibraryGateLog;
import com.college.events.model.User;
import com.college.events.repository.LibraryGateLogRepository;
import com.college.events.security.DomainAccess;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LibraryExportOrderingTest {
    @Test void roleOnlyCsvAndXlsxPathsNeverReadPrivateRows() {
        LibraryGateLogRepository repository=mock(LibraryGateLogRepository.class);
        DomainAccess access=mock(DomainAccess.class);
        FeatureFlagService flags=mock(FeatureFlagService.class);
        var service=new LibraryTurnstileService(repository,access,flags);

        var csvFailure=assertThrows(IllegalStateException.class,()->service.exportDailyCsv(LocalDate.of(2026,10,4)));
        var xlsxFailure=assertThrows(IllegalStateException.class,()->service.exportDailyXlsx(LocalDate.of(2026,10,4)));

        assertTrue(csvFailure.getMessage().contains("LIBRARY_VERIFICATION_UNAVAILABLE"));
        assertTrue(xlsxFailure.getMessage().contains("LIBRARY_VERIFICATION_UNAVAILABLE"));
        verify(flags,times(2)).requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        verify(access,times(2)).requireRole(com.college.events.model.Role.ADMIN,com.college.events.model.Role.LIBRARIAN);
        verifyNoInteractions(repository);
    }

    @Test void roleOnlyTodayRosterPathNeverReadsPrivateRows() {
        LibraryGateLogRepository repository=mock(LibraryGateLogRepository.class);
        DomainAccess access=mock(DomainAccess.class);
        FeatureFlagService flags=mock(FeatureFlagService.class);
        var service=new LibraryTurnstileService(repository,access,flags);

        var failure=assertThrows(IllegalStateException.class,service::getTodayLogs);

        assertTrue(failure.getMessage().contains("LIBRARY_VERIFICATION_UNAVAILABLE"));
        verify(flags).requireEnabledForRead("MODULE_LIBRARY_TURNSTILE");
        verify(access).requireRole(com.college.events.model.Role.ADMIN,com.college.events.model.Role.LIBRARIAN);
        verifyNoInteractions(repository);
    }

    @Test void csvRowsAreSortedAndSpreadsheetFormulasAreNeutralized() {
        LocalDate date=LocalDate.of(2026,10,4);
        User formulaUser=User.builder().name(" =HYPERLINK(\"https://example.invalid\",\"x\")").semester("+SUM(1,2)").build();
        LibraryGateLog formula=LibraryGateLog.builder().id(2L).user(formulaUser).rollNumberOrEmpId("0001")
                .departmentName("@SUM(1,2)").academicLevel("UG").sessionType("STUDY")
                .entryTime(date.atTime(9,0)).logDate(date).status("COMPLETED").build();

        String csv=new String(LibraryReportRenderer.csv(List.of(log("UG-20","Zed"),formula,log("UG-02","Ada"))),StandardCharsets.UTF_8);

        assertTrue(csv.indexOf("UG-02")<csv.indexOf("UG-20"));
        assertTrue(csv.contains("' =HYPERLINK"));
        assertTrue(csv.contains("'+SUM"));
        assertTrue(csv.contains("'@SUM"));
        assertTrue(csv.contains("0001"));
    }

    @Test void xlsxRowsAreSortedAndContainOnlyApprovedReportColumns() throws Exception {
        byte[] workbook=LibraryReportRenderer.xlsx(List.of(log("UG-20","Zed"),log("UG-02","Ada")));
        try(XSSFWorkbook document=new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            var sheet=document.getSheetAt(0);
            assertEquals("Roll Number/ID",sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("UG-02",sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("UG-20",sheet.getRow(2).getCell(2).getStringCellValue());
            for(var cell:sheet.getRow(0))assertFalse(cell.getStringCellValue().toLowerCase().contains("credential"));
        }
    }

    private LibraryGateLog log(String identifier,String name) {
        return LibraryGateLog.builder().id(1L).user(User.builder().name(name).semester("1").build())
                .rollNumberOrEmpId(identifier).departmentName("CSIT").academicLevel("UG").sessionType("STUDY")
                .entryTime(LocalDateTime.of(2026,10,4,9,0)).logDate(LocalDate.of(2026,10,4))
                .status("COMPLETED").durationFormatted("00h 10m 00s").build();
    }
}
