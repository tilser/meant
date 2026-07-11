package com.meant.api.module.merchant.service.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchResponse;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;

public record CatalogSearchResult(
        String endpoint,
        List<CatalogSearchResponse.Product> products,
        NegotiatedCapabilities negotiatedCapabilities
) {

    public CatalogSearchResult(String endpoint, List<CatalogSearchResponse.Product> products) {
        this(endpoint, products, NegotiatedCapabilities.none());
    }

    public CatalogSearchResult {
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
    }
}
