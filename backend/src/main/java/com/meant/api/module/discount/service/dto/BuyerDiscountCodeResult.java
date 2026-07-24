package com.meant.api.module.discount.service.dto;

import java.time.Instant;

/** Buyer-facing discount code fields after transport-coordinate sanitization. */
public record BuyerDiscountCodeResult(
        String code,
        String title,
        String description,
        String sourceUrl,
        Double confidence,
        String restrictions,
        Instant validUntil,
        Instant expiresAt,
        String validationMessage
) {
}
