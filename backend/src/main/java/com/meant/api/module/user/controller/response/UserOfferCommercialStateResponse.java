package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authority, freshness, and typed degradation for commercial offer facts")
public record UserOfferCommercialStateResponse(
        @Schema(description = "Whether facts are a discovery observation or current rehydration", requiredMode = Schema.RequiredMode.REQUIRED)
        UserOfferCommercialState.Authority authority,
        @Schema(description = "Detail rehydration status; absent on search observations", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CatalogRehydrationStatus rehydrationStatus,
        @Schema(description = "Typed offer-scoped rehydration failure", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CatalogRehydrationFailureKind degradation,
        @Schema(description = "Price observation freshness", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UserGroupedProductSearchV1Response.ResultFreshnessResponse priceFreshness,
        @Schema(description = "Availability observation freshness", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UserGroupedProductSearchV1Response.ResultFreshnessResponse availabilityFreshness,
        @Schema(description = "Delivery observation freshness", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UserGroupedProductSearchV1Response.ResultFreshnessResponse deliveryFreshness
) {
    public static UserOfferCommercialStateResponse from(UserOfferCommercialState state) {
        return new UserOfferCommercialStateResponse(
                state.authority(), state.rehydrationStatus(), state.degradation(),
                UserGroupedProductSearchV1Response.ResultFreshnessResponse.from(state.priceFreshness()),
                UserGroupedProductSearchV1Response.ResultFreshnessResponse.from(state.availabilityFreshness()),
                UserGroupedProductSearchV1Response.ResultFreshnessResponse.from(state.deliveryFreshness())
        );
    }
}
