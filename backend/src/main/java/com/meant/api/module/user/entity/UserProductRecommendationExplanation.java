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
        name = "user_product_recommendation_explanations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_product_recommendation_explanations_cache_key",
                        columnNames = {
                                "user_id",
                                "normalized_query",
                                "profile_hash",
                                "product_key",
                                "product_hash",
                                "model",
                                "prompt_version"
                        }
                )
        }
)
public class UserProductRecommendationExplanation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String normalizedQuery;

    @Column(nullable = false)
    private String profileHash;

    @Column(nullable = false)
    private String productKey;

    @Column(nullable = false)
    private String productHash;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String whyMeantForYou;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserProductRecommendationExplanation create(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String productKey,
            String productHash,
            String model,
            String promptVersion,
            String whyMeantForYou,
            Instant now
    ) {
        return UserProductRecommendationExplanation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .normalizedQuery(normalizedQuery)
                .profileHash(profileHash)
                .productKey(productKey)
                .productHash(productHash)
                .model(model)
                .promptVersion(promptVersion)
                .whyMeantForYou(whyMeantForYou)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
