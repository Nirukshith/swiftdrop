package com.swiftdrop.operations.courier.execution.dto;

import java.time.Instant;
import java.util.UUID;

public record ProofUploadResponse(
    UUID proofId,
    UUID parcelId,
    String proofUrl,
    String mimeType,
    Long fileSizeBytes,
    Instant uploadedAt
) {}
