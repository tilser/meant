package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserShoppingFilter;
import com.meant.api.module.user.entity.UserShoppingFilterId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserShoppingFilterRepository extends JpaRepository<UserShoppingFilter, UserShoppingFilterId> {

    @Query("""
            select userShoppingFilter.id.filterId from UserShoppingFilter userShoppingFilter
            where userShoppingFilter.id.userId = :userId
            """)
    List<String> findFilterIdsByUserId(@Param("userId") UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from UserShoppingFilter userShoppingFilter
            where userShoppingFilter.id.userId = :userId
            """)
    void deleteByIdUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_shopping_filters (user_id, filter_id, created_at)
            SELECT :userId, filter.id, :now
            FROM shopping_filters filter
            WHERE filter.id IN (:filterIds)
            ON CONFLICT (user_id, filter_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(
            @Param("userId") UUID userId,
            @Param("filterIds") Collection<String> filterIds,
            @Param("now") Instant now
    );
}
