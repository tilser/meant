package com.meant.api.plugin.checkout.common.repository;

import com.meant.api.plugin.checkout.common.entity.BuyerConsent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyerConsentRepository extends JpaRepository<BuyerConsent, UUID> {

    Optional<BuyerConsent> findByIdAndUserId(UUID id, UUID userId);
}
