package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantListItemResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record MerchantListItemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String domain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String advertisedMcpEndpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String profileMcpEndpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean supportsIdentityLinking
) {

    public static MerchantListItemResponse from(MerchantListItemResult result) {
        return new MerchantListItemResponse(
                result.id(),
                result.domain(),
                result.name(),
                result.description(),
                result.advertisedMcpEndpoint(),
                result.profileMcpEndpoint(),
                result.supportsIdentityLinking()
        );
    }
}
