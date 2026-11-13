package com.swiftdrop.operations.reports;

import com.swiftdrop.common.GlobalExceptionHandler;
import com.swiftdrop.operations.OperationsSecurityExceptionHandler;
import com.swiftdrop.operations.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class)
@Import({
        GlobalExceptionHandler.class,
        OperationsSecurityExceptionHandler.class,
        ReportExceptionHandler.class,
        SecurityConfig.class,
        com.swiftdrop.operations.zones.OperationsMethodSecurity.class
})
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportsFilteredRowsWithContractHeaders() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 7);
        when(reportService.getDailyReport(from, to)).thenReturn(List.of(
                new DailyReportRow(from, 10, 8, 2, 0.8),
                new DailyReportRow(to, 2, 1, 1, 0.5)
        ));

        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"daily-report.csv\""))
                .andExpect(content().string("""
                        reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate
                        2026-11-01,10,8,2,0.8
                        2026-11-07,2,1,1,0.5
                        """.replace("\n", "\r\n")));

        verify(reportService).getDailyReport(eq(from), eq(to));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportsHeaderForEmptyResults() throws Exception {
        when(reportService.getDailyReport(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate\r\n"));
    }

    @Test
    @WithMockUser(roles = "DISPATCHER")
    void rejectsNonAdminExport() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedExport() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnauthenticatedJsonReport() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/daily")
                        .param("from", "2026-11-01")
                        .param("to", "2026-11-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsMalformedJsonReportDateWithValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/daily")
                        .param("from", "not-a-date")
                        .param("to", "2026-11-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("from")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsMissingCsvReportDateWithValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("to")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsJsonAndCsvRowsFromTheSameReportData() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 1);
        when(reportService.getDailyReport(from, to)).thenReturn(List.of(
                new DailyReportRow(from, 3, 2, 1, 2.0 / 3.0)));

        mockMvc.perform(get("/api/v1/admin/reports/daily")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{
                          "reportDate": "2026-11-01",
                          "totalDeliveries": 3,
                          "successfulDeliveries": 2,
                          "failedDeliveries": 1,
                          "successRate": 0.6666666666666666
                        }]
                        """));

        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("""
                        reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate
                        2026-11-01,3,2,1,0.6666666666666666
                        """.replace("\n", "\r\n")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void preservesInvalidDateRangeValidation() throws Exception {
        when(reportService.getDailyReport(
                LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 1)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'from' must not be after 'to'"));

        mockMvc.perform(get("/api/v1/admin/reports/daily.csv")
                        .param("from", "2026-11-07")
                        .param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }
}
