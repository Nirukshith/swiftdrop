package com.swiftdrop.integration.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.integration.notification.channel.EmailChannel;
import com.swiftdrop.integration.notification.channel.NotificationMessage;
import com.swiftdrop.integration.notification.channel.SmsChannel;
import com.swiftdrop.integration.notification.consumer.NotificationEventConsumer;
import com.swiftdrop.integration.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared rule G5: contact details must never reach application logs unmasked.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationPiiLoggingTest {

    private static final String PHONE = "+94771234567";
    private static final String EMAIL = "alice.customer@example.com";
    private static final String EVENT_ID = "7f1c2a9e-3b4d-4e5f-8a6b-1c2d3e4f5a6b";

    private final NotificationEventConsumer consumer = new NotificationEventConsumer(
            mock(NotificationService.class), new ObjectMapper().findAndRegisterModules());

    private static String userRegisteredPayload() {
        return """
                {"eventId":"%s","eventType":"USER_REGISTERED","userId":"usr-1","email":"%s",
                 "fullName":"Alice","phoneNumber":"%s","role":"CUSTOMER","enabled":true}
                """.formatted(EVENT_ID, EMAIL, PHONE);
    }

    @Test
    @DisplayName("user.registered consumer logs the eventId but not the phone number or email")
    void userRegistered_logsNoContactDetails(CapturedOutput output) throws Exception {
        consumer.onUserRegistered(userRegisteredPayload());

        assertThat(output.getAll()).contains(EVENT_ID).doesNotContain(PHONE).doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("Dead-letter handler logs the eventId but not the payload's contact details")
    void deadLetter_logsNoContactDetails(CapturedOutput output) {
        consumer.handleDlt(userRegisteredPayload(), "user.registered.v1.dlt");
        consumer.handleDlt("not-json " + PHONE + " " + EMAIL, "parcel.status-changed.v1.dlt");

        assertThat(output.getAll())
                .contains(EVENT_ID)
                .contains("<unparseable payload>")
                .doesNotContain(PHONE)
                .doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("EmailChannel logs a masked recipient address")
    @SuppressWarnings("unchecked")
    void emailChannel_masksRecipient(CapturedOutput output) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JavaMailSender.class));

        new EmailChannel(provider).send(message(EMAIL));

        assertThat(output.getAll()).contains("a***@example.com").doesNotContain(EMAIL);
    }

    @Test
    @DisplayName("SmsChannel logs a masked phone number")
    void smsChannel_masksPhone(CapturedOutput output) {
        new SmsChannel().send(message(PHONE));

        assertThat(output.getAll()).contains("+9477 *** *567").doesNotContain(PHONE);
    }

    @Test
    @DisplayName("maskEmail handles malformed and missing addresses")
    void maskEmail_edgeCases() {
        assertThat(EmailChannel.maskEmail(null)).isEqualTo("***");
        assertThat(EmailChannel.maskEmail("no-at-sign")).isEqualTo("***");
        assertThat(EmailChannel.maskEmail("@example.com")).isEqualTo("***");
        assertThat(EmailChannel.maskEmail("bob@example.com")).isEqualTo("b***@example.com");
    }

    private static NotificationMessage message(String recipient) {
        return new NotificationMessage("notif-1", EVENT_ID, "usr-1", null, null,
                recipient, "Welcome to SwiftDrop!", "Hello Alice", null, Instant.now());
    }
}
