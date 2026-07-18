package com.meant.api.module.user.entity;

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

/** Durable identifiers-only handle for one rendered Discover search page. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_discover_product_result_sets")
public class UserDiscoverProductResultSet {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private UUID qualificationId;

    @Column(nullable = false, updatable = false)
    private int pageOffset;

    @Column(nullable = false, updatable = false)
    private int resultLimit;

    @Column(nullable = false)
    private int resultCount;

    private Integer nextOffset;

    @Column(nullable = false)
    private boolean hasMore;

    @Column(nullable = false)
    private boolean upstreamTruncated;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserDiscoverProductResultSet create(
            UUID userId,
            UUID conversationId,
            UUID qualificationId,
            int pageOffset,
            int resultLimit,
            int resultCount,
            Integer nextOffset,
            boolean hasMore,
            boolean upstreamTruncated,
            Instant now
    ) {
        return UserDiscoverProductResultSet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .conversationId(conversationId)
                .qualificationId(qualificationId)
                .pageOffset(pageOffset)
                .resultLimit(resultLimit)
                .resultCount(resultCount)
                .nextOffset(nextOffset)
                .hasMore(hasMore)
                .upstreamTruncated(upstreamTruncated)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void refresh(
            int resultCount,
            Integer nextOffset,
            boolean hasMore,
            boolean upstreamTruncated,
            Instant now
    ) {
        this.resultCount = resultCount;
        this.nextOffset = nextOffset;
        this.hasMore = hasMore;
        this.upstreamTruncated = upstreamTruncated;
        this.updatedAt = now;
    }
}
