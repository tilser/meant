package com.meant.api.plugin.catalog.getproduct.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogProductResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogGetProductResponse(
        CatalogProductResponse product,
        String instructions
) {

    public ProductDetailsResponse toProductDetailsResponse() {
        return new ProductDetailsResponse(
                product == null ? null : product.toProductDetailsProduct(),
                instructions
        );
    }
}
