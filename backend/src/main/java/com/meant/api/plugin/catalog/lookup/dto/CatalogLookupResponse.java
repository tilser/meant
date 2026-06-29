package com.meant.api.plugin.catalog.lookup.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogLookupResponse(
        @JsonProperty("product_id")
        @JsonAlias({"productId", "id"})
        String productId,

        ProductDetailsResponse.Product product,

        List<ProductDetailsResponse.Product> products
) {

    public CatalogLookupResponse(String productId, ProductDetailsResponse.Product product) {
        this(productId, product, List.of());
    }

    public String resolvedProductId(String fallbackProductId) {
        if (productId != null && !productId.isBlank()) {
            return productId;
        }
        if (product != null && product.productId() != null && !product.productId().isBlank()) {
            return product.productId();
        }
        ProductDetailsResponse.Product firstProduct = firstProduct();
        if (firstProduct != null && firstProduct.productId() != null && !firstProduct.productId().isBlank()) {
            return firstProduct.productId();
        }
        return fallbackProductId;
    }

    public ProductDetailsResponse.Product resolvedProduct() {
        return product == null ? firstProduct() : product;
    }

    private ProductDetailsResponse.Product firstProduct() {
        return products == null || products.isEmpty() ? null : products.getFirst();
    }
}
