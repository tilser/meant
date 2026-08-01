package com.meant.api.module.catalog.service.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.module.catalog.properties.CatalogProductObservationCacheProperties;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bounded cache for current provider observations. Cache identity deliberately excludes the
 * server-issued interaction key while returned results are rebound to the requesting interaction.
 */
@Component
public class CatalogProductObservationCache {
    private static final String CACHE_INTERACTION_KEY = "catalog-observation-cache";
    private static final Duration DEFAULT_MAXIMUM_TTL = Duration.ofMinutes(2);
    private static final long DEFAULT_REHYDRATION_MAXIMUM_SIZE = 10_000;
    private static final long DEFAULT_DETAIL_MAXIMUM_SIZE = 2_000;

    private final Cache<ObservationKey, CacheEntry<CatalogProductRehydrationResult>> rehydrations;
    private final Cache<DetailKey, CacheEntry<CatalogProductDetailResult>> details;
    private final Duration maximumTtl;
    private final Clock clock;

    @Autowired
    public CatalogProductObservationCache(CatalogProductObservationCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public CatalogProductObservationCache(
            CatalogProductObservationCacheProperties properties,
            Clock clock
    ) {
        this.maximumTtl = properties.maximumTtl();
        this.clock = clock;
        this.rehydrations = Caffeine.newBuilder()
                .expireAfterWrite(properties.maximumTtl())
                .maximumSize(properties.rehydrationMaximumSize())
                .build();
        this.details = Caffeine.newBuilder()
                .expireAfterWrite(properties.maximumTtl())
                .maximumSize(properties.detailMaximumSize())
                .build();
    }

    public static CatalogProductObservationCache withDefaults() {
        return new CatalogProductObservationCache(
                new CatalogProductObservationCacheProperties(
                        DEFAULT_MAXIMUM_TTL,
                        DEFAULT_REHYDRATION_MAXIMUM_SIZE,
                        DEFAULT_DETAIL_MAXIMUM_SIZE
                ),
                Clock.systemUTC()
        );
    }

    public Optional<CatalogProductRehydrationResult> findRehydration(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        CacheEntry<CatalogProductRehydrationResult> cached = current(
                rehydrations,
                new ObservationKey(cacheIdentity(reference), context)
        );
        return cached == null
                ? Optional.empty()
                : Optional.of(rebind(cached.value(), reference));
    }

    public void rememberRehydration(
            CatalogProductRehydrationResult result,
            CatalogRehydrationContext context
    ) {
        if (!cacheable(result)) {
            return;
        }
        Instant expiresAt = expiresAt(result);
        if (!expiresAt.isAfter(clock.instant())) {
            return;
        }
        rehydrations.put(
                new ObservationKey(cacheIdentity(result.reference()), context),
                new CacheEntry<>(result, expiresAt)
        );
    }

    public Optional<CatalogProductDetailResult> findDetail(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
        CacheEntry<CatalogProductDetailResult> cached = current(
                details,
                new DetailKey(cacheIdentity(reference), selection, context)
        );
        return cached == null
                ? Optional.empty()
                : Optional.of(rebind(cached.value(), reference));
    }

    public void rememberDetail(
            CatalogProductDetailResult result,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
        if (result == null || result.details() == null || !cacheable(result.rehydration())) {
            return;
        }
        Instant expiresAt = expiresAt(result.rehydration());
        if (!expiresAt.isAfter(clock.instant())) {
            return;
        }
        details.put(
                new DetailKey(cacheIdentity(result.rehydration().reference()), selection, context),
                new CacheEntry<>(result, expiresAt)
        );
        if (selection == null) {
            rememberRehydration(result.rehydration(), context);
        }
    }

    private <K, V> CacheEntry<V> current(Cache<K, CacheEntry<V>> cache, K key) {
        CacheEntry<V> cached = cache.getIfPresent(key);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt().isAfter(clock.instant())) {
            return cached;
        }
        cache.invalidate(key);
        return null;
    }

    private boolean cacheable(CatalogProductRehydrationResult result) {
        return result != null
                && result.status() == CatalogRehydrationStatus.FRESH
                && result.facts() != null
                && result.facts().freshness().freshUntil() != null;
    }

    private Instant expiresAt(CatalogProductRehydrationResult result) {
        Instant maximumExpiration = clock.instant().plus(maximumTtl);
        Instant providerExpiration = result.facts().freshness().freshUntil();
        return providerExpiration.isBefore(maximumExpiration) ? providerExpiration : maximumExpiration;
    }

    private CatalogProductReference cacheIdentity(CatalogProductReference reference) {
        return withInteractionKey(reference, CACHE_INTERACTION_KEY);
    }

    private CatalogProductRehydrationResult rebind(
            CatalogProductRehydrationResult cached,
            CatalogProductReference requested
    ) {
        return CatalogProductRehydrationResult.fresh(
                requested,
                withInteractionKey(cached.resolvedReference(), requested.interactionKey()),
                cached.facts()
        );
    }

    private CatalogProductDetailResult rebind(
            CatalogProductDetailResult cached,
            CatalogProductReference requested
    ) {
        return new CatalogProductDetailResult(
                rebind(cached.rehydration(), requested),
                cached.details(),
                cached.selection()
        );
    }

    private CatalogProductReference withInteractionKey(
            CatalogProductReference reference,
            String interactionKey
    ) {
        return new CatalogProductReference(
                interactionKey,
                reference.discoverySource(),
                reference.localMerchantId(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                reference.selectedOptions(),
                reference.components(),
                reference.sellingPlanIdentity()
        );
    }

    private record ObservationKey(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
    }

    private record DetailKey(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }
}
