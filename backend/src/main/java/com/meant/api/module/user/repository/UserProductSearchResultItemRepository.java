package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearchResultItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProductSearchResultItemRepository extends JpaRepository<UserProductSearchResultItem, UUID> {

    List<UserProductSearchResultItem> findBySearchIdOrderByRankAsc(UUID searchId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserProductSearchResultItem item where item.searchId = :searchId")
    void deleteBySearchId(@Param("searchId") UUID searchId);
}
