package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.LinkedHashSet;
import java.util.List;

public record ProductDetailsResult(
        String endpoint,
        String rawResponse,
        ProductDetailsResponse.Product product,
        List<ProductDetailsResponse.Message> messages,
        NegotiatedCapabilities negotiatedCapabilities,
        String merchantDomain,
        List<String> technicalEndpointAliases
) {

    public ProductDetailsResult(String endpoint, String rawResponse, ProductDetailsResponse.Product product) {
        this(endpoint, rawResponse, product, List.of(), NegotiatedCapabilities.none(), null, List.of());
    }

    public ProductDetailsResult(
            String endpoint,
            String rawResponse,
            ProductDetailsResponse.Product product,
            NegotiatedCapabilities negotiatedCapabilities
    ) {
        this(endpoint, rawResponse, product, List.of(), negotiatedCapabilities, null, List.of());
    }

    public ProductDetailsResult(
            String endpoint,
            String rawResponse,
            ProductDetailsResponse.Product product,
            List<ProductDetailsResponse.Message> messages,
            NegotiatedCapabilities negotiatedCapabilities
    ) {
        this(endpoint, rawResponse, product, messages, negotiatedCapabilities, null, List.of());
    }

    public ProductDetailsResult {
        messages = messages == null ? List.of() : messages;
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
        LinkedHashSet<String> normalizedAliases = new LinkedHashSet<>();
        if (technicalEndpointAliases != null) {
            technicalEndpointAliases.stream()
                    .filter(alias -> alias != null && !alias.isBlank())
                    .map(String::trim)
                    .forEach(normalizedAliases::add);
        }
        technicalEndpointAliases = List.copyOf(normalizedAliases);
    }

    public ProductDetailsResult withBuyerContext(
            String merchantDomain,
            List<String> technicalEndpointAliases
    ) {
        return new ProductDetailsResult(
                endpoint,
                rawResponse,
                product,
                messages,
                negotiatedCapabilities,
                merchantDomain,
                technicalEndpointAliases
        );
    }
}
