package com.swiftdrop.integration.assistant.tools;

import com.swiftdrop.integration.assistant.dto.FeeEstimateResult;
import com.swiftdrop.integration.assistant.dto.ParcelStatusResult;
import com.swiftdrop.integration.assistant.dto.RecentParcelItem;
import java.util.List;

/**
 * Backend tools contract executable by the LLM.
 * Calls existing REST APIs using the caller's JWT token to preserve permission isolation.
 */
public interface AssistantTools {

    /**
     * Tool 1: Look up current parcel status.
     * Refuses access if the parcel belongs to a different customer.
     */
    ParcelStatusResult getParcelStatus(String trackingNo, String callerToken);

    /**
     * Tool 2: Estimate delivery fee using drop-off zone base fee, weight, and size.
     */
    FeeEstimateResult estimateFee(Double weightKg, String sizeCategory, String destinationDistrict, String callerToken);

    /**
     * Tool 3: List the caller's recent parcels.
     */
    List<RecentParcelItem> listMyRecentParcels(String callerToken);
}
