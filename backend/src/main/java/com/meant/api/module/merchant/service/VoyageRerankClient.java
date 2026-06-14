package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEmbeddingException;
import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.service.dto.VoyageRerankData;
import com.meant.api.module.merchant.service.dto.VoyageRerankRequest;
import com.meant.api.module.merchant.service.dto.VoyageRerankResponse;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class VoyageRerankClient {

    private final RestClient.Builder restClientBuilder;
    private final MerchantEmbeddingProperties merchantEmbeddingProperties;

    public List<VoyageRerankResult> rerank(String query, List<String> documents) {
        if (merchantEmbeddingProperties.apiKey().isBlank()) {
            throw new MerchantEmbeddingException("Voyage API key is not configured");
        }
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        VoyageRerankRequest request = new VoyageRerankRequest(
                query,
                documents,
                merchantEmbeddingProperties.rerankModel(),
                documents.size(),
                false,
                true
        );

        try {
            VoyageRerankResponse response = restClientBuilder.clone()
                    .baseUrl(merchantEmbeddingProperties.voyageBaseUrl())
                    .build()
                    .post()
                    .uri("/v1/rerank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantEmbeddingProperties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(VoyageRerankResponse.class);
            return rerankResults(response, documents.size());
        } catch (RestClientException exception) {
            throw new MerchantEmbeddingException("Failed to fetch Voyage rerank results", exception);
        }
    }

    private List<VoyageRerankResult> rerankResults(VoyageRerankResponse response, int expectedCount) {
        List<VoyageRerankData> results = response == null ? null : response.rerankResults();
        if (results == null || results.size() != expectedCount) {
            throw new MerchantEmbeddingException("Voyage rerank response did not match request size");
        }

        Set<Integer> indexes = new HashSet<>();
        return results.stream()
                .map(result -> toRerankResult(result, expectedCount))
                .peek(result -> validateUniqueIndex(indexes, result))
                .toList();
    }

    private VoyageRerankResult toRerankResult(VoyageRerankData result, int expectedCount) {
        if (result == null || result.relevanceScore() == null) {
            throw new MerchantEmbeddingException("Voyage rerank response included an invalid result");
        }
        if (result.index() < 0 || result.index() >= expectedCount) {
            throw new MerchantEmbeddingException("Voyage rerank response included an invalid document index");
        }
        return new VoyageRerankResult(result.index(), result.relevanceScore());
    }

    private void validateUniqueIndex(Set<Integer> indexes, VoyageRerankResult result) {
        if (!indexes.add(result.index())) {
            throw new MerchantEmbeddingException("Voyage rerank response included a duplicate document index");
        }
    }
}
