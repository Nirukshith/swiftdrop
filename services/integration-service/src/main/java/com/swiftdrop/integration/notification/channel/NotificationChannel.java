package com.swiftdrop.integration.notification.channel;

public interface NotificationChannel {
    String getChannelType();
    void send(NotificationMessage message);
}

