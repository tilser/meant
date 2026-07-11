package com.meant.api.module.user.service.dto;

import java.time.Instant;
import java.util.List;

public record UserSavedProductResult(
        String id,
        String productHash,
        String name,
        String brand,
        String category,
        String tone,
        String imageUrl,
        String productUrl,
        boolean remote,
        int match,
        double priceFrom,
        int merchants,
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

    public record Review(
            double score,
            int count,
            String insight
    ) {
    }

    public record Offer(
            String merchant,
            double price,
            String delivery,
            String merchantId,
            String merchantDomain,
            String productVariantId,
            String variantTitle,
            Boolean available
    ) {
    }
}
