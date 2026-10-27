package com.swiftdrop.integration.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "webhook_delivery_attempts")
public class WebhookDeliveryAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private WebhookDeliveryEntity delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public WebhookDeliveryAttemptEntity() {
    }

    public WebhookDeliveryAttemptEntity(WebhookDeliveryEntity delivery, int attemptNumber,
                                        Instant attemptedAt, Integer httpStatusCode,
                                        int latencyMs, boolean success, String errorMessage) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.httpStatusCode = httpStatusCode;
        this.latencyMs = latencyMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WebhookDeliveryEntity getDelivery() {
        return delivery;
    }

    public void setDelivery(WebhookDeliveryEntity delivery) {
        this.delivery = delivery;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
