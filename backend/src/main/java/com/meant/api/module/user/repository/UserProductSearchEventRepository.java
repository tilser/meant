package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearchEvent;
import com.meant.api.module.user.service.dto.UserPopularProductSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProductSearchEventRepository extends JpaRepository<UserProductSearchEvent, UUID> {

    @Query("""
            select new com.meant.api.module.user.service.dto.UserPopularProductSearchResult(
                event.displayQuery,
                event.displayQuery,
                count(event),
                count(distinct event.userId),
                max(event.createdAt)
            )
            from UserProductSearchEvent event
            where event.createdAt >= :since
              and event.merchantId is null
              and event.resultCount > 0
              and length(event.displayQuery) between 3 and :maxDisplayLength
            group by event.normalizedDisplayQuery, event.displayQuery
            having count(distinct event.userId) >= :minDistinctUsers
            order by count(event) desc, count(distinct event.userId) desc, max(event.createdAt) desc
            """)
    List<UserPopularProductSearchResult> findPopularSearches(
            @Param("since") Instant since,
            @Param("minDistinctUsers") long minDistinctUsers,
            @Param("maxDisplayLength") int maxDisplayLength,
            Pageable pageable
    );
}
