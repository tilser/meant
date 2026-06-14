package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEmbeddingException;
import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.service.dto.VoyageEmbeddingData;
import com.meant.api.module.merchant.service.dto.VoyageEmbeddingRequest;
import com.meant.api.module.merchant.service.dto.VoyageEmbeddingResponse;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class VoyageEmbeddingClient {

    private static final String DOCUMENT_INPUT_TYPE = "document";
    private static final String QUERY_INPUT_TYPE = "query";
    private static final String FLOAT_OUTPUT_DTYPE = "float";

    private final RestClient.Builder restClientBuilder;
    private final MerchantEmbeddingProperties merchantEmbeddingProperties;

    public List<List<Double>> embedDocuments(List<String> texts) {
        return embed(texts, DOCUMENT_INPUT_TYPE);
    }

    public List<Double> embedQuery(String query) {
        return embed(List.of(query), QUERY_INPUT_TYPE).getFirst();
    }

    private List<List<Double>> embed(List<String> texts, String inputType) {
        if (merchantEmbeddingProperties.apiKey().isBlank()) {
            throw new MerchantEmbeddingException("Voyage API key is not configured");
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        VoyageEmbeddingRequest request = new VoyageEmbeddingRequest(
                texts,
                merchantEmbeddingProperties.model(),
                inputType,
                true,
                merchantEmbeddingProperties.dimension(),
                FLOAT_OUTPUT_DTYPE
        );

        try {
            VoyageEmbeddingResponse response = restClientBuilder.clone()
                    .baseUrl(merchantEmbeddingProperties.voyageBaseUrl())
                    .build()
                    .post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantEmbeddingProperties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(VoyageEmbeddingResponse.class);
            return embeddings(response, texts.size());
        } catch (RestClientException exception) {
            throw new MerchantEmbeddingException("Failed to fetch Voyage embeddings", exception);
        }
    }

    private List<List<Double>> embeddings(VoyageEmbeddingResponse response, int expectedCount) {
        if (response == null || response.data() == null || response.data().size() != expectedCount) {
            throw new MerchantEmbeddingException("Voyage embedding response did not match request size");
        }

        return response.data().stream()
                .sorted(Comparator.comparingInt(VoyageEmbeddingData::index))
                .map(VoyageEmbeddingData::embedding)
                .peek(this::validateEmbedding)
                .toList();
    }

    private void validateEmbedding(List<Double> embedding) {
        if (embedding == null || embedding.size() != merchantEmbeddingProperties.dimension()) {
            throw new MerchantEmbeddingException("Voyage embedding response did not match configured dimension");
        }
    }
}
