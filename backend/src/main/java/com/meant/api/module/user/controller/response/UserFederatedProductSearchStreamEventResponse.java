package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response.CanonicalProductResponse;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response.DiscoverySourceIdentityResponse;
import com.meant.api.module.user.service.dto.UserFederatedProductSearchStreamEvent;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryEventType;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailure;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailureKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

@Schema(description = "Provider-neutral federated discovery event with source provenance")
public record UserFederatedProductSearchStreamEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CatalogDiscoveryEventType type,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        DiscoverySourceIdentityResponse source,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CanonicalProductResponse candidate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CatalogSourceFailureResponse failure,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        CatalogDiscoveryTerminalStatus terminalStatus
) {

    public static UserFederatedProductSearchStreamEventResponse from(
            UserFederatedProductSearchStreamEvent event
    ) {
        return new UserFederatedProductSearchStreamEventResponse(
                event.type(),
                DiscoverySourceIdentityResponse.from(event.source()),
                CanonicalProductResponse.from(event.candidate()),
                CatalogSourceFailureResponse.from(event.failure()),
                event.terminalStatus()
        );
    }

    public static UserFederatedProductSearchStreamEventResponse error() {
        return new UserFederatedProductSearchStreamEventResponse(
                CatalogDiscoveryEventType.ERROR,
                null,
                null,
                null,
                CatalogDiscoveryTerminalStatus.FAILED
        );
    }

    public record CatalogSourceFailureResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            CatalogSourceFailureKind kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String message,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Duration retryAfter,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer upstreamStatus
    ) {

        static CatalogSourceFailureResponse from(CatalogSourceFailure failure) {
            return failure == null ? null : new CatalogSourceFailureResponse(
                    failure.kind(),
                    failure.message(),
                    failure.retryAfter(),
                    failure.upstreamStatus()
            );
        }
    }
}
