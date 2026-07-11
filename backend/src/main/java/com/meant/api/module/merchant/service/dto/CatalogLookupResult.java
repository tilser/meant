package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;

import com.meant.api.plugin.spi.NegotiatedCapabilities;

public record CatalogLookupResult(
        String endpoint,
        String productId,
        ProductDetailsResponse.Product product,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public CatalogLookupResult {
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
