package com.swiftdrop.operations.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    public List<DailyReportRow> daily(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "zone", required = false) String zone
    ) {
        if (zone != null && !zone.isBlank()) {
            return reportService.getDailyReport(from, to, zone);
        }
        return reportService.getDailyReport(from, to);
    }

    @GetMapping(value = "/daily.csv", produces = "text/csv")
    public ResponseEntity<String> exportDailyCsv(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "zone", required = false) String zone
    ) {
        List<DailyReportRow> rows = (zone != null && !zone.isBlank())
                ? reportService.getDailyReport(from, to, zone)
                : reportService.getDailyReport(from, to);
        String csv = DailyReportCsvSerializer.serialize(rows);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("daily-report.csv")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }

    @GetMapping("/zones")
    public List<ZoneReportSummary> zones(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return reportService.getZoneReport(from, to);
    }
}
