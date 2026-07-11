package com.meant.api.plugin.catalog.extension.shopify;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shopify.global-catalog")
public record ShopifyGlobalCatalogExtensionProperties(
        @NotBlank String protocolVersion
) {
}
