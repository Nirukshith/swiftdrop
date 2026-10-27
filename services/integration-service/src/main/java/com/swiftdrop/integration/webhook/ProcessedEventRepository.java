package com.swiftdrop.integration.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {

    boolean existsByEventIdAndConsumerGroup(UUID eventId, String consumerGroup);
}
