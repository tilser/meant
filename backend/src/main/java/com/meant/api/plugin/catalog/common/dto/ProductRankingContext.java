package com.meant.api.plugin.catalog.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Provider-neutral, request-scoped inputs for canonical-product ranking. */
public record ProductRankingContext(
        String normalizedIntent,
        CatalogSearchContext searchContext,
        CatalogSearchFilters hardFilters,
        List<PreferenceSignal> preferences,
        Map<String, InventoryRelationship> inventoryRelationships,
        Instant rankedAt,
        int diversityWindow
) {

    public ProductRankingContext {
        normalizedIntent = normalizedIntent == null || normalizedIntent.isBlank()
                ? null
                : normalizedIntent.trim();
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        inventoryRelationships = inventoryRelationships == null ? Map.of() : Map.copyOf(inventoryRelationships);
        if (rankedAt == null) {
            throw new IllegalArgumentException("Ranking time is required");
        }
        if (diversityWindow < 1 || diversityWindow > 100) {
            throw new IllegalArgumentException("Diversity window must be between 1 and 100");
        }
    }

    public record PreferenceSignal(Type type, String normalizedValue, int weightBasisPoints) {

        public PreferenceSignal {
            if (type == null || normalizedValue == null || normalizedValue.isBlank()) {
                throw new IllegalArgumentException("Preference type and normalized value are required");
            }
            normalizedValue = normalizedValue.trim();
            if (weightBasisPoints < -10_000 || weightBasisPoints > 10_000) {
                throw new IllegalArgumentException("Preference weight must be between -10000 and 10000");
            }
        }

        public enum Type {
            FILTER,
            BRAND,
            CATEGORY,
            MATERIAL,
            CERTIFICATION,
            QUERY
        }
    }

    public enum InventoryRelationship {
        NONE,
        DUPLICATE,
        COMPLEMENT,
        RESTOCK
    }
}
