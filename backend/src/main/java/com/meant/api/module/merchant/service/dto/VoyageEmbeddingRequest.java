package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VoyageEmbeddingRequest(
        List<String> input,
        String model,
        @JsonProperty("input_type")
        String inputType,
        Boolean truncation,
        @JsonProperty("output_dimension")
        Integer outputDimension,
        @JsonProperty("output_dtype")
        String outputDtype
) {
}
