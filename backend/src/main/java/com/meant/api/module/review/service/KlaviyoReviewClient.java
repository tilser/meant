package com.meant.api.module.review.service;

import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.review.properties.KlaviyoReviewProperties;
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
public class KlaviyoReviewClient {

    private static final int SORT_MOST_RECENT = 3;

    private final RestClient restClient;
    private final KlaviyoReviewProperties properties;
    private final KlaviyoReviewResponseMapper responseMapper;

    @Autowired
    public KlaviyoReviewClient(
            RestClient.Builder restClientBuilder,
            KlaviyoReviewProperties properties,
            KlaviyoReviewResponseMapper responseMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.responseMapper = responseMapper;
    }

    KlaviyoReviewClient(
            RestClient restClient,
            KlaviyoReviewProperties properties,
            KlaviyoReviewResponseMapper responseMapper
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
            return responseMapper.fromJson(merchantId, productId, response);
        } catch (RestClientResponseException exception) {
            throw new ReviewException(
                    "Klaviyo Reviews API returned status " + exception.getStatusCode(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new ReviewException("Klaviyo Reviews API request failed", exception);
        }
    }

    private URI reviewsUri(String productId, String providerKey, int limit, int offset) {
        return UriComponentsBuilder.fromUriString(trimTrailingSlash(properties.reviewsBaseUrl()))
                .pathSegment("client_reviews", productId)
                .path("/")
                .queryParam("product_id", productId)
                .queryParam("company_id", providerKey)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("sort", SORT_MOST_RECENT)
                .queryParam("filter", "")
                .queryParam("type", "reviews")
                .queryParam("media", "false")
                .build()
                .toUri();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
