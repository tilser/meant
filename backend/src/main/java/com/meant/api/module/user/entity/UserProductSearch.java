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
        name = "user_product_searches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_product_searches_cache_key",
                        columnNames = {"user_id", "normalized_query", "profile_hash", "search_version"}
                )
        }
)
public class UserProductSearch {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String query;

    @Column(nullable = false)
    private String normalizedQuery;

    @Column(nullable = false)
    private String profileHash;

    @Column(nullable = false)
    private String searchVersion;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private String retentionPolicyFingerprint;

    @Column(nullable = false)
    private boolean hasMoreProducts;

    public static UserProductSearch create(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            Instant now,
            Instant expiresAt,
            String retentionPolicyFingerprint,
            boolean hasMoreProducts
    ) {
        return UserProductSearch.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .query(query)
                .normalizedQuery(normalizedQuery)
                .profileHash(profileHash)
                .searchVersion(searchVersion)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(expiresAt)
                .retentionPolicyFingerprint(retentionPolicyFingerprint)
                .hasMoreProducts(hasMoreProducts)
                .build();
    }

    /** Compatibility constructor for historical rows; the null policy marker makes them unservable. */
    public static UserProductSearch create(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            Instant now,
            Instant expiresAt,
            boolean hasMoreProducts
    ) {
        return create(userId, query, normalizedQuery, profileHash, searchVersion, now, expiresAt, null, hasMoreProducts);
    }

    public void refresh(
            String query,
            Instant now,
            Instant expiresAt,
            String retentionPolicyFingerprint,
            boolean hasMoreProducts
    ) {
        this.query = query;
        this.updatedAt = now;
        this.expiresAt = expiresAt;
        this.retentionPolicyFingerprint = retentionPolicyFingerprint;
        this.hasMoreProducts = hasMoreProducts;
    }
}
