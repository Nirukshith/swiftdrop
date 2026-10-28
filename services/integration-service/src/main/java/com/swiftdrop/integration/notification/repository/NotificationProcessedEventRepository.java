package com.swiftdrop.integration.notification.repository;

import com.swiftdrop.integration.notification.model.NotificationProcessedEventEntity;
import com.swiftdrop.integration.notification.model.NotificationProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationProcessedEventRepository extends JpaRepository<NotificationProcessedEventEntity, NotificationProcessedEventId> {
}

