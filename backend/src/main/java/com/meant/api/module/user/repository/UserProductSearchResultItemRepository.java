package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserProductSearchResultItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProductSearchResultItemRepository extends JpaRepository<UserProductSearchResultItem, UUID> {

    List<UserProductSearchResultItem> findBySearchIdOrderByRankAsc(UUID searchId);

    void deleteBySearchId(UUID searchId);
}
