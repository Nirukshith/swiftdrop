package com.swiftdrop.operations.reports;

import java.util.List;

final class DailyReportCsvSerializer {

    private static final String HEADER =
            "reportDate,totalDeliveries,successfulDeliveries,failedDeliveries,successRate";

    private DailyReportCsvSerializer() {
    }

    static String serialize(List<DailyReportRow> rows) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (DailyReportRow row : rows) {
            csv.append(escape(row.reportDate().toString())).append(',')
                    .append(row.totalDeliveries()).append(',')
                    .append(row.successfulDeliveries()).append(',')
                    .append(row.failedDeliveries()).append(',')
                    .append(row.successRate()).append("\r\n");
        }
        return csv.toString();
    }

    private static String escape(String value) {
        if (value.indexOf(',') < 0
                && value.indexOf('"') < 0
                && value.indexOf('\r') < 0
                && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
