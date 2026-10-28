package com.swiftdrop.operations.courier.execution;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_proof")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryProofEntity {

    @Id
    private UUID id;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "courier_id", nullable = false, length = 64)
    private String courierId;

    @Column(name = "proof_type", nullable = false, length = 32)
    private String proofType;

    @Column(name = "proof_url", nullable = false, length = 512)
    private String proofUrl;

    @Column(name = "recipient_name", length = 128)
    private String recipientName;

    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
