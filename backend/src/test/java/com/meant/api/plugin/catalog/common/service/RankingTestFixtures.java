package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferDelivery;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.OfferRankingEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRetrievalSignal;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class RankingTestFixtures {
    static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    private RankingTestFixtures() { }

    static ProductRankingContext context(String intent) {
        return context(intent, "USD", "US", null, List.of(), 20);
    }

    static ProductRankingContext context(
            String intent, String currency, String country, CatalogSearchFilters filters,
            List<ProductRankingContext.PreferenceSignal> preferences, int window
    ) {
        return new ProductRankingContext(
                intent, new CatalogSearchContext(country, null, null, "en", currency, null), filters,
                preferences, Map.of(), NOW, window);
    }

    static Offer offer(String provider, String merchant, String product, String variant, Money price,
                       OfferAvailabilityStatus availability, Integer reliability, List<OfferDelivery> delivery) {
        return offer(provider, merchant, product, variant, price, availability, reliability, delivery,
                new ResultFreshness(NOW.minusSeconds(60), NOW.plusSeconds(3_600)));
    }

    static Offer offer(String providerValue, String merchant, String product, String variant, Money price,
                       OfferAvailabilityStatus availability, Integer reliability, List<OfferDelivery> delivery,
                       ResultFreshness freshness) {
        ProviderIdentity provider = new ProviderIdentity(providerValue);
        DiscoverySourceIdentity source = source(providerValue, providerValue.toLowerCase() + "-source");
        ExternalIdentifier merchantId = id(ExternalIdentifierType.MERCHANT, providerValue, merchant);
        ExternalIdentifier productId = id(ExternalIdentifierType.PRODUCT, providerValue, product);
        ExternalIdentifier variantId = id(ExternalIdentifierType.VARIANT, providerValue, variant);
        ResultProvenance provenance = new ResultProvenance(
                provider, source, null, merchantId, productId, variantId, freshness,
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, source.value(), null));
        return new Offer(
                new OfferIdentity(provider, OfferMerchantScope.external(merchantId), productId, variantId,
                        List.of(), List.of(), null), merchant, variant, price, null,
                new OfferAvailability(availability, null, null), delivery, null,
                new OfferRankingEvidence(null, reliability, null, null), List.of(provenance));
    }

    static CanonicalProduct product(String key, String title, String provider, String sourceValue,
                                    int signal, List<ProductAttribute> attributes, Offer... offers) {
        List<ResultProvenance> provenance = Arrays.stream(offers)
                .flatMap(offer -> offer.provenance().stream()).distinct().toList();
        OfferMerchantScope merchant = offers.length == 0 ? null : offers[0].identity().merchantScope();
        return new CanonicalProduct(
                key, title, title == null ? null : title + " description", List.of(), attributes, List.of(),
                List.of(), List.of(), List.of(), provenance,
                List.of(new ProductRetrievalSignal(source(provider, sourceValue), merchant,
                        ProductRetrievalSignal.Feature.INTENT_FIT, signal, provider.toLowerCase() + "-fixture-v1")),
                List.of(offers));
    }

    static CanonicalProduct withSignals(CanonicalProduct product, ProductRetrievalSignal... signals) {
        return new CanonicalProduct(
                product.key(), product.title(), product.description(), product.media(), product.attributes(),
                product.materials(), product.certifications(), product.attribution(), product.identityEvidence(),
                product.provenance(), List.of(signals), product.offers());
    }

    static ProductRetrievalSignal signal(String provider, String sourceValue, OfferMerchantScope merchant, int score) {
        return new ProductRetrievalSignal(source(provider, sourceValue), merchant,
                ProductRetrievalSignal.Feature.INTENT_FIT, score, provider.toLowerCase() + "-fixture-v1");
    }

    static DiscoverySourceIdentity source(String provider, String value) {
        return new DiscoverySourceIdentity(new ProviderIdentity(provider), ResultSourceType.PROVIDER_CATALOG, value);
    }

    private static ExternalIdentifier id(ExternalIdentifierType type, String namespace, String value) {
        return new ExternalIdentifier(type, namespace, value);
    }
}
