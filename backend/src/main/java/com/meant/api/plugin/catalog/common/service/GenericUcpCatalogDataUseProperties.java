package com.meant.api.plugin.catalog.common.service;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "commerce.catalog.data-use.generic-ucp")
public record GenericUcpCatalogDataUseProperties(
        @NotNull Duration searchCacheTtl,
        @NotNull Duration rehydratedFactsTtl,
        @NotNull Duration transactionSnapshotTtl
) {
}
