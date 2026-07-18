package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.port.CatalogDiscoverySource;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogItemReference;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
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
        CatalogSimilarityReference similarity = request.similarityReference();
        return request.broad()
                && properties.discoveryEnabled()
                && authProperties.isEnabled()
                && (similarity == null || sourceIdentity().provider().equals(similarity.provider()));
    }

    @Override
    public CatalogSourceResult search(
            CatalogDiscoveryRequest request,
            Consumer<ProductCandidate> candidateConsumer
    ) {
        CatalogSourceResult result = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                request.query(),
                itemReference(request.similarityReference()),
                context(request.context()),
                filters(request.filters(), request.discoveryFilters()),
                request.candidateLimit(),
                null
        ));
        if (result.successful()) {
            result.candidates().forEach(candidateConsumer);
        }
        return result;
    }

    private ShopifyCatalogItemReference itemReference(CatalogSimilarityReference reference) {
        return reference == null ? null : new ShopifyCatalogItemReference(reference.productReference().value());
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

    private ShopifyCatalogFilters filters(
            CatalogSearchFilters baseFilters,
            CatalogDiscoveryFilters discoveryFilters
    ) {
        if (baseFilters == null && discoveryFilters == null) {
            return null;
        }
        List<String> baseCategories = baseFilters == null || baseFilters.categories() == null
                ? List.of()
                : baseFilters.categories().stream()
                        .filter(category -> category != null && !category.isBlank())
                        .map(String::trim)
                        .toList();
        List<String> categories = discoveryFilters == null || discoveryFilters.categoryIds().isEmpty()
                ? baseCategories
                : discoveryFilters.categoryIds();
        ShopifyCatalogFilters.Price price = discoveryFilters != null && discoveryFilters.price() != null
                ? new ShopifyCatalogFilters.Price(
                        discoveryFilters.price().min(), discoveryFilters.price().max())
                : baseFilters == null || baseFilters.price() == null
                ? null
                : new ShopifyCatalogFilters.Price(baseFilters.price().min(), baseFilters.price().max());
        return new ShopifyCatalogFilters(
                discoveryFilters == null ? null : discoveryFilters.available(),
                discoveryFilters == null
                        ? List.of()
                        : discoveryFilters.conditions().stream()
                                .map(condition -> condition.name().toLowerCase(java.util.Locale.ROOT))
                                .toList(),
                discoveryFilters == null ? null : location(discoveryFilters.shipsTo()),
                discoveryFilters == null
                        ? List.of()
                        : discoveryFilters.shipsFrom().stream().map(this::originLocation).toList(),
                price,
                discoveryFilters == null ? List.of() : discoveryFilters.shopIds(),
                categories,
                discoveryFilters == null
                        ? List.of()
                        : discoveryFilters.attributes().stream()
                                .map(attribute -> new ShopifyCatalogFilters.Attribute(
                                        attributeName(attribute.name()), attribute.values()))
                                .toList(),
                discoveryFilters == null || discoveryFilters.rating() == null
                        ? null
                        : new ShopifyCatalogFilters.Rating(new ShopifyCatalogFilters.VariantRating(
                                discoveryFilters.rating().variantMinimum(),
                                discoveryFilters.rating().variantMinimumCount())),
                discoveryFilters == null
                        ? List.of()
                        : discoveryFilters.priceTiers().stream()
                                .map(tier -> tier.name().toLowerCase(java.util.Locale.ROOT))
                                .toList()
        );
    }

    private ShopifyCatalogFilters.Location location(CatalogDiscoveryLocation location) {
        return location == null
                ? null
                : new ShopifyCatalogFilters.Location(
                        location.country(), location.region(), location.postalCode());
    }

    private ShopifyCatalogFilters.Location originLocation(CatalogDiscoveryLocation location) {
        return location == null
                ? null
                : new ShopifyCatalogFilters.Location(location.country(), null, null);
    }

    private String attributeName(CatalogDiscoveryAttributeName name) {
        return switch (name) {
            case COLOR -> "Color";
            case SIZE -> "Size";
            case TARGET_GENDER -> "Target gender";
        };
    }
}
