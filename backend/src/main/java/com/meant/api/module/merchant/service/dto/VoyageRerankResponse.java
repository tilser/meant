package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VoyageRerankResponse(
        List<VoyageRerankData> data,
        List<VoyageRerankData> results,
        String model
) {

    public List<VoyageRerankData> rerankResults() {
        if (data != null) {
            return data;
        }
        return results;
    }
}
