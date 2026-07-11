package com.meant.api.module.review.service;

import com.meant.api.module.review.service.port.ReviewProductIdNormalizationStrategy;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewProductIdNormalizer {

    private final List<ReviewProductIdNormalizationStrategy> strategies;

    public ReviewProductIdNormalizer(List<ReviewProductIdNormalizationStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(ReviewProductIdNormalizationStrategy::order)
                        .thenComparing(strategy -> strategy.getClass().getName()))
                .toList();
    }

    public String normalize(String productId) {
        if (productId == null) {
            return null;
        }
        String trimmed = productId.trim();
        for (ReviewProductIdNormalizationStrategy strategy : strategies) {
            if (strategy.supports(trimmed)) {
                return strategy.normalize(trimmed);
            }
        }
        return trimTrailingSlashes(trimmed);
    }

    private String trimTrailingSlashes(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
