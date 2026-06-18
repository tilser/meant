package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.UserInventoryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInventoryItemRepository extends JpaRepository<UserInventoryItem, UUID> {

    List<UserInventoryItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<UserInventoryItem> findByUserIdAndCategoryOrderByUpdatedAtDesc(UUID userId, UserInventoryCategory category);

    List<UserInventoryItem> findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc(UUID userId);

    List<UserInventoryItem> findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc(
            UUID userId,
            UserInventoryCategory category
    );

    Optional<UserInventoryItem> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserInventoryItem> findByUserIdAndSourceAndSourceProductKey(
            UUID userId,
            UserInventorySource source,
            String sourceProductKey
    );

    long deleteByIdAndUserId(UUID id, UUID userId);
}
