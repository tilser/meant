package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserDiscoverProductResultSetResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Current products rehydrated from one identifiers-only Discover history reference.")
public record UserDiscoverProductResultSetResponse(
        @Schema(
                description = "Server-issued identifiers-only result-set handle",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID resultSetId,

        @Schema(
                description = "Currently rehydrated products in their original result order",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<UserGroupedProductSearchV1Response.CanonicalProductResponse> products,

        @Schema(
                description = "Referenced products that could not be authoritatively rehydrated",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int unavailableCount
) {
    public static UserDiscoverProductResultSetResponse from(UserDiscoverProductResultSetResult result) {
        return new UserDiscoverProductResultSetResponse(
                result.resultSetId(),
                result.products().stream()
                        .map(product -> UserGroupedProductSearchV1Response.CanonicalProductResponse.from(
                                product.product(),
                                null,
                                product.personalization(),
                                java.util.Map.of(),
                                product.commercialStates()
                        ))
                        .toList(),
                result.unavailableCount()
        );
    }
}
