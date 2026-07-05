package com.meant.api.module.review.service;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.review.service.dto.ProductReview;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OkendoReviewResponseMapper {

    private final ObjectMapper objectMapper;

    public ProductReviewsResult fromJson(
            UUID merchantId,
            String productId,
            String rawReviewsResponse,
            String rawAggregateResponse,
            int limit,
            int offset
    ) {
        try {
            JsonNode reviewsRoot = objectMapper.readTree(rawReviewsResponse == null ? "{}" : rawReviewsResponse);
            JsonNode aggregateRoot = objectMapper.readTree(rawAggregateResponse == null ? "{}" : rawAggregateResponse);
            JsonNode aggregate = aggregateRoot.path("reviewAggregate");
            List<ProductReview> visibleReviews = visibleReviews(reviews(reviewsRoot.path("reviews")), limit, offset);
            Integer reviewCount = reviewCount(aggregate, visibleReviews.size());

            return new ProductReviewsResult(
                    merchantId,
                    productId,
                    ReviewProviderType.OKENDO,
                    rating(aggregate),
                    reviewCount,
                    hasMore(reviewsRoot, reviewCount, visibleReviews.size(), offset),
                    visibleReviews,
                    false,
                    true,
                    null
            );
        } catch (JacksonException exception) {
            throw new ReviewException("Okendo Reviews response could not be parsed", exception);
        }
    }

    private List<ProductReview> visibleReviews(List<ProductReview> reviews, int limit, int offset) {
        if (offset >= reviews.size()) {
            return List.of();
        }
        return reviews.subList(offset, Math.min(reviews.size(), offset + limit));
    }

    private Integer reviewCount(JsonNode aggregate, int fallback) {
        Integer reviewCount = intValue(first(
                aggregate,
                "reviewCount",
                "ratingAndReviewCount",
                "ratingCount"
        ));
        return reviewCount == null ? fallback : reviewCount;
    }

    private Double rating(JsonNode aggregate) {
        Double explicitAverage = doubleValue(first(
                aggregate,
                "averageRating",
                "average_rating",
                "rating"
        ));
        if (explicitAverage != null) {
            return explicitAverage;
        }

        Integer count = intValue(first(aggregate, "ratingAndReviewCount", "reviewCount"));
        Integer total = intValue(first(aggregate, "ratingAndReviewValuesTotal", "reviewRatingValuesTotal"));
        if (count == null || count == 0 || total == null) {
            return null;
        }
        return (double) total / count;
    }

    private boolean hasMore(JsonNode reviewsRoot, int reviewCount, int visibleReviewCount, int offset) {
        if (hasText(text(reviewsRoot, "nextUrl"))) {
            return true;
        }
        return offset + visibleReviewCount < reviewCount;
    }

    private List<ProductReview> reviews(JsonNode reviewsNode) {
        if (reviewsNode == null || !reviewsNode.isArray()) {
            return List.of();
        }
        List<ProductReview> reviews = new ArrayList<>();
        for (JsonNode review : reviewsNode) {
            reviews.add(new ProductReview(
                    text(review, "reviewId", "id", "externalId"),
                    author(review),
                    intValue(first(review, "rating")),
                    text(review, "body", "reviewBody", "content", "text"),
                    verified(review),
                    instantValue(text(review, "dateCreated", "createdAt", "date")),
                    text(review, "variantId"),
                    text(review, "productVariantName", "variantTitle")
            ));
        }
        return reviews;
    }

    private String author(JsonNode review) {
        JsonNode reviewer = first(review, "reviewer", "author", "customer");
        if (reviewer != null) {
            if (reviewer.isObject()) {
                String displayName = text(reviewer, "displayName", "display_name", "name");
                if (hasText(displayName)) {
                    return displayName;
                }
            } else if (!reviewer.isArray()) {
                String displayName = reviewer.asText();
                if (hasText(displayName)) {
                    return displayName;
                }
            }
        }
        return text(review, "reviewerName", "reviewer_name", "author", "name");
    }

    private Boolean verified(JsonNode review) {
        JsonNode reviewer = first(review, "reviewer");
        if (reviewer != null && reviewer.isObject()) {
            Boolean reviewerVerified = booleanValue(first(reviewer, "isVerified", "verified"));
            if (reviewerVerified != null) {
                return reviewerVerified;
            }
        }
        return booleanValue(first(review, "verified", "isVerified", "verifiedBuyer"));
    }

    private JsonNode first(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        if (value == null || value.isObject() || value.isArray()) {
            return null;
        }
        String text = value.asText();
        return hasText(text) ? text : null;
    }

    private Double doubleValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            String text = node.asText();
            return hasText(text) ? Double.valueOf(text) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer intValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        try {
            String text = node.asText();
            return hasText(text) ? Double.valueOf(text).intValue() : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Boolean booleanValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asInt() != 0;
        }
        String text = node.asText();
        if (!hasText(text)) {
            return null;
        }
        return Boolean.valueOf(text);
    }

    private Instant instantValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
