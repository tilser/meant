package com.meant.api.module.review.service.port;

public interface ReviewProductIdNormalizationStrategy {

    boolean supports(String productId);

    String normalize(String productId);

    default int order() {
        return 0;
    }
}
