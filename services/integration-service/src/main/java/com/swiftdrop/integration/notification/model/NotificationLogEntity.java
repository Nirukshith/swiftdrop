package com.swiftdrop.integration.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notification_log")
public class NotificationLogEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "parcel_id", length = 64)
    private String parcelId;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "channel", length = 16, nullable = false)
    private String channel;

    @Column(name = "recipient_target", length = 255, nullable = false)
    private String recipientTarget;

    @Column(name = "title", length = 128, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "sequence")
    private Long sequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public NotificationLogEntity() {}

    public NotificationLogEntity(
            String id,
            String eventId,
            String userId,
            String parcelId,
            String trackingNumber,
            String channel,
            String recipientTarget,
            String title,
            String content,
            boolean read,
            Instant readAt,
            Long sequence,
            Instant createdAt,
            Instant sentAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.parcelId = parcelId;
        this.trackingNumber = trackingNumber;
        this.channel = channel;
        this.recipientTarget = recipientTarget;
        this.title = title;
        this.content = content;
        this.read = read;
        this.readAt = readAt;
        this.sequence = sequence;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getParcelId() {
        return parcelId;
    }

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getRecipientTarget() {
        return recipientTarget;
    }

    public void setRecipientTarget(String recipientTarget) {
        this.recipientTarget = recipientTarget;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}

