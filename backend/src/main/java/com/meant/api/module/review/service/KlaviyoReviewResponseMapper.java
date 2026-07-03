package com.meant.api.module.review.service;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.exception.ReviewException;
import com.meant.api.module.review.service.dto.ProductReview;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class KlaviyoReviewResponseMapper {

    private final ObjectMapper objectMapper;

    public ProductReviewsResult fromJson(UUID merchantId, String productId, String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse == null ? "{}" : rawResponse);
            Double rating = doubleValue(first(root.path("summary"),
                    "rating",
                    "average_rating",
                    "averageRating",
                    "average",
                    "avg_rating"));
            Integer reviewCount = intValue(first(root.path("summary"),
                    "review_count",
                    "reviews_count",
                    "reviewCount",
                    "reviewsCount",
                    "count",
                    "total"));
            if (reviewCount == null) {
                reviewCount = intValue(first(root, "filtered_count", "review_count", "reviews_count"));
            }
            Boolean hasMore = booleanValue(first(root, "has_more", "hasMore"));

            return new ProductReviewsResult(
                    merchantId,
                    productId,
                    ReviewProviderType.KLAVIYO,
                    rating,
                    reviewCount == null ? 0 : reviewCount,
                    hasMore != null && hasMore,
                    reviews(root.path("reviews")),
                    false,
                    true,
                    null
            );
        } catch (JacksonException exception) {
            throw new ReviewException("Klaviyo Reviews response could not be parsed", exception);
        }
    }

    private List<ProductReview> reviews(JsonNode reviewsNode) {
        if (reviewsNode == null || !reviewsNode.isArray()) {
            return List.of();
        }
        List<ProductReview> reviews = new ArrayList<>();
        for (JsonNode review : reviewsNode) {
            reviews.add(new ProductReview(
                    text(review, "id", "review_id", "external_id", "externalId"),
                    author(review),
                    intValue(first(review, "rating", "score")),
                    text(review, "content", "body", "review", "text"),
                    booleanValue(first(review, "verified", "verified_buyer", "verifiedBuyer", "is_verified")),
                    instantValue(text(review, "created_at", "createdAt", "submitted_at", "submittedAt", "date")),
                    text(review, "variant_id", "variantId"),
                    text(review, "variant_title", "variantTitle")
            ));
        }
        return reviews;
    }

    private String author(JsonNode review) {
        JsonNode author = first(review, "author", "customer", "reviewer");
        if (author != null) {
            if (author.isObject()) {
                String name = text(author, "name", "display_name", "displayName");
                if (hasText(name)) {
                    return name;
                }
            } else if (!author.isObject() && !author.isArray()) {
                String name = author.asText();
                if (hasText(name)) {
                    return name;
                }
            }
        }
        return text(review, "name", "customer_name", "customerName", "reviewer_name", "reviewerName");
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
                try {
                    return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException finalIgnored) {
                    return null;
                }
            }
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
