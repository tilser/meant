package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VoyageEmbeddingResponse(
        List<VoyageEmbeddingData> data,
        String model
) {
}
