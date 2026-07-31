package com.meant.api.module.agent.service.dto;

import java.math.BigDecimal;
import java.util.List;

/** Model-facing, bounded subset of provider-neutral catalog discovery filters. */
public record SearchCatalogAgentToolInput(
        String query,
        Location shipsTo,
        List<Origin> shipsFrom,
        Price price,
        List<String> conditions,
        List<Attribute> attributes,
        Rating rating,
        List<String> priceTiers,
        Integer offset,
        Integer limit
) {

    public SearchCatalogAgentToolInput {
        shipsFrom = shipsFrom == null ? List.of() : List.copyOf(shipsFrom);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        priceTiers = priceTiers == null ? List.of() : List.copyOf(priceTiers);
    }

    public SearchCatalogAgentToolInput(String query, Integer offset, Integer limit) {
        this(query, null, List.of(), null, List.of(), List.of(), null, List.of(), offset, limit);
    }

    public record Location(String country, String region, String postalCode) {
    }

    public record Origin(String country) {
    }

    /** Buyer-facing USD major units; the tool maps these to provider-neutral minor units. */
    public record Price(BigDecimal minUsd, BigDecimal maxUsd) {
    }

    public record Attribute(String name, List<String> values) {

        public Attribute {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record Rating(BigDecimal variantMinimum, Long variantMinimumCount) {
    }
}
