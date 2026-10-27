package com.swiftdrop.operations.zones;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByDistrictsContaining(String district);
}
