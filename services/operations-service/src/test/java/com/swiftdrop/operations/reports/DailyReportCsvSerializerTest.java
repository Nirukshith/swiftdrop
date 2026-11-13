package com.swiftdrop.operations.reports;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyReportCsvSerializerTest {

    @Test
    void serializesHeaderAndRowsWithStandardCrlf() {
        List<DailyReportRow> rows = List.of(
                new DailyReportRow(LocalDate.of(2026, 11, 1), 10, 8, 2, 0.8),
                new DailyReportRow(LocalDate.of(2026, 11, 2), 4, 3, 1, 0.75)
        );

        String csv = DailyReportCsvSerializer.serialize(rows);

        assertThat(csv).isEqualTo(
                "reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate\r\n"
                        + "2026-11-01,10,8,2,0.8\r\n"
                        + "2026-11-02,4,3,1,0.75\r\n");
    }

    @Test
    void serializesEmptyListWithHeaderOnly() {
        String csv = DailyReportCsvSerializer.serialize(List.of());

        assertThat(csv).isEqualTo(
                "reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate\r\n");
    }

    @Test
    void preservesZeroRateForZeroDeliveries() {
        List<DailyReportRow> rows = List.of(
                new DailyReportRow(LocalDate.of(2026, 11, 3), 0, 0, 0, 0.0)
        );

        String csv = DailyReportCsvSerializer.serialize(rows);

        assertThat(csv).isEqualTo(
                "reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate\r\n"
                        + "2026-11-03,0,0,0,0.0\r\n");
    }
}

