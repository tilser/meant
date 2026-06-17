package com.meant.api.module.user.entity;

import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_product_search_events")
public class UserProductSearchEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID merchantId;

    @Column(nullable = false)
    private String originalQuery;

    @Column(nullable = false)
    private String normalizedOriginalQuery;

    @Column(nullable = false)
    private String displayQuery;

    @Column(nullable = false)
    private String normalizedDisplayQuery;

    @Column(nullable = false)
    private String searchQuery;

    @Column(nullable = false)
    private String intentCacheKey;

    @Column(nullable = false)
    private int resultCount;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private Instant createdAt;

    public static UserProductSearchEvent from(
            UUID userId,
            UUID merchantId,
            String model,
            String promptVersion,
            UserProductSearchQueryIntentResult queryIntent,
            int resultCount,
            Instant now
    ) {
        return UserProductSearchEvent.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .merchantId(merchantId)
                .originalQuery(queryIntent.originalQuery())
                .normalizedOriginalQuery(queryIntent.normalizedOriginalQuery())
                .displayQuery(queryIntent.displayQuery())
                .normalizedDisplayQuery(queryIntent.normalizedDisplayQuery())
                .searchQuery(queryIntent.searchQuery())
                .intentCacheKey(queryIntent.intentCacheKey())
                .resultCount(resultCount)
                .model(model)
                .promptVersion(promptVersion)
                .createdAt(now)
                .build();
    }
}
