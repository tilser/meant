package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogLookupRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyCatalogSearchCandidateVerifierTest {

    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER,
            ResultSourceType.PROVIDER_CATALOG,
            "SHOPIFY_GLOBAL_CATALOG"
    );

    @Test
    void excludesSearchCandidatesThatExactLookupCannotResolveForTheBuyerMarket() {
        ProductCandidate verified = candidate("verified", "merchant-a.myshopify.com");
        ProductCandidate unavailable = candidate("unavailable", "merchant-b.myshopify.com");
        FakeProvider provider = new FakeProvider(properties(), List.of(success(List.of(verified))));
        ShopifyCatalogSearchCandidateVerifier verifier = new ShopifyCatalogSearchCandidateVerifier(
                provider,
                properties(),
                new ShopifyCatalogReferenceMatcher()
        );
        ShopifyCatalogContext context = new ShopifyCatalogContext("CZ", null, null, "cs", "USD", null);
        ShopifyCatalogFilters filters = new ShopifyCatalogFilters(
                true,
                List.of("new"),
                new ShopifyCatalogFilters.Location("CZ", null, null),
                List.of(),
                new ShopifyCatalogFilters.Price(1000L, 5000L),
                List.of("gid://shopify/Shop/1"),
                List.of("category"),
                List.of(new ShopifyCatalogFilters.Attribute("Color", List.of("Red"))),
                null,
                List.of("medium")
        );

        ShopifyCatalogSearchCandidateVerifier.Verification result = verifier.verify(
                List.of(verified, unavailable),
                context,
                filters
        );

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).containsExactly(verified);
        assertThat(result.truncated()).isFalse();
        assertThat(provider.requests).hasSize(1);
        assertThat(provider.requests.getFirst().ids())
                .containsExactly(variantId("verified").value(), variantId("unavailable").value());
        assertThat(provider.requests.getFirst().context()).isEqualTo(context);
        assertThat(provider.requests.getFirst().filters().available()).isTrue();
        assertThat(provider.requests.getFirst().filters().condition()).containsExactly("new");
        assertThat(provider.requests.getFirst().filters().shipsTo()).isEqualTo(filters.shipsTo());
        assertThat(provider.requests.getFirst().filters().shops()).containsExactly("gid://shopify/Shop/1");
        assertThat(provider.requests.getFirst().filters().price()).isNull();
        assertThat(provider.requests.getFirst().filters().categories()).isNull();
        assertThat(provider.requests.getFirst().filters().attributes()).isNull();
        assertThat(provider.requests.getFirst().filters().priceTier()).isNull();
    }

    @Test
    void failsClosedWhenLookupVerificationFails() {
        CatalogSourceFailure failure = new CatalogSourceFailure(
                CatalogSourceFailureKind.TIMEOUT,
                "lookup timed out",
                null,
                null
        );
        FakeProvider provider = new FakeProvider(properties(), List.of(failure(failure)));
        ShopifyCatalogSearchCandidateVerifier verifier = new ShopifyCatalogSearchCandidateVerifier(
                provider,
                properties(),
                new ShopifyCatalogReferenceMatcher()
        );

        ShopifyCatalogSearchCandidateVerifier.Verification result = verifier.verify(
                List.of(candidate("unverified", "merchant.myshopify.com")),
                new ShopifyCatalogContext("CZ", null, null, null, "USD", null),
                null
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.failure()).isSameAs(failure);
    }

    private ProductCandidate candidate(String suffix, String merchantDomain) {
        ExternalIdentifier merchant = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT,
                PROVIDER.value(),
                "gid://shopify/Shop/" + suffix
        );
        ExternalIdentifier product = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT,
                PROVIDER.value(),
                "gid://shopify/p/" + suffix
        );
        ExternalIdentifier variant = variantId(suffix);
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                SOURCE,
                null,
                merchant,
                merchantDomain,
                product,
                variant,
                new ResultFreshness(Instant.parse("2026-08-01T12:00:00Z"), null),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "shopify-global-catalog",
                        URI.create("https://catalog.shopify.test")
                )
        );
        ProductAttribute option = new ProductAttribute("variant-option", "Color", "Red");
        OfferIdentity identity = new OfferIdentity(
                PROVIDER,
                OfferMerchantScope.external(merchant),
                product,
                variant,
                List.of(option),
                List.of(),
                null
        );
        Offer offer = new Offer(
                identity,
                suffix,
                "Red",
                new Money(2500L, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new ProductCandidate(
                "Product " + suffix,
                null,
                List.of(),
                List.of(option),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                offer
        );
    }

    private ExternalIdentifier variantId(String suffix) {
        return new ExternalIdentifier(
                ExternalIdentifierType.VARIANT,
                PROVIDER.value(),
                "gid://shopify/ProductVariant/" + suffix
        );
    }

    private CatalogSourceResult success(List<ProductCandidate> candidates) {
        return result(candidates, null);
    }

    private CatalogSourceResult failure(CatalogSourceFailure failure) {
        return result(List.of(), failure);
    }

    private CatalogSourceResult result(
            List<ProductCandidate> candidates,
            CatalogSourceFailure failure
    ) {
        return new CatalogSourceResult(
                PROVIDER,
                SOURCE,
                CatalogSourceOperation.LOOKUP,
                "2026-04-08",
                NegotiatedCapabilities.none(),
                candidates,
                null,
                false,
                failure
        );
    }

    private ShopifyGlobalCatalogProperties properties() {
        return new ShopifyGlobalCatalogProperties(
                true,
                true,
                URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "2026-04-08",
                Duration.ofMinutes(15),
                10,
                50,
                50,
                200,
                16,
                Duration.ofSeconds(2),
                Duration.ofSeconds(8),
                Duration.ofSeconds(10),
                3,
                Duration.ofSeconds(30)
        );
    }

    private static final class FakeProvider extends ShopifyGlobalCatalogProvider {
        private final List<CatalogSourceResult> results;
        private final List<ShopifyGlobalCatalogLookupRequest> requests = new ArrayList<>();
        private int calls;

        private FakeProvider(
                ShopifyGlobalCatalogProperties properties,
                List<CatalogSourceResult> results
        ) {
            super(null, null, null, null, properties);
            this.results = List.copyOf(results);
        }

        @Override
        public CatalogSourceResult lookupCatalog(ShopifyGlobalCatalogLookupRequest request) {
            requests.add(request);
            if (calls >= results.size()) {
                throw new AssertionError("Unexpected Shopify lookup verification request");
            }
            return results.get(calls++);
        }
    }
}
