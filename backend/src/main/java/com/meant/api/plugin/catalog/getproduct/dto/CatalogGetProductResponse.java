package com.meant.api.plugin.catalog.getproduct.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogProductResponse;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogGetProductResponse(
        CatalogProductResponse product,
        String instructions,
        List<ProductDetailsResponse.Message> messages
) {

    public ProductDetailsResponse toProductDetailsResponse() {
        return new ProductDetailsResponse(
                product == null ? null : product.toProductDetailsProduct(),
                instructions,
                messages == null ? List.of() : messages
        );
    }
}
