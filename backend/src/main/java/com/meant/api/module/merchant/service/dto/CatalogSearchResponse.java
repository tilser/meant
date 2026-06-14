package com.meant.api.module.merchant.service.dto;

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
            List<Variant> variants,
            List<Media> media,
            List<Category> categories,
            List<String> tags
    ) {
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
            Availability availability,
            List<Media> media
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Availability(
            Boolean available
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(
            String type,
            String url
    ) {
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
