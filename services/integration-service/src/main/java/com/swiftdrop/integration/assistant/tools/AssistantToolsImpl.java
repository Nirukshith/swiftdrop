package com.swiftdrop.integration.assistant.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AssistantToolsImpl implements AssistantTools {

    private static final Pattern TRACKING_NUMBER = Pattern.compile("^SD-\\d{8}-[A-Z0-9]{6}$");
    private static final int MAX_PAGE_SIZE = 50;

    private final ParcelServiceClient parcelClient;

    public AssistantToolsImpl(ParcelServiceClient parcelClient) {
        this.parcelClient = parcelClient;
    }

    @Override
    public ParcelLookupResult getParcelByTrackingNumber(String trackingNumber, String callerToken) {
        String normalized = requireTrackingNumber(trackingNumber);
        ParcelServiceClient.ParcelListItem item = findOwnedParcel(normalized, callerToken);
        return new ParcelLookupResult(item.trackingNumber(), item.status(), null,
                item.courierId() != null, item.updatedAt(), item.fee());
    }

    @Override
    public List<ParcelLookupResult> getCustomerParcels(String status, int page, int size, String callerToken) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 50");
        }
        ParcelServiceClient.ParcelPage result = parcelClient.list(normalizeStatus(status), page, size, callerToken);
        if (result == null || result.content() == null) {
            throw new IllegalStateException("Parcel service returned no parcel list");
        }
        return result.content().stream()
                .map(item -> new ParcelLookupResult(item.trackingNumber(), item.status(), null,
                        item.courierId() != null, item.updatedAt(), item.fee()))
                .toList();
    }

    @Override
    public ParcelHistoryResult getTrackingHistory(String trackingNumber, String callerToken) {
        ParcelServiceClient.ParcelDetail parcel = findOwnedDetail(requireTrackingNumber(trackingNumber), callerToken);
        ParcelServiceClient.ParcelHistory history = parcelClient.history(parcel.id(), callerToken);
        if (history == null) {
            throw new IllegalStateException("Parcel service returned no tracking history");
        }
        List<HistoryEntry> entries = history.entries() == null ? List.of() : history.entries().stream()
                .map(item -> new HistoryEntry(item.toStatus(), item.occurredAt()))
                .toList();
        return new ParcelHistoryResult(parcel.trackingNumber(), parcel.status(), entries);
    }

    @Override
    public DeliveryInfoResult getDeliveryInformation(String trackingNumber, String callerToken) {
        ParcelServiceClient.ParcelDetail parcel = findOwnedDetail(requireTrackingNumber(trackingNumber), callerToken);
        String district = parcel.dropoffAddress() == null ? null : parcel.dropoffAddress().district();
        return new DeliveryInfoResult(parcel.trackingNumber(), parcel.status(),
                parcel.courierId() != null, district);
    }

    @Override
    public ParcelSummaryResult getParcelSummary(String callerToken) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        ParcelServiceClient.ParcelPage page = parcelClient.list(null, 0, MAX_PAGE_SIZE, callerToken);
        for (int current = 0; page != null && current < Math.min(page.totalPages(), 100); current++) {
            if (current > 0) {
                page = parcelClient.list(null, current, MAX_PAGE_SIZE, callerToken);
            }
            if (page != null && page.content() != null) {
                page.content().forEach(item -> counts.merge(item.status(), 1, Integer::sum));
            }
            total = page == null ? total : (int) Math.min(Integer.MAX_VALUE, page.totalElements());
        }
        return new ParcelSummaryResult(total, counts);
    }

    @Override
    public FeeEstimateResult estimateFee(
            String pickupDistrict, String destinationDistrict, double weightKg, String sizeCategory, String callerToken) {
        if (pickupDistrict == null || pickupDistrict.isBlank()
                || destinationDistrict == null || destinationDistrict.isBlank()
                || weightKg < 0.1 || weightKg > 50
                || !isSupportedSize(sizeCategory)) {
            throw new IllegalArgumentException("Valid pickup, destination, weight, and size are required");
        }
        ParcelServiceClient.Quote quote = parcelClient.quote(
                pickupDistrict.trim(), destinationDistrict.trim(), weightKg,
                sizeCategory.trim().toUpperCase(Locale.ROOT), callerToken);
        if (quote == null || quote.totalFee() == null || quote.currency() == null) {
            throw new IllegalStateException("Pricing service returned no quote");
        }
        return new FeeEstimateResult(weightKg, sizeCategory.trim().toUpperCase(Locale.ROOT),
                destinationDistrict.trim(), quote.totalFee(), quote.currency());
    }

    private ParcelServiceClient.ParcelListItem findOwnedParcel(String trackingNumber, String token) {
        ParcelServiceClient.ParcelPage page = parcelClient.list(null, 0, MAX_PAGE_SIZE, token);
        int pages = page == null ? 0 : Math.min(page.totalPages(), 100);
        for (int current = 0; current < pages; current++) {
            if (current > 0) {
                page = parcelClient.list(null, current, MAX_PAGE_SIZE, token);
            }
            if (page != null && page.content() != null) {
                var match = page.content().stream()
                        .filter(item -> trackingNumber.equals(item.trackingNumber()))
                        .findFirst();
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }
        throw new ParcelNotFoundException();
    }

    private ParcelServiceClient.ParcelDetail findOwnedDetail(String trackingNumber, String token) {
        ParcelServiceClient.ParcelListItem item = findOwnedParcel(trackingNumber, token);
        ParcelServiceClient.ParcelDetail detail = parcelClient.detail(item.id(), token);
        if (detail == null || !Objects.equals(trackingNumber, detail.trackingNumber())) {
            throw new ParcelNotFoundException();
        }
        return detail;
    }

    private static String requireTrackingNumber(String value) {
        if (value == null || !TRACKING_NUMBER.matcher(value.trim().toUpperCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Invalid tracking number");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isSupportedSize(String sizeCategory) {
        if (sizeCategory == null) {
            return false;
        }
        String normalized = sizeCategory.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("SMALL") || normalized.equals("MEDIUM") || normalized.equals("LARGE");
    }

    public static final class ParcelNotFoundException extends RuntimeException {
        public ParcelNotFoundException() {
            super("Parcel was not found for the authenticated customer");
        }
    }
}
