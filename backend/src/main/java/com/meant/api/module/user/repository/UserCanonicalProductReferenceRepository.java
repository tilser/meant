package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserCanonicalProductReference;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCanonicalProductReferenceRepository
        extends JpaRepository<UserCanonicalProductReference, UUID> {

    List<UserCanonicalProductReference> findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(
            UUID userId,
            String canonicalProductKey
    );

    List<UserCanonicalProductReference> findByUserIdAndCanonicalProductKeyInOrderByCanonicalProductKeyAscOfferRankAscIdAsc(
            UUID userId,
            List<String> canonicalProductKeys
    );

    List<UserCanonicalProductReference> findByUserIdAndOfferKeyOrderByReferenceVerifiedAtDescIdAsc(
            UUID userId,
            String offerKey
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from UserCanonicalProductReference reference
            where reference.userId = :userId
              and reference.canonicalProductKey in :canonicalProductKeys
            """)
    int deleteByUserIdAndCanonicalProductKeyIn(
            @Param("userId") UUID userId,
            @Param("canonicalProductKeys") Collection<String> canonicalProductKeys
    );
}
