package com.swiftdrop.integration.notification.service;

import com.swiftdrop.common.EventEnvelope;
import com.swiftdrop.integration.notification.channel.EmailChannel;
import com.swiftdrop.integration.notification.channel.NotificationMessage;
import com.swiftdrop.integration.notification.channel.SmsChannel;
import com.swiftdrop.integration.notification.client.AuthContactClient;
import com.swiftdrop.integration.notification.client.ContactResolutionException;
import com.swiftdrop.integration.notification.dto.NotificationDto;
import com.swiftdrop.integration.notification.dto.NotificationPageResponse;
import com.swiftdrop.integration.notification.dto.UserContactDto;
import com.swiftdrop.integration.notification.model.NotificationLogEntity;
import com.swiftdrop.integration.notification.model.NotificationProcessedEventEntity;
import com.swiftdrop.integration.notification.model.NotificationProcessedEventId;
import com.swiftdrop.integration.notification.repository.NotificationLogRepository;
import com.swiftdrop.integration.notification.repository.NotificationProcessedEventRepository;
import com.swiftdrop.integration.notification.template.NotificationTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final String CONSUMER_GROUP = "integration-notification";

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationProcessedEventRepository processedEventRepository;
    private final AuthContactClient authContactClient;
    private final NotificationTemplateEngine templateEngine;
    private final EmailChannel emailChannel;
    private final SmsChannel smsChannel;

    public NotificationService(
            NotificationLogRepository notificationLogRepository,
            NotificationProcessedEventRepository processedEventRepository,
            AuthContactClient authContactClient,
            NotificationTemplateEngine templateEngine,
            EmailChannel emailChannel,
            SmsChannel smsChannel) {
        this.notificationLogRepository = notificationLogRepository;
        this.processedEventRepository = processedEventRepository;
        this.authContactClient = authContactClient;
        this.templateEngine = templateEngine;
        this.emailChannel = emailChannel;
        this.smsChannel = smsChannel;
    }

    @Transactional
    public void processStatusChangeEvent(EventEnvelope event) {
        if (event == null || event.eventId() == null) {
            log.warn("Received empty event or event without eventId");
            return;
        }

        UUID eventUuid;
        try {
            eventUuid = UUID.fromString(event.eventId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid eventId UUID: {}", event.eventId());
            return;
        }

        // 1. Consumer-scoped idempotency check by eventId
        if (processedEventRepository.existsById(new NotificationProcessedEventId(eventUuid, CONSUMER_GROUP))) {
            log.info("Event {} already processed by group {}, skipping duplicate", event.eventId(), CONSUMER_GROUP);
            return;
        }

        // 2. Out-of-order check: ignore stale events using sequence per ADR 005 and design note
        if (event.parcelId() != null && event.sequence() != null) {
            Optional<Long> maxSeq = notificationLogRepository.findMaxSequenceByParcelId(event.parcelId());
            if (maxSeq.isPresent() && event.sequence() <= maxSeq.get()) {
                log.info("Stale event {} for parcel {} (seq {} <= max observed seq {}), ignoring",
                        event.eventId(), event.parcelId(), event.sequence(), maxSeq.get());
                processedEventRepository.save(new NotificationProcessedEventEntity(eventUuid, CONSUMER_GROUP, Instant.now()));
                return;
            }
        }

        // 3. User contact resolution
        String customerId = event.customerId();
        if (customerId == null || customerId.isBlank()) {
            log.warn("Event {} has no customerId; cannot resolve recipient", event.eventId());
            processedEventRepository.save(new NotificationProcessedEventEntity(eventUuid, CONSUMER_GROUP, Instant.now()));
            return;
        }

        Optional<UserContactDto> contactOpt = authContactClient.getContact(customerId);
        if (contactOpt.isEmpty()) {
            log.error("Could not resolve contact for customerId: {}", customerId);
            throw new ContactResolutionException("Could not resolve contact for customer " + customerId);
        }
        UserContactDto contact = contactOpt.get();

        // 4. Render template
        NotificationTemplateEngine.RenderedTemplate rendered = templateEngine.render(event);

        // 5. Store IN_APP notification (so the notification bell can display it)
        String inAppId = "notif-" + UUID.randomUUID();
        NotificationLogEntity inAppLog = new NotificationLogEntity(
                inAppId,
                event.eventId(),
                customerId,
                event.parcelId(),
                event.trackingNumber(),
                "IN_APP",
                customerId,
                rendered.subject(),
                rendered.body(),
                false,
                null,
                event.sequence(),
                Instant.now(),
                Instant.now()
        );
        notificationLogRepository.save(inAppLog);

        // 6. Send and store EMAIL notification
        if (contact.email() != null && !contact.email().isBlank()) {
            String emailId = "email-" + UUID.randomUUID();
            NotificationMessage emailMessage = new NotificationMessage(
                    emailId,
                    event.eventId(),
                    customerId,
                    event.parcelId(),
                    event.trackingNumber(),
                    contact.email(),
                    rendered.subject(),
                    rendered.body(),
                    event.sequence(),
                    Instant.now()
            );

            emailChannel.send(emailMessage);

            NotificationLogEntity emailLog = new NotificationLogEntity(
                    emailId,
                    event.eventId(),
                    customerId,
                    event.parcelId(),
                    event.trackingNumber(),
                    "EMAIL",
                    contact.email(),
                    rendered.subject(),
                    rendered.body(),
                    false,
                    null,
                    event.sequence(),
                    Instant.now(),
                    Instant.now()
            );
            notificationLogRepository.save(emailLog);
        }

        // 7. Dispatch SMS if phone number available
        if (contact.phoneNumber() != null && !contact.phoneNumber().isBlank()) {
            NotificationMessage smsMessage = new NotificationMessage(
                    "sms-" + UUID.randomUUID(),
                    event.eventId(),
                    customerId,
                    event.parcelId(),
                    event.trackingNumber(),
                    contact.phoneNumber(),
                    rendered.subject(),
                    rendered.body(),
                    event.sequence(),
                    Instant.now()
            );
            smsChannel.send(smsMessage);
        }

        // 8. Commit processed event record
        processedEventRepository.save(new NotificationProcessedEventEntity(eventUuid, CONSUMER_GROUP, Instant.now()));
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(String userId, int page, int size, boolean unreadOnly) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));

        Page<NotificationLogEntity> logPage = unreadOnly
                ? notificationLogRepository.findByUserIdAndChannelAndReadFalseOrderByCreatedAtDesc(userId, "IN_APP", pageable)
                : notificationLogRepository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, "IN_APP", pageable);

        long unreadCount = notificationLogRepository.countByUserIdAndChannelAndReadFalse(userId, "IN_APP");

        List<NotificationDto> dtoList = logPage.getContent().stream()
                .map(this::toDto)
                .toList();

        return new NotificationPageResponse(dtoList, logPage.getTotalElements(), unreadCount);
    }

    @Transactional
    public NotificationDto markAsRead(String id, String userId) {
        NotificationLogEntity entity = notificationLogRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found with id: " + id));

        if (!entity.isRead()) {
            entity.setRead(true);
            entity.setReadAt(Instant.now());
            notificationLogRepository.save(entity);
        }

        return toDto(entity);
    }

    private NotificationDto toDto(NotificationLogEntity entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getChannel(),
                entity.getParcelId(),
                entity.getTrackingNumber(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }
}

