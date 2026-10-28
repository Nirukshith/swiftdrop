package com.swiftdrop.operations.courier.execution.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryProofInfo(
    UUID proofId,
    String proofType,
    String proofUrl,
    String recipientName,
    Instant uploadedAt
) {}
