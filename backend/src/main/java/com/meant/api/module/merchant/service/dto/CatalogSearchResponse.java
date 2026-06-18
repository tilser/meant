package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSearchResponse(
        List<Product> products,
        Pagination pagination
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            String id,
            String title,
            Description description,
            String url,
            @JsonProperty("price_range")
            PriceRange priceRange,
            @JsonProperty("list_price")
            @JsonAlias({"compare_at_price", "compareAtPrice", "original_price", "regular_price", "was_price"})
            Object listPrice,
            @JsonProperty("rating")
            @JsonAlias({"aggregate_rating", "aggregateRating", "ratings"})
            Object rating,
            @JsonProperty("review_count")
            @JsonAlias({"reviews_count", "reviewCount", "reviewsCount", "rating_count", "ratingCount"})
            Object reviewCount,
            List<Variant> variants,
            List<Media> media,
            List<Category> categories,
            List<String> tags,
            Object skus,
            Object certifications,
            Object materials,
            Object collections,
            Object metadata,
            Object metafields,
            @JsonProperty("tech_specs")
            @JsonAlias({"techSpecs", "specifications"})
            Object techSpecs
    ) {
        public Product(
                String id,
                String title,
                Description description,
                String url,
                PriceRange priceRange,
                List<Variant> variants,
                List<Media> media,
                List<Category> categories,
                List<String> tags
        ) {
            this(
                    id,
                    title,
                    description,
                    url,
                    priceRange,
                    null,
                    null,
                    null,
                    variants,
                    media,
                    categories,
                    tags,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Description(
            String html
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRange(
            Money min,
            Money max
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Money(
            Long amount,
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(
            String id,
            String title,
            Description description,
            Money price,
            String sku,
            @JsonProperty("list_price")
            @JsonAlias({"compare_at_price", "compareAtPrice", "original_price", "regular_price", "was_price"})
            Object listPrice,
            Availability availability,
            List<Media> media
    ) {
        public Variant(
                String id,
                String title,
                Description description,
                Money price,
                Availability availability,
                List<Media> media
        ) {
            this(id, title, description, price, null, null, availability, media);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Availability(
            Boolean available
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
            String type,
            String url,
            @JsonProperty("alt_text")
            @JsonAlias("altText")
            String altText,
            @JsonProperty("preview_image_url")
            @JsonAlias("previewImageUrl")
            String previewImageUrl
    ) {
        public Media(String type, String url) {
            this(type, url, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String value,
            String taxonomy
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(
            @JsonProperty("has_next_page")
            Boolean hasNextPage,
            String cursor
    ) {
    }
}
