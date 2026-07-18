package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserDiscoverProductResultItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDiscoverProductResultItemRepository
        extends JpaRepository<UserDiscoverProductResultItem, UUID> {

    List<UserDiscoverProductResultItem> findByResultSetIdOrderByResultRankAsc(UUID resultSetId);

    long deleteByResultSetId(UUID resultSetId);
}
