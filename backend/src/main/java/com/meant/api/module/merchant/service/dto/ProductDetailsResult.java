package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;

public record ProductDetailsResult(
        String endpoint,
        String rawResponse,
        ProductDetailsResponse.Product product,
        List<ProductDetailsResponse.Message> messages,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public ProductDetailsResult(String endpoint, String rawResponse, ProductDetailsResponse.Product product) {
        this(endpoint, rawResponse, product, List.of(), NegotiatedCapabilities.none());
    }

    public ProductDetailsResult(
            String endpoint,
            String rawResponse,
            ProductDetailsResponse.Product product,
            NegotiatedCapabilities negotiatedCapabilities
    ) {
        this(endpoint, rawResponse, product, List.of(), negotiatedCapabilities);
    }

    public ProductDetailsResult {
        messages = messages == null ? List.of() : messages;
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
