package com.swiftdrop.integration.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpointEntity {

    @Id
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "subscribed_events", nullable = false, length = 255)
    private String subscribedEvents = "PARCEL_CREATED,PARCEL_STATUS_CHANGED";

    @Column(name = "encrypted_secret", nullable = false, length = 512)
    private String encryptedSecret;

    @Column(name = "secret_preview", nullable = false, length = 64)
    private String secretPreview;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public WebhookEndpointEntity() {
    }

    public WebhookEndpointEntity(UUID merchantId, String url, boolean active,
                                 String subscribedEvents, String encryptedSecret,
                                 String secretPreview) {
        this.merchantId = merchantId;
        this.url = url;
        this.active = active;
        this.subscribedEvents = subscribedEvents != null ? subscribedEvents : "PARCEL_CREATED,PARCEL_STATUS_CHANGED";
        this.encryptedSecret = encryptedSecret;
        this.secretPreview = secretPreview;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public List<String> getSubscribedEventsList() {
        if (subscribedEvents == null || subscribedEvents.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(subscribedEvents.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setSubscribedEventsList(List<String> events) {
        if (events == null || events.isEmpty()) {
            this.subscribedEvents = "";
        } else {
            this.subscribedEvents = String.join(",", events);
        }
    }

    public boolean isSubscribedTo(String eventType) {
        if (eventType == null || !active) {
            return false;
        }
        return getSubscribedEventsList().contains(eventType);
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }

    public String getSecretPreview() {
        return secretPreview;
    }

    public void setSecretPreview(String secretPreview) {
        this.secretPreview = secretPreview;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
