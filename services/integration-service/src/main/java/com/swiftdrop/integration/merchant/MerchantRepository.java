package com.swiftdrop.integration.merchant;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select merchant from Merchant merchant where merchant.id=:id")
    Optional<Merchant> findForOrder(@Param("id") UUID id);
    boolean existsByEmailIgnoreCase(String email);
    Page<Merchant> findAllByStatus(MerchantStatus status, Pageable pageable);
}
