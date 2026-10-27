package com.swiftdrop.operations.zones;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<ZoneAuditService.AuditLog, UUID> {
}
