package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.UserInventoryItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInventoryItemRepository extends JpaRepository<UserInventoryItem, UUID> {

    List<UserInventoryItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<UserInventoryItem> findByUserIdAndCategoryOrderByUpdatedAtDesc(UUID userId, UserInventoryCategory category);

    List<UserInventoryItem> findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc(UUID userId);

    List<UserInventoryItem> findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc(
            UUID userId,
            UserInventoryCategory category
    );

    long countByUserId(UUID userId);

    @Query("select max(item.updatedAt) from UserInventoryItem item where item.userId = :userId")
    Optional<Instant> findMaxUpdatedAtByUserId(@Param("userId") UUID userId);

    Optional<UserInventoryItem> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserInventoryItem> findByUserIdAndSourceAndSourceProductKey(
            UUID userId,
            UserInventorySource source,
            String sourceProductKey
    );

    long deleteByIdAndUserId(UUID id, UUID userId);
}
