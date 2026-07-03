package com.meant.api.module.discount.controller.response;

import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import java.time.Instant;

public record DiscountCodeResponse(
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

    public static DiscountCodeResponse from(DiscountCodeResult result) {
        return new DiscountCodeResponse(
                result.code(),
                result.title(),
                result.description(),
                result.sourceUrl(),
                result.confidence(),
                result.restrictions(),
                result.validUntil(),
                result.expiresAt(),
                result.validationMessage()
        );
    }
}
