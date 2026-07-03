package com.meant.api.module.discount.repository;

import com.meant.api.module.discount.entity.DiscountCodeSearch;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountCodeSearchRepository extends JpaRepository<DiscountCodeSearch, UUID> {

    Optional<DiscountCodeSearch> findFirstByMerchant_IdAndExpiresAtAfterOrderBySearchedAtDesc(
            UUID merchantId,
            Instant now
    );
}
