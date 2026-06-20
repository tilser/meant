package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantIdentityLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantIdentityLinkRepository extends JpaRepository<MerchantIdentityLink, UUID> {

    @EntityGraph(attributePaths = "merchant")
    List<MerchantIdentityLink> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<MerchantIdentityLink> findByUserIdAndMerchantId(UUID userId, UUID merchantId);

    Optional<MerchantIdentityLink> findByStateHash(String stateHash);

    void deleteByUserIdAndMerchantId(UUID userId, UUID merchantId);
}
