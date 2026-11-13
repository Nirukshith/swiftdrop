package com.swiftdrop.integration.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.notification.channel.EmailChannel;
import com.swiftdrop.integration.notification.channel.NotificationMessage;
import com.swiftdrop.integration.notification.channel.SmsChannel;
import com.swiftdrop.integration.notification.client.AuthContactClient;
import com.swiftdrop.integration.notification.consumer.NotificationEventConsumer;
import com.swiftdrop.integration.notification.dto.UserRegisteredEvent;
import com.swiftdrop.integration.notification.model.NotificationProcessedEventEntity;
import com.swiftdrop.integration.notification.model.NotificationProcessedEventId;
import com.swiftdrop.integration.notification.repository.NotificationLogRepository;
import com.swiftdrop.integration.notification.repository.NotificationProcessedEventRepository;
import com.swiftdrop.integration.notification.service.NotificationService;
import com.swiftdrop.integration.notification.template.NotificationTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerHardeningTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationProcessedEventRepository processedEventRepository;

    @Mock
    private AuthContactClient authContactClient;

    @Mock
    private NotificationTemplateEngine templateEngine;

    @Mock
    private EmailChannel emailChannel;

    @Mock
    private SmsChannel smsChannel;

    @Mock
    private JavaMailSender javaMailSender;

    private NotificationService notificationService;
    private NotificationEventConsumer notificationConsumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        notificationService = new NotificationService(
                notificationLogRepository,
                processedEventRepository,
                authContactClient,
                templateEngine,
                emailChannel,
                smsChannel
        );
        notificationConsumer = new NotificationEventConsumer(notificationService, objectMapper);
    }

    // =========================================================================
    // 1. Poison Pill & Invalid Envelope Resilience (Section 4)
    // =========================================================================

    @Test
    @DisplayName("Process parcel event safely returns without exception when event is null")
    void processParcelEvent_nullEvent_safelyHandled() {
        assertThatCode(() -> notificationService.processParcelEvent(null))
                .doesNotThrowAnyException();
        verifyNoInteractions(processedEventRepository, notificationLogRepository);
    }

    @Test
    @DisplayName("Process parcel event safely returns without exception when eventId is null")
    void processParcelEvent_nullEventId_safelyHandled() {
        EventEnvelope event = new EventEnvelope(
                null, "PARCEL_CREATED", Instant.now(), 1L, "pcl-1", "SD-001",
                "NONE", "CREATED", null, null, null, "cust-1", null
        );

        assertThatCode(() -> notificationService.processParcelEvent(event))
                .doesNotThrowAnyException();
        verifyNoInteractions(processedEventRepository, notificationLogRepository);
    }

    @Test
    @DisplayName("Process parcel event safely returns when eventId is not a valid UUID")
    void processParcelEvent_invalidUuidEventId_safelyHandled() {
        EventEnvelope event = new EventEnvelope(
                "not-a-valid-uuid", "PARCEL_CREATED", Instant.now(), 1L, "pcl-1", "SD-001",
                "NONE", "CREATED", null, null, null, "cust-1", null
        );

        assertThatCode(() -> notificationService.processParcelEvent(event))
                .doesNotThrowAnyException();
        verifyNoInteractions(processedEventRepository, notificationLogRepository);
    }

    @Test
    @DisplayName("Process parcel event marks event processed and skips when customerId is missing")
    void processParcelEvent_missingCustomerId_marksProcessedAndSkips() {
        UUID eventUuid = UUID.randomUUID();
        EventEnvelope event = new EventEnvelope(
                eventUuid.toString(), "PARCEL_CREATED", Instant.now(), 1L, "pcl-1", "SD-001",
                "NONE", "CREATED", null, null, null, "", null
        );

        when(processedEventRepository.existsById(new NotificationProcessedEventId(eventUuid, NotificationService.CONSUMER_GROUP)))
                .thenReturn(false);

        notificationService.processParcelEvent(event);

        verify(processedEventRepository).save(any(NotificationProcessedEventEntity.class));
        verify(notificationLogRepository, never()).save(any());
        verifyNoInteractions(emailChannel, smsChannel);
    }

    @Test
    @DisplayName("Process user registered event safely handles null or blank userId")
    void processUserRegisteredEvent_blankUserId_safelyHandled() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                UUID.randomUUID().toString(), "USER_REGISTERED", Instant.now(),
                "   ", "user@test.com", "Test User", null, "CUSTOMER", null, true
        );

        assertThatCode(() -> notificationService.processUserRegisteredEvent(event))
                .doesNotThrowAnyException();
        verifyNoInteractions(processedEventRepository, notificationLogRepository);
    }

    @Test
    @DisplayName("Process user registered event safely handles invalid UUID eventId")
    void processUserRegisteredEvent_invalidUuid_safelyHandled() {
        UserRegisteredEvent event = new UserRegisteredEvent(
                "invalid-uuid", "USER_REGISTERED", Instant.now(),
                "usr-1", "user@test.com", "Test User", null, "CUSTOMER", null, true
        );

        assertThatCode(() -> notificationService.processUserRegisteredEvent(event))
                .doesNotThrowAnyException();
        verifyNoInteractions(processedEventRepository, notificationLogRepository);
    }

    // =========================================================================
    // 2. Email Channel Failure & Error Handling (Section 5)
    // =========================================================================

    @Test
    @DisplayName("EmailChannel: Skips dispatch when recipientTarget is null or blank")
    void emailChannel_blankRecipient_skipsDispatch() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        EmailChannel channel = new EmailChannel(provider);

        NotificationMessage message = new NotificationMessage(
                "msg-1", "evt-1", "usr-1", "pcl-1", "SD-001",
                "   ", "Title", "Body", 1L, Instant.now()
        );

        assertThatCode(() -> channel.send(message)).doesNotThrowAnyException();
        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("EmailChannel: Skips dispatch when JavaMailSender is not configured in environment")
    void emailChannel_noMailSender_skipsGracefully() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        EmailChannel channel = new EmailChannel(provider);

        NotificationMessage message = new NotificationMessage(
                "msg-1", "evt-1", "usr-1", "pcl-1", "SD-001",
                "user@example.com", "Title", "Body", 1L, Instant.now()
        );

        assertThat(channel.isConfigured()).isFalse();
        assertThatCode(() -> channel.send(message)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EmailChannel: Dispatches formatted SimpleMailMessage when configured")
    void emailChannel_configured_dispatchesSimpleMailMessage() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(javaMailSender);

        EmailChannel channel = new EmailChannel(provider);

        NotificationMessage message = new NotificationMessage(
                "msg-1", "evt-1", "usr-1", "pcl-1", "SD-001",
                "customer@swiftdrop.lk", "Order Confirmed", "Your parcel is on its way", 1L, Instant.now()
        );

        channel.send(message);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("customer@swiftdrop.lk");
        assertThat(sent.getSubject()).isEqualTo("Order Confirmed");
        assertThat(sent.getText()).isEqualTo("Your parcel is on its way");
        assertThat(sent.getFrom()).isEqualTo("no-reply@swiftdrop.com");
    }

    @Test
    @DisplayName("EmailChannel: Propagates MailSendException so Kafka retry topic catches failure")
    void emailChannel_mailExceptionPropagated_forKafkaRetry() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(javaMailSender);

        EmailChannel channel = new EmailChannel(provider);
        NotificationMessage message = new NotificationMessage(
                "msg-1", "evt-1", "usr-1", "pcl-1", "SD-001",
                "customer@swiftdrop.lk", "Order Confirmed", "Your parcel is on its way", 1L, Instant.now()
        );

        doThrow(new MailSendException("SMTP connection refused: port 1025 unreachable"))
                .when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> channel.send(message))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("SMTP connection refused");
    }

    // =========================================================================
    // 3. DLT Dead Letter Queue Handler Resilience (Section 4 & 5)
    // =========================================================================

    @Test
    @DisplayName("DLT handler executes cleanly when retry attempts are exhausted")
    void dltHandler_executesSafely() {
        assertThatCode(() -> notificationConsumer.handleDlt("{\"invalid\":\"payload\"}", "parcel.status-changed.v1"))
                .doesNotThrowAnyException();
    }
}
