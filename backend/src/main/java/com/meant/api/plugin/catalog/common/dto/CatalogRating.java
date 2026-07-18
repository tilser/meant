package com.meant.api.plugin.catalog.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = CatalogRatingDeserializer.class)
public record CatalogRating(
        Double value,
        @JsonAlias({"scale_max", "scaleMax", "max"}) Double scaleMax,
        @JsonAlias({"review_count", "reviewCount", "rating_count", "ratingCount"}) Integer count
) {
}
