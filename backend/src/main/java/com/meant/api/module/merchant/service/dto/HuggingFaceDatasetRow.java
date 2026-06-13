package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HuggingFaceDatasetRow(
        @JsonProperty("row_idx") int rowIdx,
        UcpMerchantDatasetRow row,
        @JsonProperty("truncated_cells") List<String> truncatedCells
) {
}
