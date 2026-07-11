package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRetrievalSignal;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductRankingMetricsTest {

    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Test
    void metricsUseOnlyControlledOutcomeFeatureAvailabilityAndVersionTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductRankingService service = ProductRankingTestFactory.service(
                List.of(), new ProductRankingMetrics(registry));

        service.rank(List.of(product()), context());

        assertThat(registry.get("commerce.catalog.ranking.requests")
                .tags(
                        "outcome", "success",
                        "product_version", ProductRankingService.PRODUCT_RANKING_VERSION,
                        "offer_version", OfferRankingService.OFFER_RANKING_VERSION
                )
                .counter().count()).isEqualTo(1.0d);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertSafeTags(meter.getId()));
        assertThat(registry.get("commerce.catalog.ranking.feature.availability")
                .tags(
                        "scope", "offer",
                        "feature", "merchant_trust",
                        "availability", "unknown",
                        "version", OfferRankingService.OFFER_RANKING_VERSION
                )
                .counter().count()).isEqualTo(1.0d);
    }

    @Test
    void rankingExplanationsExcludeRawIntentCredentialsEndpointsAndPayloadText() {
        ProductRankingService service = ProductRankingTestFactory.service();
        var result = service.rank(List.of(product()), context());
        String explanation = result.productExplanations().values().toString()
                + result.offerExplanations().values();

        assertThat(explanation)
                .doesNotContain("private shopper intent")
                .doesNotContain("secret-token")
                .doesNotContain("https://credentials.example/private")
                .doesNotContain("raw-payload");
        assertThat(explanation)
                .contains(ProductRankingService.PRODUCT_RANKING_VERSION)
                .contains(OfferRankingService.OFFER_RANKING_VERSION)
                .contains("CALIBRATED_SOURCE_INTENT_FIT");
    }

    private void assertSafeTags(Meter.Id id) {
        String tags = id.getTags().toString();
        assertThat(tags)
                .doesNotContain("private shopper intent")
                .doesNotContain("secret-token")
                .doesNotContain("credentials.example")
                .doesNotContain("raw-payload");
        assertThat(id.getTags()).hasSizeLessThanOrEqualTo(5);
    }

    private ProductRankingContext context() {
        return new ProductRankingContext(
                "private shopper intent secret-token raw-payload",
                new CatalogSearchContext(null, null, null, "en", "USD", "private shopper intent"),
                null,
                List.of(),
                Map.of(),
                NOW,
                20
        );
    }

    private CanonicalProduct product() {
        ProviderIdentity provider = new ProviderIdentity("FIXTURE");
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.PROVIDER_CATALOG, "FIXTURE_SOURCE");
        ExternalIdentifier merchant = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, provider.value(), "merchant-redacted");
        ExternalIdentifier product = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), "product-redacted");
        ResultProvenance provenance = new ResultProvenance(
                provider,
                source,
                null,
                merchant,
                product,
                null,
                new ResultFreshness(NOW, null),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "fixture-reference",
                        java.net.URI.create("https://credentials.example/private")
                )
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        provider,
                        OfferMerchantScope.external(merchant),
                        product,
                        null,
                        List.of(),
                        List.of(),
                        null
                ),
                "Fixture merchant",
                null,
                null,
                null,
                new OfferAvailability(OfferAvailabilityStatus.UNKNOWN, null, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                "canonical-redacted",
                "Fixture product",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(new ProductRetrievalSignal(
                        source,
                        ProductRetrievalSignal.Feature.INTENT_FIT,
                        7_000,
                        "fixture-calibration-v1"
                )),
                List.of(offer)
        );
    }
}
