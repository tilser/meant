package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "user_product_recommendation_filter_matches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_product_recommendation_filter_matches_filter",
                        columnNames = {"explanation_id", "filter_id", "match_type"}
                )
        }
)
public class UserProductRecommendationFilterMatch {

    public static final String MATCHED = "matched";
    public static final String MISSED = "missed";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID explanationId;

    @Column(nullable = false)
    private String filterId;

    @Column(nullable = false)
    private String matchType;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false)
    private Instant createdAt;

    public static UserProductRecommendationFilterMatch create(
            UUID explanationId,
            String filterId,
            String matchType,
            int rank,
            Instant now
    ) {
        return UserProductRecommendationFilterMatch.builder()
                .id(UUID.randomUUID())
                .explanationId(explanationId)
                .filterId(filterId)
                .matchType(matchType)
                .rank(rank)
                .createdAt(now)
                .build();
    }
}
