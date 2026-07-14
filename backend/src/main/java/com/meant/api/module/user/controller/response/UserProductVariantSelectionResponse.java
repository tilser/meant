package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current product detail and an exact offer only when the requested options resolve uniquely")
public record UserProductVariantSelectionResponse(
        @Schema(
                description = "Current transient provider detail for the effective selection",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UserSavedProductDetailsResponse details,
        @Schema(
                description = "Server-issued exact offer key; absent for partial, relaxed, or ambiguous selections",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String selectedOfferKey,
        @Schema(
                description = "Exact canonical offer; absent for partial, relaxed, or ambiguous selections",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        UserGroupedProductSearchV1Response.OfferResponse selectedOffer,
        @Schema(
                description = "True when the exact offer is currently eligible for the cart",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean cartable
) {
    public static UserProductVariantSelectionResponse from(UserProductVariantSelectionResult result) {
        return new UserProductVariantSelectionResponse(
                UserSavedProductDetailsResponse.from(result.details()),
                result.selectedOffer() == null ? null : result.selectedOffer().key(),
                result.selectedOffer() == null
                        ? null
                        : UserGroupedProductSearchV1Response.OfferResponse.from(result.selectedOffer()),
                result.cartable()
        );
    }
}
