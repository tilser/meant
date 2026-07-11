package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.port.CatalogDiscoverySource;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Federated search adapter around the one-call Shopify Global Catalog provider. */
@Component
@RequiredArgsConstructor
public class ShopifyGlobalCatalogDiscoverySource implements CatalogDiscoverySource {

    private final ShopifyGlobalCatalogProvider provider;
    private final ShopifyGlobalCatalogProperties properties;
    private final ShopifyAgentAuthProperties authProperties;

    @Override
    public DiscoverySourceIdentity sourceIdentity() {
        return provider.discoverySourceIdentity();
    }

    @Override
    public Duration timeout() {
        return properties.requestDeadline();
    }

    @Override
    public boolean supports(CatalogDiscoveryRequest request) {
        return request.broad() && properties.discoveryEnabled() && authProperties.isEnabled();
    }

    @Override
    public CatalogSourceResult search(
            CatalogDiscoveryRequest request,
            Consumer<ProductCandidate> candidateConsumer
    ) {
        CatalogSourceResult result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                request.query(),
                context(request.context()),
                filters(request.filters()),
                request.candidateLimit(),
                null
        ));
        if (result.successful()) {
            result.candidates().forEach(candidateConsumer);
        }
        return result;
    }

    private ShopifyCatalogContext context(CatalogSearchContext context) {
        return context == null ? null : new ShopifyCatalogContext(
                context.addressCountry(),
                context.addressRegion(),
                context.postalCode(),
                context.language(),
                context.currency(),
                context.intent()
        );
    }

    private ShopifyCatalogFilters filters(CatalogSearchFilters filters) {
        if (filters == null) {
            return null;
        }
        List<ShopifyCatalogFilters.Category> categories = filters.categories() == null
                ? List.of()
                : filters.categories().stream()
                        .filter(category -> category != null && !category.isBlank())
                        .map(category -> new ShopifyCatalogFilters.Category(category.trim(), null))
                        .toList();
        ShopifyCatalogFilters.Price price = filters.price() == null
                ? null
                : new ShopifyCatalogFilters.Price(filters.price().min(), filters.price().max());
        return new ShopifyCatalogFilters(
                null,
                List.of(),
                null,
                List.of(),
                price,
                List.of(),
                categories,
                List.of(),
                null,
                List.of()
        );
    }
}
