package com.swiftdrop.integration.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private UUID id;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Column(name = "business_registration_number", length = 128)
    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MerchantStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "auth_user_id", length = 64)
    private String authUserId;

    @Column(name = "client_id", length = 128)
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Merchant() {}

    public Merchant(UUID id, String businessName, String contactName, String email, String phoneNumber,
                    String businessRegistrationNumber, Instant now) {
        this.id = id;
        this.businessName = businessName;
        this.contactName = contactName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.status = MerchantStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setAuthUserId(String authUserId) {
        this.authUserId = authUserId;
        this.updatedAt = Instant.now();
    }

    public void approve(String clientId) {
        transitionTo(MerchantStatus.APPROVED);
        this.clientId = clientId;
    }

    public void reject(String reason) {
        transitionTo(MerchantStatus.REJECTED);
        this.rejectionReason = reason;
    }

    private void transitionTo(MerchantStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Merchant cannot transition from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBusinessName() { return businessName; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getBusinessRegistrationNumber() { return businessRegistrationNumber; }
    public MerchantStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public String getAuthUserId() { return authUserId; }
    public String getClientId() { return clientId; }
    public Instant getCreatedAt() { return createdAt; }
}
