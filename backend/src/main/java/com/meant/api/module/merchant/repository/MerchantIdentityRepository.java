package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantIdentityRepository extends JpaRepository<MerchantIdentity, UUID> {

    @EntityGraph(attributePaths = "merchant")
    Optional<MerchantIdentity> findByNamespaceAndNormalizedValue(
            MerchantIdentityNamespace namespace,
            String normalizedValue
    );

    List<MerchantIdentity> findByMerchantIdOrderByVerifiedAtAsc(UUID merchantId);
}
