package com.swiftdrop.integration.notification.repository;

import com.swiftdrop.integration.notification.model.NotificationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, String> {

    boolean existsByEventIdAndChannel(String eventId, String channel);

    @Query("SELECT MAX(n.sequence) FROM NotificationLogEntity n WHERE n.parcelId = :parcelId")
    Optional<Long> findMaxSequenceByParcelId(@Param("parcelId") String parcelId);

    Page<NotificationLogEntity> findByUserIdAndChannelOrderByCreatedAtDesc(String userId, String channel, Pageable pageable);

    Page<NotificationLogEntity> findByUserIdAndChannelAndReadFalseOrderByCreatedAtDesc(String userId, String channel, Pageable pageable);

    long countByUserIdAndChannelAndReadFalse(String userId, String channel);

    Optional<NotificationLogEntity> findByIdAndUserId(String id, String userId);
}

