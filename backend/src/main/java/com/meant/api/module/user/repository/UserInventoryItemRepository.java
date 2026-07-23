package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.UserInventoryItem;
import com.meant.api.module.user.service.dto.UserInventoryProfileSummary;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInventoryItemRepository extends JpaRepository<UserInventoryItem, UUID> {

    List<UserInventoryItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<UserInventoryItem> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    List<UserInventoryItem> findByUserIdAndCategoryOrderByUpdatedAtDesc(UUID userId, UserInventoryCategory category);

    List<UserInventoryItem> findByUserIdAndCategoryOrderByUpdatedAtDesc(
            UUID userId,
            UserInventoryCategory category,
            Pageable pageable
    );

    List<UserInventoryItem> findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc(UUID userId);

    List<UserInventoryItem> findByUserIdAndRestockEnabledTrueOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    List<UserInventoryItem> findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc(
            UUID userId,
            UserInventoryCategory category
    );

    List<UserInventoryItem> findByUserIdAndCategoryAndRestockEnabledTrueOrderByUpdatedAtDesc(
            UUID userId,
            UserInventoryCategory category,
            Pageable pageable
    );

    long countByUserId(UUID userId);

    @Query("""
            select new com.meant.api.module.user.service.dto.UserInventoryProfileSummary(
                count(item),
                max(item.updatedAt)
            )
            from UserInventoryItem item
            where item.userId = :userId
            """)
    UserInventoryProfileSummary summarizeProfileByUserId(@Param("userId") UUID userId);

    Optional<UserInventoryItem> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserInventoryItem> findByUserIdAndSourceAndSourceProductKey(
            UUID userId,
            UserInventorySource source,
            String sourceProductKey
    );

    List<UserInventoryItem> findByUserIdAndSourceAndSourceProductKeyIn(
            UUID userId,
            UserInventorySource source,
            Collection<String> sourceProductKeys
    );

    long deleteByIdAndUserId(UUID id, UUID userId);
}
