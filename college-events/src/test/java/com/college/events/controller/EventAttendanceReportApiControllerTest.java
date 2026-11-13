package com.college.events.controller;

import com.college.events.service.EventAttendanceReportService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventAttendanceReportApiControllerTest {
    private final EventAttendanceReportService reports = mock(EventAttendanceReportService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new EventAttendanceReportApiController(reports)).build();

    @Test
    void scanLogDownloadUsesPrivateNoStoreCsvResponse() throws Exception {
        when(reports.scanLogCsv(7L)).thenReturn("Scan ID,Verified Identifier\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/cujammu/events/7/attendance/scan-log.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=event-7-attendance-scan-log.csv"))
                .andExpect(content().contentType("text/csv"));
    }

    @Test
    void xlsxAndSummaryCsvAreAlsoMarkedNoStore() throws Exception {
        when(reports.csv(7L)).thenReturn(new byte[]{1, 2, 3});
        when(reports.xlsx(7L)).thenReturn(new byte[]{4, 5, 6});

        mvc.perform(get("/api/v1/cujammu/events/7/attendance/report.csv"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/cujammu/events/7/attendance/report.xlsx"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }
}
