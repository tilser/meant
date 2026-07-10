package com.meant.api.plugin.catalog.shopify.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShopifyCatalogFilters(
        Boolean available,
        List<String> condition,
        @JsonProperty("ships_to") Location shipsTo,
        @JsonProperty("ships_from") List<Location> shipsFrom,
        Price price,
        List<String> shops,
        List<Category> categories,
        List<Attribute> attributes,
        Rating rating,
        @JsonProperty("price_tier") List<String> priceTier
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Location(String country, String region, @JsonProperty("postal_code") String postalCode) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Price(Long min, Long max) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Category(String id, String taxonomy) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Attribute(String name, List<String> values) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Rating(VariantRating variant) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record VariantRating(BigDecimal min, @JsonProperty("min_count") Long minCount) {
    }
}
