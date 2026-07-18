package com.meant.api.module.agent.service.dto;

public record GetProductReviewsAgentToolInput(
        String canonicalProductKey,
        String selectedOfferKey,
        Integer limit,
        Integer offset
) {
}
