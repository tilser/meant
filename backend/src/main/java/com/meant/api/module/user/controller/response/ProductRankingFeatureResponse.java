package com.meant.api.module.user.controller.response;

import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One named product-ranking feature")
public record ProductRankingFeatureResponse(
        @Schema(description = "Controlled product feature name", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductRankingExplanation.Name name,
        @Schema(description = "Whether this feature was known", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductRankingExplanation.Availability availability,
        @Schema(description = "Normalized feature value in basis points", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer valueBasisPoints,
        @Schema(description = "Versioned policy weight", requiredMode = Schema.RequiredMode.REQUIRED)
        int weight,
        @Schema(description = "Provider-adapter calibration or model versions used", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> evidenceVersions
) {
    static ProductRankingFeatureResponse from(ProductRankingExplanation.Feature feature) {
        return new ProductRankingFeatureResponse(
                feature.name(), feature.availability(), feature.valueBasisPoints(), feature.weight(), feature.evidenceVersions());
    }
}
