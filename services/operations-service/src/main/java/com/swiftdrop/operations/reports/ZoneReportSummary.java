package com.swiftdrop.operations.reports;

import java.util.Set;

public record ZoneReportSummary(
    String zoneName,
    Set<String> districts,
    boolean served,
    int totalDeliveries,
    int successfulDeliveries,
    int failedDeliveries,
    double successRate
) {}

