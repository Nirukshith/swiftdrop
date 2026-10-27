package com.swiftdrop.integration.merchant;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Page<Merchant> findAllByStatus(MerchantStatus status, Pageable pageable);
}
