package com.meant.api.module.user.service.dto;

import java.time.Instant;
import java.util.List;

/** Ephemeral saved-product view: only fresh rehydration may populate provider facts. */
public record UserSavedProductResult(
        String id,
        String productHash,
        String name,
        String brand,
        String category,
        String tone,
        String imageUrl,
        String productUrl,
        Boolean remote,
        Integer match,
        Double priceFrom,
        Integer merchants,
        List<String> satisfies,
        List<String> misses,
        String note,
        List<String> pros,
        List<String> cons,
        Review review,
        List<Offer> offers,
        String needs,
        List<String> provides,
        boolean commercialFactsAuthoritative,
        Instant createdAt,
        Instant updatedAt
) {
    public UserSavedProductResult {
        satisfies = satisfies == null ? List.of() : List.copyOf(satisfies);
        misses = misses == null ? List.of() : List.copyOf(misses);
        pros = pros == null ? List.of() : List.copyOf(pros);
        cons = cons == null ? List.of() : List.copyOf(cons);
        offers = offers == null ? List.of() : List.copyOf(offers);
        provides = provides == null ? List.of() : List.copyOf(provides);
    }

    public record Review(Double score, Integer count, String insight) {
    }

    public record Offer(
            String merchant,
            Double price,
            String delivery,
            String merchantId,
            String merchantDomain,
            String productVariantId,
            String variantTitle,
            Boolean available
    ) {
    }
}
