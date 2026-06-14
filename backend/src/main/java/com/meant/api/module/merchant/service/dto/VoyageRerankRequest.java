package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VoyageRerankRequest(
        String query,
        List<String> documents,
        String model,
        @JsonProperty("top_k")
        Integer topK,
        @JsonProperty("return_documents")
        Boolean returnDocuments,
        Boolean truncation
) {
}
