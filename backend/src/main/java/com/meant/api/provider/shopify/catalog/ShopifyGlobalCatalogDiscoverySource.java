package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.port.CatalogDiscoverySource;
import com.meant.api.module.merchant.service.MerchantShopifyIdentityLookupService;
import com.meant.api.module.merchant.service.query.FindMerchantShopifyIdentityQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogItemReference;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Federated search adapter around bounded sequential Shopify Global Catalog pages. */
@Component
public class ShopifyGlobalCatalogDiscoverySource implements CatalogDiscoverySource {

    private static final Pattern SHOP_GID =
            Pattern.compile("^gid://shopify/Shop/[1-9][0-9]*$");

    private final ShopifyGlobalCatalogProvider provider;
    private final ShopifyGlobalCatalogProperties properties;
    private final ShopifyAgentAuthProperties authProperties;
    private final MerchantShopifyIdentityLookupService merchantShopifyIdentityLookupService;
    private final LongSupplier nanoTime;

    @Autowired
    public ShopifyGlobalCatalogDiscoverySource(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties properties,
            ShopifyAgentAuthProperties authProperties,
            MerchantShopifyIdentityLookupService merchantShopifyIdentityLookupService
    ) {
        this(
                provider,
                properties,
                authProperties,
                merchantShopifyIdentityLookupService,
                System::nanoTime
        );
    }

    ShopifyGlobalCatalogDiscoverySource(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties properties,
            ShopifyAgentAuthProperties authProperties,
            MerchantShopifyIdentityLookupService merchantShopifyIdentityLookupService,
            LongSupplier nanoTime
    ) {
        this.provider = provider;
        this.properties = properties;
        this.authProperties = authProperties;
        this.merchantShopifyIdentityLookupService = merchantShopifyIdentityLookupService;
        this.nanoTime = nanoTime;
    }

    ShopifyGlobalCatalogDiscoverySource(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties properties,
            ShopifyAgentAuthProperties authProperties
    ) {
        this(provider, properties, authProperties, null);
    }

    @Override
    public DiscoverySourceIdentity sourceIdentity() {
        return provider.discoverySourceIdentity();
    }

    @Override
    public Duration timeout() {
        return properties.discoverySourceTimeout();
    }

    @Override
    public boolean supports(CatalogDiscoveryRequest request) {
        CatalogSimilarityReference similarity = request.similarityReference();
        return properties.discoveryEnabled()
                && (request.broad() || request.merchantId() != null)
                && (similarity == null || sourceIdentity().provider().equals(similarity.provider()));
    }

    @Override
    public CatalogSourceResult search(
            CatalogDiscoveryRequest request,
            Consumer<ProductCandidate> candidateConsumer
    ) {
        long startedNanos = nanoTime.getAsLong();
        long timeoutNanos = timeout().toNanos();
        Optional<String> resolvedShopId;
        try {
            resolvedShopId = scopedShopId(request);
        } catch (RuntimeException exception) {
            return deadlineReached(startedNanos, timeoutNanos)
                    ? timeoutFailure(null)
                    : unavailableMerchantIdentityFailure();
        }
        if (deadlineReached(startedNanos, timeoutNanos)) {
            return timeoutFailure(null);
        }
        if (request.merchantId() != null
                && (resolvedShopId.isEmpty() || !isCanonicalShopGid(resolvedShopId.get()))) {
            return unavailableMerchantIdentityFailure();
        }
        String scopedShopId = resolvedShopId.orElse(null);
        ShopifyCatalogItemReference itemReference = itemReference(request.similarityReference());
        ShopifyCatalogContext context = context(request.context());
        ShopifyCatalogFilters filters = filters(
                request.filters(),
                request.discoveryFilters(),
                scopedShopId
        );
        LinkedHashMap<String, ProductCandidate> candidates = new LinkedHashMap<>();
        LinkedHashSet<String> seenCursors = new LinkedHashSet<>();
        CatalogSourceResult lastPage = null;
        String cursor = null;
        int pagesFetched = 0;

        while (candidates.size() < request.candidateLimit()) {
            if (deadlineReached(startedNanos, timeoutNanos)) {
                return timeoutFailure(lastPage);
            }
            int pageLimit = Math.min(
                    request.candidateLimit() - candidates.size(),
                    properties.maximumResultLimit()
            );
            CatalogSourceResult page = provider.searchCatalog(new ShopifyGlobalCatalogSearchRequest(
                    request.query(),
                    itemReference,
                    context,
                    request.signals(),
                    filters,
                    pageLimit,
                    cursor
            ));
            pagesFetched++;
            if (!page.successful()) {
                return page;
            }
            lastPage = page;
            if (deadlineReached(startedNanos, timeoutNanos)) {
                return timeoutFailure(lastPage);
            }
            if (scopedShopId != null && !validScopedCandidates(page.candidates(), scopedShopId)) {
                return merchantScopeMismatchFailure(lastPage);
            }

            boolean omittedUniqueCandidate = false;
            for (ProductCandidate candidate : page.candidates()) {
                String key = candidate.offer().key();
                if (candidates.containsKey(key)) {
                    continue;
                }
                if (candidates.size() >= request.candidateLimit()) {
                    omittedUniqueCandidate = true;
                    continue;
                }
                candidates.put(key, candidate);
            }

            boolean requestedLimitReached = candidates.size() >= request.candidateLimit();
            boolean hasNextPage = page.page() != null && page.page().hasNextPage();
            boolean truncated = page.truncated() || omittedUniqueCandidate || hasNextPage;
            if (page.truncated() || omittedUniqueCandidate || requestedLimitReached) {
                return emit(aggregate(page, candidates, truncated), candidateConsumer);
            }
            if (!hasNextPage) {
                return emit(aggregate(page, candidates, false), candidateConsumer);
            }
            if (pagesFetched >= properties.maximumSearchPages()) {
                return emit(aggregate(page, candidates, true), candidateConsumer);
            }

            String nextCursor = page.page().cursor();
            if (nextCursor == null || !seenCursors.add(nextCursor)) {
                return emit(aggregate(page, candidates, true), candidateConsumer);
            }
            if (deadlineReached(startedNanos, timeoutNanos)) {
                return timeoutFailure(lastPage);
            }
            cursor = nextCursor;
        }

        return emit(aggregate(lastPage, candidates, false), candidateConsumer);
    }

    private CatalogSourceResult aggregate(
            CatalogSourceResult lastPage,
            LinkedHashMap<String, ProductCandidate> candidates,
            boolean truncated
    ) {
        return new CatalogSourceResult(
                lastPage.provider(),
                lastPage.discoverySource(),
                CatalogSourceOperation.SEARCH,
                lastPage.protocolVersion(),
                lastPage.negotiatedCapabilities(),
                List.copyOf(candidates.values()),
                lastPage.page(),
                truncated,
                null
        );
    }

    private CatalogSourceResult emit(
            CatalogSourceResult result,
            Consumer<ProductCandidate> candidateConsumer
    ) {
        result.candidates().forEach(candidateConsumer);
        return result;
    }

    private CatalogSourceResult timeoutFailure(CatalogSourceResult lastPage) {
        return failure(
                lastPage,
                CatalogSourceFailureKind.TIMEOUT,
                "Shopify Global Catalog pagination exceeded its source deadline"
        );
    }

    private CatalogSourceResult unavailableMerchantIdentityFailure() {
        return failure(
                null,
                CatalogSourceFailureKind.UNAVAILABLE,
                "Verified Shopify merchant identity was unavailable for scoped search"
        );
    }

    private CatalogSourceResult merchantScopeMismatchFailure(CatalogSourceResult lastPage) {
        return failure(
                lastPage,
                CatalogSourceFailureKind.MALFORMED_RESPONSE,
                "Shopify Global Catalog returned a candidate outside the verified merchant scope"
        );
    }

    private CatalogSourceResult failure(
            CatalogSourceResult lastPage,
            CatalogSourceFailureKind kind,
            String message
    ) {
        DiscoverySourceIdentity source = sourceIdentity();
        return new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                lastPage == null ? properties.protocolVersion() : lastPage.protocolVersion(),
                lastPage == null ? NegotiatedCapabilities.none() : lastPage.negotiatedCapabilities(),
                List.of(),
                null,
                false,
                new CatalogSourceFailure(
                        kind,
                        message,
                        null,
                        null
                )
        );
    }

    private boolean validScopedCandidates(List<ProductCandidate> candidates, String scopedShopId) {
        return candidates != null
                && candidates.stream().allMatch(candidate -> validScopedCandidate(candidate, scopedShopId));
    }

    private boolean validScopedCandidate(ProductCandidate candidate, String scopedShopId) {
        if (candidate == null
                || candidate.offer() == null
                || candidate.offer().identity() == null
                || candidate.offer().identity().merchantScope() == null
                || !matchesScopedShop(
                        candidate.offer().identity().merchantScope().externalMerchantIdentity(),
                        scopedShopId)
                || candidate.provenance() == null
                || candidate.provenance().isEmpty()
                || candidate.offer().provenance() == null
                || candidate.offer().provenance().isEmpty()) {
            return false;
        }
        return Stream.concat(candidate.provenance().stream(), candidate.offer().provenance().stream())
                .allMatch(provenance -> matchesScopedShop(provenance, scopedShopId));
    }

    private boolean matchesScopedShop(ResultProvenance provenance, String scopedShopId) {
        return provenance != null
                && matchesScopedShop(provenance.externalMerchantReference(), scopedShopId);
    }

    private boolean matchesScopedShop(ExternalIdentifier identifier, String scopedShopId) {
        if (identifier == null
                || identifier.type() != ExternalIdentifierType.MERCHANT
                || !sourceIdentity().provider().value().equals(identifier.namespace())) {
            return false;
        }
        return isCanonicalShopGid(identifier.value())
                && scopedShopId.equals(identifier.value());
    }

    private boolean isCanonicalShopGid(String value) {
        return value != null && SHOP_GID.matcher(value).matches();
    }

    private boolean deadlineReached(long startedNanos, long timeoutNanos) {
        return Thread.currentThread().isInterrupted()
                || nanoTime.getAsLong() - startedNanos >= timeoutNanos;
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
            CatalogDiscoveryFilters discoveryFilters,
            String scopedShopId
    ) {
        if (baseFilters == null && discoveryFilters == null && scopedShopId == null) {
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
        LinkedHashSet<String> shopIds = new LinkedHashSet<>();
        if (scopedShopId != null) {
            shopIds.add(scopedShopId);
        } else if (discoveryFilters != null) {
            shopIds.addAll(discoveryFilters.shopIds());
        }
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
                List.copyOf(shopIds),
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

    private Optional<String> scopedShopId(CatalogDiscoveryRequest request) {
        if (request.merchantId() == null || merchantShopifyIdentityLookupService == null) {
            return Optional.empty();
        }
        return merchantShopifyIdentityLookupService.find(
                new FindMerchantShopifyIdentityQuery(request.merchantId())
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
