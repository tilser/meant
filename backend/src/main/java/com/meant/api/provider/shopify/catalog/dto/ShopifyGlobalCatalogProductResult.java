package com.meant.api.provider.shopify.catalog.dto;

import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import java.util.List;
import java.util.Objects;

/** One get_product observation with both normalized matching candidates and its typed raw detail payload. */
public record ShopifyGlobalCatalogProductResult(
        CatalogSourceResult catalogResult,
        ShopifyGlobalCatalogResponse.Product product,
        List<ShopifyGlobalCatalogResponse.Message> messages
) {
    public ShopifyGlobalCatalogProductResult {
        if (catalogResult == null) {
            throw new IllegalArgumentException("Shopify product result requires a catalog result");
        }
        messages = messages == null
                ? List.of()
                : messages.stream().filter(Objects::nonNull).toList();
        if (catalogResult.successful() != (product != null)) {
            throw new IllegalArgumentException("Successful Shopify product result requires its typed product payload");
        }
    }
}
