package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.spi.NegotiatedCapabilities;

public record ProductDetailsResult(
        String endpoint,
        String rawResponse,
        ProductDetailsResponse.Product product,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public ProductDetailsResult(String endpoint, String rawResponse, ProductDetailsResponse.Product product) {
        this(endpoint, rawResponse, product, NegotiatedCapabilities.none());
    }

    public ProductDetailsResult {
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
