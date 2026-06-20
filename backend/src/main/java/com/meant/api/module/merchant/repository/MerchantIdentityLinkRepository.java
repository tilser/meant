package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantIdentityLink;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MerchantIdentityLinkRepository extends JpaRepository<MerchantIdentityLink, UUID> {

    @EntityGraph(attributePaths = "merchant")
    List<MerchantIdentityLink> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<MerchantIdentityLink> findByUserIdAndMerchantId(UUID userId, UUID merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from MerchantIdentityLink l where l.userId = :userId and l.merchant.id = :merchantId")
    Optional<MerchantIdentityLink> findByUserIdAndMerchantIdForUpdate(UUID userId, UUID merchantId);

    Optional<MerchantIdentityLink> findByStateHash(String stateHash);

    void deleteByUserIdAndMerchantId(UUID userId, UUID merchantId);
}
