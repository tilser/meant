package com.meant.api.module.user.entity;

import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
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
        name = "user_product_search_query_intents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_product_search_query_intents_cache_key",
                        columnNames = {"normalized_original_query", "model", "prompt_version"}
                )
        }
)
public class UserProductSearchQueryIntent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String normalizedOriginalQuery;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String searchQuery;

    @Column(nullable = false)
    private String normalizedSearchQuery;

    @Column(nullable = false)
    private String intentCacheKey;

    @Column(nullable = false)
    private String constraintsText;

    @Column(nullable = false)
    private String preferenceHintsText;

    @Column(nullable = false)
    private String confidence;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserProductSearchQueryIntent from(
            String normalizedOriginalQuery,
            String model,
            String promptVersion,
            UserProductSearchQueryIntentResult result,
            Instant now
    ) {
        return UserProductSearchQueryIntent.builder()
                .id(UUID.randomUUID())
                .normalizedOriginalQuery(normalizedOriginalQuery)
                .model(model)
                .promptVersion(promptVersion)
                .searchQuery(result.searchQuery())
                .normalizedSearchQuery(result.normalizedSearchQuery())
                .intentCacheKey(result.intentCacheKey())
                .constraintsText(String.join("\n", result.constraints()))
                .preferenceHintsText(String.join("\n", result.preferenceHints()))
                .confidence(result.confidence())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public UserProductSearchQueryIntentResult toResult(String originalQuery) {
        return new UserProductSearchQueryIntentResult(
                originalQuery,
                normalizedOriginalQuery,
                searchQuery,
                normalizedSearchQuery,
                intentCacheKey,
                lines(constraintsText),
                lines(preferenceHintsText),
                confidence,
                "llm-cache"
        );
    }

    private java.util.List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
