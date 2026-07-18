package com.meant.api.module.agent.service.dto;

public record FindDiscountCodesAgentToolInput(
        String canonicalProductKey,
        String selectedOfferKey,
        Integer quantity
) {
}
