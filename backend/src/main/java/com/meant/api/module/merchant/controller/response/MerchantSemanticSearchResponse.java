package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record MerchantSemanticSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String domain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String retrievalContent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double semanticScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double rerankScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int rank
) {

    public static MerchantSemanticSearchResponse from(MerchantSemanticSearchResult result) {
        String domain = MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(result.domain());
        String name = buyerText(result.name(), result);
        String retrievalContent = buyerText(result.retrievalContent(), result);
        return new MerchantSemanticSearchResponse(
                result.merchantId(),
                domain == null ? "Merchant" : domain,
                name,
                retrievalContent,
                result.semanticScore(),
                result.rerankScore(),
                result.rank()
        );
    }

    private static String buyerText(
            String value,
            MerchantSemanticSearchResult result
    ) {
        String sanitized = MerchantBuyerTextSanitizer.sanitize(
                value,
                result.domain(),
                null,
                result.advertisedMcpEndpoint()
        );
        return MerchantBuyerTextSanitizer.sanitize(
                sanitized,
                result.domain(),
                null,
                result.profileMcpEndpoint()
        );
    }
}
