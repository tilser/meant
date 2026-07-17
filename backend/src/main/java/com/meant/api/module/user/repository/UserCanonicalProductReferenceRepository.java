package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserCanonicalProductReference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCanonicalProductReferenceRepository
        extends JpaRepository<UserCanonicalProductReference, UUID> {

    List<UserCanonicalProductReference> findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(
            UUID userId,
            String canonicalProductKey
    );

    long deleteByUserIdAndCanonicalProductKey(UUID userId, String canonicalProductKey);
}
