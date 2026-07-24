package com.meant.api.module.discount.controller.response;

import com.meant.api.module.discount.service.DiscountCodeBuyerProjection;
import com.meant.api.module.discount.service.dto.BuyerDiscountCodeResult;
import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "DiscountCodeResponse", description = "Validated merchant discount code.")
public record DiscountCodeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Discount code submitted to the merchant.", example = "SAVE10")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Human-readable code title.", example = "10% off")
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Code description from discovery source.", example = "Save 10% on your order.")
        String description,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Buyer-safe source URL where the code was discovered.", example = "https://merchant.example/codes")
        String sourceUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Discovery confidence from 0 to 1.", example = "0.9")
        Double confidence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Known restrictions or eligibility notes.", example = "Valid for new customers only.")
        String restrictions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Best-effort code validity deadline parsed from source text.")
        Instant validUntil,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Cache expiration timestamp for this validation result.")
        Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Merchant validation result message.", example = "Discount code accepted by merchant.")
        String validationMessage
) {

    public static DiscountCodeResponse from(DiscountCodeResult result, String merchantOrigin) {
        return from(DiscountCodeBuyerProjection.from(result, merchantOrigin));
    }

    public static DiscountCodeResponse from(BuyerDiscountCodeResult result) {
        if (result == null) {
            return null;
        }
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
