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
public class YotpoReviewResponseMapper {

    private final ObjectMapper objectMapper;

    public ProductReviewsResult fromJson(UUID merchantId, String productId, String rawResponse, int limit, int offset) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse == null ? "{}" : rawResponse);
            JsonNode response = root.path("response");
            JsonNode bottomline = response.path("bottomline");
            JsonNode pagination = response.path("pagination");
            Integer reviewCount = firstInt(
                    intValue(first(bottomline, "total_review", "total_reviews")),
                    intValue(first(pagination, "total"))
            );
            List<ProductReview> reviews = reviews(response.path("reviews"));
            return new ProductReviewsResult(
                    merchantId,
                    productId,
                    ReviewProviderType.YOTPO,
                    doubleValue(first(bottomline, "average_score", "averageScore")),
                    reviewCount == null ? reviews.size() : reviewCount,
                    hasMore(pagination, reviews.size(), limit, offset),
                    reviews,
                    false,
                    true,
                    null
            );
        } catch (JacksonException exception) {
            throw new ReviewException("Yotpo Reviews response could not be parsed", exception);
        }
    }

    private boolean hasMore(JsonNode pagination, int returnedReviews, int limit, int offset) {
        Integer total = intValue(first(pagination, "total"));
        if (total != null) {
            return offset + returnedReviews < total;
        }
        return returnedReviews >= limit;
    }

    private List<ProductReview> reviews(JsonNode reviewsNode) {
        if (reviewsNode == null || !reviewsNode.isArray()) {
            return List.of();
        }
        List<ProductReview> reviews = new ArrayList<>();
        for (JsonNode review : reviewsNode) {
            reviews.add(new ProductReview(
                    text(review, "id"),
                    author(review),
                    intValue(first(review, "score", "rating")),
                    text(review, "content", "body", "review", "text"),
                    booleanValue(first(review, "verified_buyer", "verifiedBuyer", "verified")),
                    instantValue(text(review, "created_at", "createdAt")),
                    null,
                    null
            ));
        }
        return reviews;
    }

    private String author(JsonNode review) {
        JsonNode user = first(review, "user");
        if (user != null && user.isObject()) {
            String displayName = text(user, "display_name", "displayName", "name");
            if (hasText(displayName)) {
                return displayName;
            }
        }
        return text(review, "author", "name", "reviewer_name", "reviewerName");
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

    private Integer firstInt(Integer first, Integer second) {
        return first == null ? second : first;
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
