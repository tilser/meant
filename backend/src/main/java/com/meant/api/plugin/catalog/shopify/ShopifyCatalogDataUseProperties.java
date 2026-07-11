package com.meant.api.plugin.catalog.shopify;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Reviewed data-use switches are deliberately separate from transport/discovery configuration. */
@Validated
@ConfigurationProperties(prefix = "shopify.global-catalog.data-use")
public record ShopifyCatalogDataUseProperties(
        boolean searchPersistenceApproved,
        @NotNull Duration approvedSearchCacheTtl,
        @NotNull Duration rehydratedFactsTtl
) {
}
