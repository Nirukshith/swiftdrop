package com.swiftdrop.integration.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsChannel.class);

    @Override
    public String getChannelType() {
        return "SMS";
    }

    @Override
    public void send(NotificationMessage message) {
        String masked = maskPhoneNumber(message.recipientTarget());
        log.info("[SMS Dispatch] To: {} - Title: {} - Body: {}", masked, message.title(), message.content());
    }

    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 5) + " *** *" + phone.substring(phone.length() - 3);
    }
}

