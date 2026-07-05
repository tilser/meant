package com.meant.api.module.review.service;

import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.review.properties.OkendoReviewProperties;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
    private static final int MAX_REVIEW_REQUEST_LIMIT = 25;
    private static final int MAX_REVIEW_RESULT_WINDOW = 100;

    private final RestClient restClient;
    private final OkendoReviewResponseMapper responseMapper;
    private final String reviewsBaseUrl;
    private final URI reviewsBaseUri;

    @Autowired
    public OkendoReviewClient(
            RestClient.Builder restClientBuilder,
            OkendoReviewProperties properties,
            OkendoReviewResponseMapper responseMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.responseMapper = responseMapper;
        this.reviewsBaseUrl = trimTrailingSlash(properties.reviewsBaseUrl());
        this.reviewsBaseUri = paginationUri(reviewsBaseUrl);
    }

    OkendoReviewClient(
            RestClient restClient,
            OkendoReviewProperties properties,
            OkendoReviewResponseMapper responseMapper
    ) {
        this.restClient = restClient;
        this.responseMapper = responseMapper;
        this.reviewsBaseUrl = trimTrailingSlash(properties.reviewsBaseUrl());
        this.reviewsBaseUri = paginationUri(reviewsBaseUrl);
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
        int targetReviewCount = targetReviewCount(safeLimit, safeOffset);
        try {
            List<String> reviewsResponses = reviewsResponses(providerKey, okendoProductId, targetReviewCount);
            String aggregateResponse = aggregateResponse(providerKey, okendoProductId);
            return responseMapper.fromJson(
                    merchantId,
                    productId,
                    reviewsResponses,
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

    private List<String> reviewsResponses(String providerKey, String okendoProductId, int targetReviewCount) {
        List<String> responses = new ArrayList<>();
        URI nextUri = reviewsUri(
                providerKey,
                okendoProductId,
                Math.min(MAX_REVIEW_REQUEST_LIMIT, targetReviewCount)
        );
        int fetchedReviews = 0;
        while (nextUri != null && fetchedReviews < targetReviewCount) {
            String response = restClient.get()
                    .uri(nextUri)
                    .retrieve()
                    .body(String.class);
            responses.add(response);

            OkendoReviewResponseMapper.ReviewsPage page = responseMapper.reviewsPage(response);
            int pageReviewCount = page.reviewCount();
            if (pageReviewCount <= 0) {
                break;
            }
            fetchedReviews += pageReviewCount;
            nextUri = nextReviewsUri(page.nextUrl());
        }
        return responses;
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
        return UriComponentsBuilder.fromUriString(reviewsBaseUrl)
                .pathSegment("stores", providerKey, "products", okendoProductId, "reviews")
                .queryParam("limit", limit)
                .queryParam("orderBy", SORT_MOST_RECENT)
                .build()
                .toUri();
    }

    private URI aggregateUri(String providerKey, String okendoProductId) {
        return UriComponentsBuilder.fromUriString(reviewsBaseUrl)
                .pathSegment("stores", providerKey, "products", okendoProductId, "review_aggregate")
                .build()
                .toUri();
    }

    private URI nextReviewsUri(String nextUrl) {
        if (!hasText(nextUrl)) {
            return null;
        }
        String trimmedNextUrl = nextUrl.trim();
        URI candidate = paginationUri(trimmedNextUrl);
        if (candidate.isAbsolute()) {
            if (isSameBaseUri(candidate)) {
                return candidate;
            }
            throw new ReviewException("Okendo Reviews API returned an unexpected pagination URL");
        }
        String separator = trimmedNextUrl.startsWith("/") ? "" : "/";
        return paginationUri(reviewsBaseUrl + separator + trimmedNextUrl);
    }

    private URI paginationUri(String nextUrl) {
        try {
            return URI.create(nextUrl);
        } catch (IllegalArgumentException exception) {
            throw new ReviewException("Okendo Reviews API returned an invalid pagination URL", exception);
        }
    }

    private boolean isSameBaseUri(URI candidate) {
        return equalsIgnoreCase(candidate.getScheme(), reviewsBaseUri.getScheme())
                && equalsIgnoreCase(candidate.getHost(), reviewsBaseUri.getHost())
                && effectivePort(candidate) == effectivePort(reviewsBaseUri)
                && isSameBasePath(candidate.getPath(), reviewsBaseUri.getPath());
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return -1;
    }

    private boolean equalsIgnoreCase(String first, String second) {
        if (first == null) {
            return second == null;
        }
        return first.equalsIgnoreCase(second);
    }

    private boolean isSameBasePath(String candidatePath, String basePath) {
        if (!hasText(basePath) || "/".equals(basePath)) {
            return true;
        }
        return candidatePath != null && (candidatePath.equals(basePath) || candidatePath.startsWith(basePath + "/"));
    }

    private String okendoProductId(String productId) {
        if (productId.startsWith(PRODUCT_ID_PREFIX)) {
            return productId;
        }
        return PRODUCT_ID_PREFIX + productId;
    }

    private int safeLimit(int limit) {
        return Math.min(Math.max(MIN_REVIEW_REQUEST_LIMIT, limit), MAX_REVIEW_RESULT_WINDOW);
    }

    private int safeOffset(int offset) {
        return Math.max(0, offset);
    }

    private int targetReviewCount(int limit, int offset) {
        long requestedReviewCount = (long) limit + offset;
        return requestedReviewCount > MAX_REVIEW_RESULT_WINDOW ? MAX_REVIEW_RESULT_WINDOW : (int) requestedReviewCount;
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
