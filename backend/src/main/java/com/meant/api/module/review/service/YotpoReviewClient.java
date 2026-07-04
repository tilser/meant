package com.meant.api.module.review.service;

import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.review.properties.YotpoReviewProperties;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class YotpoReviewClient {

    private final RestClient restClient;
    private final YotpoReviewProperties properties;
    private final YotpoReviewResponseMapper responseMapper;

    @Autowired
    public YotpoReviewClient(
            RestClient.Builder restClientBuilder,
            YotpoReviewProperties properties,
            YotpoReviewResponseMapper responseMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.responseMapper = responseMapper;
    }

    YotpoReviewClient(
            RestClient restClient,
            YotpoReviewProperties properties,
            YotpoReviewResponseMapper responseMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.responseMapper = responseMapper;
    }

    public ProductReviewsResult fetchReviews(
            UUID merchantId,
            String productId,
            String providerKey,
            int limit,
            int offset
    ) {
        URI uri = reviewsUri(productId, providerKey, limit, offset);
        try {
            String response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            return responseMapper.fromJson(merchantId, productId, response, limit, offset);
        } catch (RestClientResponseException exception) {
            throw new ReviewException(
                    "Yotpo Reviews API returned status " + exception.getStatusCode(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new ReviewException("Yotpo Reviews API request failed", exception);
        }
    }

    private URI reviewsUri(String productId, String providerKey, int limit, int offset) {
        return UriComponentsBuilder.fromUriString(trimTrailingSlash(properties.reviewsBaseUrl()))
                .pathSegment(providerKey, "products", productId, "reviews.json")
                .queryParam("per_page", limit)
                .queryParam("page", page(limit, offset))
                .build()
                .toUri();
    }

    private int page(int limit, int offset) {
        return offset / limit + 1;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
