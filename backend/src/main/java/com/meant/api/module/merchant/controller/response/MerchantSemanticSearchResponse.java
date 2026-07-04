package com.meant.api.module.merchant.controller.response;

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
        return new MerchantSemanticSearchResponse(
                result.merchantId(),
                result.domain(),
                result.name(),
                result.retrievalContent(),
                result.semanticScore(),
                result.rerankScore(),
                result.rank()
        );
    }
}
