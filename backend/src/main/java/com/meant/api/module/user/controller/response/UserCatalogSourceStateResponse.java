package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Typed source-scoped completion, degradation, and truncation state")
public record UserCatalogSourceStateResponse(
        @Schema(description = "Stable provider and discovery-path identity", requiredMode = Schema.RequiredMode.REQUIRED)
        UserGroupedProductSearchV1Response.DiscoverySourceIdentityResponse source,
        @Schema(description = "Catalog operation performed by the source", requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogSourceOperation operation,
        @Schema(description = "Whether this source failed without failing successful sources", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean degraded,
        @Schema(description = "Whether this source reported more candidates than were materialized", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean truncated,
        @Schema(description = "Safe typed failure classification", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CatalogSourceFailureKind failureKind,
        @Schema(description = "Typed offer rehydration failure for this source", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CatalogRehydrationFailureKind rehydrationFailureKind,
        @Schema(description = "Retry delay in milliseconds when supplied by the source", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long retryAfterMilliseconds
) {
    public static UserCatalogSourceStateResponse from(UserCatalogSourceState state) {
        return new UserCatalogSourceStateResponse(
                UserGroupedProductSearchV1Response.DiscoverySourceIdentityResponse.from(state.source()),
                state.operation(),
                state.degraded(),
                state.truncated(),
                state.failureKind(),
                state.rehydrationFailureKind(),
                state.retryAfter() == null ? null : state.retryAfter().toMillis()
        );
    }
}
