package com.swiftdrop.integration.assistant.tools;

import java.util.List;

public interface AssistantTools {
    ParcelLookupResult getParcelByTrackingNumber(String trackingNumber, String callerToken);
    List<ParcelLookupResult> getCustomerParcels(String status, int page, int size, String callerToken);
    ParcelHistoryResult getTrackingHistory(String trackingNumber, String callerToken);
    DeliveryInfoResult getDeliveryInformation(String trackingNumber, String callerToken);
    ParcelSummaryResult getParcelSummary(String callerToken);
    FeeEstimateResult estimateFee(
            String pickupDistrict, String destinationDistrict, double weightKg, String sizeCategory, String callerToken);

    record ParcelLookupResult(
            String trackingNumber,
            String status,
            String destinationDistrict,
            boolean courierAssigned,
            java.time.Instant lastUpdated,
            java.math.BigDecimal fee) {
    }

    record ParcelHistoryResult(String trackingNumber, String currentStatus, List<HistoryEntry> entries) {
    }

    record HistoryEntry(String status, java.time.Instant occurredAt) {
    }

    record DeliveryInfoResult(String trackingNumber, String status, boolean courierAssigned, String destinationDistrict) {
    }

    record ParcelSummaryResult(int total, java.util.Map<String, Integer> statusCounts) {
    }

    record FeeEstimateResult(
            double weightKg, String sizeCategory, String destinationDistrict,
            java.math.BigDecimal estimatedFee, String currency) {
    }
}
