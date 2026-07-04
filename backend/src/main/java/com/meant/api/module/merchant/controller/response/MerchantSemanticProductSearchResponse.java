package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MerchantSemanticProductSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MerchantCatalogSearchAttemptResponse> merchants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MerchantSemanticProductResponse> products
) {

    public static MerchantSemanticProductSearchResponse from(MerchantSemanticProductSearchResult result) {
        return new MerchantSemanticProductSearchResponse(
                result.merchants().stream()
                        .map(MerchantCatalogSearchAttemptResponse::from)
                        .toList(),
                result.products().stream()
                        .map(MerchantSemanticProductResponse::from)
                        .toList()
        );
    }
}
