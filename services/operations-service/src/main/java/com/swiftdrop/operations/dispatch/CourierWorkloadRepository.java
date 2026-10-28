package com.swiftdrop.operations.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourierWorkloadRepository extends JpaRepository<CourierWorkloadEntity, String> {

    Optional<CourierWorkloadEntity> findByCourierId(String courierId);
}
