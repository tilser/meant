package com.meant.api.module.user.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ordered canonical-product identifier in one durable Discover result set. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_discover_product_result_items")
public class UserDiscoverProductResultItem extends AssignedIdEntity<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID resultSetId;

    @Column(nullable = false, updatable = false)
    private String canonicalProductKey;

    @Column(nullable = false, updatable = false)
    private int resultRank;

    public static UserDiscoverProductResultItem create(
            UUID resultSetId,
            String canonicalProductKey,
            int resultRank
    ) {
        return UserDiscoverProductResultItem.builder()
                .id(UUID.randomUUID())
                .resultSetId(resultSetId)
                .canonicalProductKey(canonicalProductKey)
                .resultRank(resultRank)
                .build();
    }
}
