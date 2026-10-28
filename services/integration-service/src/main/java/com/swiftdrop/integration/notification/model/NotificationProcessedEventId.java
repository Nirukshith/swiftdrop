package com.swiftdrop.integration.notification.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class NotificationProcessedEventId implements Serializable {

    private UUID eventId;
    private String consumerGroup;

    public NotificationProcessedEventId() {}

    public NotificationProcessedEventId(UUID eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationProcessedEventId that = (NotificationProcessedEventId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumerGroup, that.consumerGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumerGroup);
    }
}

