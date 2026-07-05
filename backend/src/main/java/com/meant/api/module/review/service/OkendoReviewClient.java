package com.meant.api.module.review.service;

import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.review.properties.OkendoReviewProperties;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OkendoReviewClient {

    private static final String PRODUCT_ID_PREFIX = "shopify-";
    private static final String SORT_MOST_RECENT = "date desc";
    private static final int MIN_REVIEW_REQUEST_LIMIT = 1;
    private static final int MAX_REVIEW_REQUEST_LIMIT = 100;

    private final RestClient restClient;
    private final OkendoReviewProperties properties;
    private final OkendoReviewResponseMapper responseMapper;

    @Autowired
    public OkendoReviewClient(
            RestClient.Builder restClientBuilder,
            OkendoReviewProperties properties,
            OkendoReviewResponseMapper responseMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.responseMapper = responseMapper;
    }

    OkendoReviewClient(
            RestClient restClient,
            OkendoReviewProperties properties,
            OkendoReviewResponseMapper responseMapper
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
        int safeLimit = safeLimit(limit);
        int safeOffset = safeOffset(offset);
        String okendoProductId = okendoProductId(productId);
        int requestLimit = requestLimit(safeLimit, safeOffset);
        try {
            String reviewsResponse = restClient.get()
                    .uri(reviewsUri(providerKey, okendoProductId, requestLimit))
                    .retrieve()
                    .body(String.class);
            String aggregateResponse = aggregateResponse(providerKey, okendoProductId);
            return responseMapper.fromJson(
                    merchantId,
                    productId,
                    reviewsResponse,
                    aggregateResponse,
                    safeLimit,
                    safeOffset
            );
        } catch (RestClientResponseException exception) {
            throw new ReviewException(
                    "Okendo Reviews API returned status " + exception.getStatusCode(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new ReviewException("Okendo Reviews API request failed", exception);
        }
    }

    private String aggregateResponse(String providerKey, String okendoProductId) {
        URI uri = aggregateUri(providerKey, okendoProductId);
        try {
            return restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return "{}";
            }
            throw exception;
        }
    }

    private URI reviewsUri(String providerKey, String okendoProductId, int limit) {
        return UriComponentsBuilder.fromUriString(trimTrailingSlash(properties.reviewsBaseUrl()))
                .pathSegment("stores", providerKey, "products", okendoProductId, "reviews")
                .queryParam("limit", limit)
                .queryParam("orderBy", SORT_MOST_RECENT)
                .build()
                .toUri();
    }

    private URI aggregateUri(String providerKey, String okendoProductId) {
        return UriComponentsBuilder.fromUriString(trimTrailingSlash(properties.reviewsBaseUrl()))
                .pathSegment("stores", providerKey, "products", okendoProductId, "review_aggregate")
                .build()
                .toUri();
    }

    private String okendoProductId(String productId) {
        if (productId.startsWith(PRODUCT_ID_PREFIX)) {
            return productId;
        }
        return PRODUCT_ID_PREFIX + productId;
    }

    private int safeLimit(int limit) {
        return Math.min(Math.max(MIN_REVIEW_REQUEST_LIMIT, limit), MAX_REVIEW_REQUEST_LIMIT);
    }

    private int safeOffset(int offset) {
        return Math.max(0, offset);
    }

    private int requestLimit(int limit, int offset) {
        long requestedLimit = (long) limit + offset;
        return requestedLimit > MAX_REVIEW_REQUEST_LIMIT ? MAX_REVIEW_REQUEST_LIMIT : (int) requestedLimit;
    }

    private String trimTrailingSlash(String value) {
        if (!hasText(value)) {
            throw new ReviewException("Okendo Reviews API base URL is not configured");
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
