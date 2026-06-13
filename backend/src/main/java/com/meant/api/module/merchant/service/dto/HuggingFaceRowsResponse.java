package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HuggingFaceRowsResponse(
        List<HuggingFaceFeature> features,
        List<HuggingFaceDatasetRow> rows,
        @JsonProperty("num_rows_total") int numRowsTotal,
        @JsonProperty("num_rows_per_page") int numRowsPerPage,
        boolean partial
) {
}
