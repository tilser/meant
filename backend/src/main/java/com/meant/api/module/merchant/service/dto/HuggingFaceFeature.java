package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HuggingFaceFeature(
        @JsonProperty("feature_idx") int featureIdx,
        String name,
        HuggingFaceFeatureType type
) {
}
