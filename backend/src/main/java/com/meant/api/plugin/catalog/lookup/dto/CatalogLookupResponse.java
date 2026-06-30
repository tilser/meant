package com.meant.api.plugin.catalog.lookup.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogProductResponse;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogLookupResponse(
        @JsonProperty("product_id")
        @JsonAlias({"productId", "id"})
        String productId,

        CatalogProductResponse product,

        List<CatalogProductResponse> products
) {

    public CatalogLookupResponse(String productId, CatalogProductResponse product) {
        this(productId, product, List.of());
    }

    public String resolvedProductId(String fallbackProductId) {
        if (productId != null && !productId.isBlank()) {
            return productId;
        }
        if (product != null && product.id() != null && !product.id().isBlank()) {
            return product.id();
        }
        CatalogProductResponse firstProduct = firstProduct();
        if (firstProduct != null && firstProduct.id() != null && !firstProduct.id().isBlank()) {
            return firstProduct.id();
        }
        return fallbackProductId;
    }

    public ProductDetailsResponse.Product resolvedProduct() {
        CatalogProductResponse resolvedProduct = product == null ? firstProduct() : product;
        return resolvedProduct == null ? null : resolvedProduct.toProductDetailsProduct();
    }

    private CatalogProductResponse firstProduct() {
        return products == null || products.isEmpty() ? null : products.getFirst();
    }
}
