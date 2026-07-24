package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
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
        boolean supportsIdentityLinking
) {

    public static MerchantListItemResponse from(MerchantListItemResult result) {
        String domain = MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(result.domain());
        if (domain == null) {
            domain = "Merchant";
        }
        return new MerchantListItemResponse(
                result.id(),
                domain,
                MerchantBuyerTextSanitizer.sanitize(
                        result.name(),
                        result.domain(),
                        null,
                        result.advertisedMcpEndpoint()
                ),
                MerchantBuyerTextSanitizer.sanitize(
                        MerchantBuyerTextSanitizer.sanitize(
                                result.description(),
                                result.domain(),
                                null,
                                result.advertisedMcpEndpoint()
                        ),
                        result.domain(),
                        null,
                        result.profileMcpEndpoint()
                ),
                result.supportsIdentityLinking()
        );
    }
}
