package com.swiftdrop.operations.reports;

import java.time.LocalDate;

public record DailyReportRow(
        LocalDate reportDate,
        int totalDeliveries,
        int successfulDeliveries,
        int failedDeliveries,
        double successRate
) {
}
