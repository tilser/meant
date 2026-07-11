package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearchResultItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProductSearchResultItemRepository extends JpaRepository<UserProductSearchResultItem, UUID> {

    List<UserProductSearchResultItem> findBySearchIdOrderByRankAsc(UUID searchId);

    List<UserProductSearchResultItem> findBySearchIdIn(Collection<UUID> searchIds);

    @Query("""
            select item from UserProductSearchResultItem item
            where item.productKey = :productKey
              and item.searchId in (
                select search.id from UserProductSearch search
                where search.userId = :userId
                  and search.expiresAt > :now
                  and search.retentionPolicyFingerprint is not null
              )
            order by item.createdAt desc
            """)
    List<UserProductSearchResultItem> findCurrentByUserAndProductKey(
            @Param("userId") UUID userId,
            @Param("productKey") String productKey,
            @Param("now") java.time.Instant now,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from UserProductSearchResultItem item where item.searchId = :searchId")
    void deleteBySearchId(@Param("searchId") UUID searchId);
}
