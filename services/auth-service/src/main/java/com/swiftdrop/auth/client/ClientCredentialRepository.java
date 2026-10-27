package com.swiftdrop.auth.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCredentialRepository extends JpaRepository<ClientCredential, String> {

    Optional<ClientCredential> findByClientId(String clientId);

    List<ClientCredential> findByMerchantId(String merchantId);

    boolean existsByMerchantId(String merchantId);
}
