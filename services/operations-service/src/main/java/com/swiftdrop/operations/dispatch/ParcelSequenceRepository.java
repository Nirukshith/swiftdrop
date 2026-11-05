package com.swiftdrop.operations.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParcelSequenceRepository extends JpaRepository<ParcelSequenceEntity, String> {
}

