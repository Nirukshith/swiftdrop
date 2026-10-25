package com.swiftdrop.integration.assistant.tools;

import com.swiftdrop.integration.assistant.dto.FeeEstimateResult;
import com.swiftdrop.integration.assistant.dto.ParcelStatusResult;
import com.swiftdrop.integration.assistant.dto.RecentParcelItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Skeleton implementation of AssistantTools.
 * In the integration phase, this client will call the internal/public REST endpoints of
 * parcel-service and operations-service forwarding the caller's JWT bearer token.
 */
@Service
public class AssistantToolsImpl implements AssistantTools {

    @Override
    public ParcelStatusResult getParcelStatus(String trackingNo, String callerToken) {
        // Week 1 skeleton: returns mock result matching SRS demo scenarios
        return new ParcelStatusResult(
            trackingNo,
            "OUT_FOR_DELIVERY",
            "Colombo",
            "Kandy",
            Instant.now()
        );
    }

    @Override
    public FeeEstimateResult estimateFee(Double weightKg, String sizeCategory, String destinationDistrict, String callerToken) {
        // Week 1 skeleton: mirrors ADR 003 formula (drop-off base fee + weight-band + size charge)
        return new FeeEstimateResult(
            weightKg != null ? weightKg : 1.0,
            sizeCategory != null ? sizeCategory : "MEDIUM",
            destinationDistrict != null ? destinationDistrict : "Kandy",
            BigDecimal.valueOf(650.00),
            "LKR"
        );
    }

    @Override
    public List<RecentParcelItem> listMyRecentParcels(String callerToken) {
        // Week 1 skeleton: returns empty list or stub items
        return Collections.singletonList(
            new RecentParcelItem("SD-20261024-001", "OUT_FOR_DELIVERY", "Nimal Silva", Instant.now())
        );
    }
}
