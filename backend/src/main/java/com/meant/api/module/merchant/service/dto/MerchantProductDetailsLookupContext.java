package com.meant.api.module.merchant.service.dto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record MerchantProductDetailsLookupContext(
        MerchantSemanticSearchResult routingMerchant,
        List<String> technicalEndpointAliases
) {

    public MerchantProductDetailsLookupContext {
        Objects.requireNonNull(routingMerchant, "routingMerchant must not be null");
        LinkedHashSet<String> normalizedAliases = new LinkedHashSet<>();
        if (technicalEndpointAliases != null) {
            technicalEndpointAliases.stream()
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(String::trim)
                    .forEach(normalizedAliases::add);
        }
        technicalEndpointAliases = List.copyOf(normalizedAliases);
    }
}
