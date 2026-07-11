package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.OfferRankingEvidence;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductRankingContext;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingResult;
import com.meant.api.module.catalog.service.dto.ProductRetrievalSignal;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductRankingGoldenFixtureTest {

    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");
    private final ProductRankingService service = ProductRankingTestFactory.service();

    @Test
    void calibratedProviderNeutralSignalsPreventRawScoreScaleCompetition() {
        CanonicalProduct shopify = product(
                "shopify-product", "everyday shirt", "SHOPIFY", "shopify-global", 4_000,
                offer("SHOPIFY", "seller-a", "shopify-product", "variant-a", 4_000,
                        OfferAvailabilityStatus.IN_STOCK, null)
        );
        CanonicalProduct merchantSemantic = product(
                "merchant-product", "everyday shirt", "GENERIC_UCP", "semantic-source", 8_000,
                offer("GENERIC_UCP", "seller-b", "merchant-product", "variant-b", 4_000,
                        OfferAvailabilityStatus.IN_STOCK, null)
        );

        ProductRankingResult result = service.rank(List.of(shopify, merchantSemantic), context("everyday shirt"));

        assertThat(result.products()).extracting(CanonicalProduct::key)
                .containsExactly("merchant-product", "shopify-product");
        assertThat(shopify.retrievalSignals()).singleElement().satisfies(signal -> {
            assertThat(signal.valueBasisPoints()).isEqualTo(4_000);
            assertThat(signal.calibrationVersion()).doesNotContain("score=");
        });
    }

    @Test
    void highVolumeSourceCannotMonopolizeTheTopWindow() {
        List<CanonicalProduct> products = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            products.add(product(
                    "large-" + index, "linen shirt", "LARGE_PROVIDER", "large-source", 9_000,
                    offer("LARGE_PROVIDER", "large-merchant-" + index, "large-" + index, "v", 5_000,
                            OfferAvailabilityStatus.IN_STOCK, null)
            ));
        }
        for (int index = 0; index < 3; index++) {
            products.add(product(
                    "small-" + index, "linen shirt", "SMALL_PROVIDER", "small-source", 5_000,
                    offer("SMALL_PROVIDER", "small-merchant-" + index, "small-" + index, "v", 5_000,
                            OfferAvailabilityStatus.IN_STOCK, null)
            ));
        }

        List<CanonicalProduct> top = service.rank(products, context("linen shirt")).products().stream()
                .limit(20)
                .toList();

        assertThat(top).extracting(CanonicalProduct::key).anyMatch(key -> key.startsWith("small-"));
        assertThat(top).filteredOn(product -> product.key().startsWith("large-")).hasSizeLessThan(20);
    }

    @Test
    void intentDurablePreferenceAndInventoryRelationshipRankProductsWithoutCommercialInputs() {
        CanonicalProduct linen = product(
                "linen", "natural linen shirt", "SOURCE_A", "source-a", 5_000,
                offer("SOURCE_A", "merchant-a", "linen", "v", 9_000,
                        OfferAvailabilityStatus.IN_STOCK, null)
        );
        CanonicalProduct polyester = product(
                "polyester", "polyester jacket", "SOURCE_B", "source-b", 7_000,
                offer("SOURCE_B", "merchant-b", "polyester", "v", 1_000,
                        OfferAvailabilityStatus.IN_STOCK, null)
        );
        ProductRankingContext context = new ProductRankingContext(
                "linen shirt",
                new CatalogSearchContext(null, null, null, "en", "USD", null),
                null,
                List.of(new ProductRankingContext.PreferenceSignal(
                        ProductRankingContext.PreferenceSignal.Type.MATERIAL,
                        "material:linen",
                        "linen",
                        8_000
                )),
                Map.of(
                        "linen", ProductRankingContext.InventoryRelationship.COMPLEMENT,
                        "polyester", ProductRankingContext.InventoryRelationship.DUPLICATE
                ),
                NOW,
                20
        );

        assertThat(service.rank(List.of(polyester, linen), context).products())
                .extracting(CanonicalProduct::key)
                .containsExactly("linen", "polyester");
    }

    @Test
    void priceReliabilityAndAvailabilityReorderOnlyOffersInsideOneCanonicalProduct() {
        Offer cheapUnreliable = offer(
                "SOURCE_A", "cheap", "shared", "cheap-v", 2_000,
                OfferAvailabilityStatus.IN_STOCK, 1_000
        );
        Offer reliable = offer(
                "SOURCE_B", "reliable", "shared", "reliable-v", 3_000,
                OfferAvailabilityStatus.IN_STOCK, 9_500
        );
        CanonicalProduct shared = product(
                "shared", "linen shirt", "SOURCE_A", "source-a", 8_000,
                cheapUnreliable, reliable
        );
        CanonicalProduct other = product(
                "other", "cotton shirt", "SOURCE_C", "source-c", 4_000,
                offer("SOURCE_C", "other", "other", "other-v", 1_000,
                        OfferAvailabilityStatus.IN_STOCK, null)
        );
        ProductRankingResult first = service.rank(List.of(shared, other), context("linen shirt"));

        Offer cheapNowTrusted = offer(
                "SOURCE_A", "cheap", "shared", "cheap-v", 1_000,
                OfferAvailabilityStatus.IN_STOCK, 10_000
        );
        Offer reliableUnavailable = offer(
                "SOURCE_B", "reliable", "shared", "reliable-v", 3_000,
                OfferAvailabilityStatus.OUT_OF_STOCK, 9_500
        );
        CanonicalProduct changedOffers = product(
                "shared", "linen shirt", "SOURCE_A", "source-a", 8_000,
                cheapNowTrusted, reliableUnavailable
        );
        ProductRankingResult second = service.rank(List.of(other, changedOffers), context("linen shirt"));

        assertThat(first.products()).extracting(CanonicalProduct::key)
                .containsExactlyElementsOf(second.products().stream().map(CanonicalProduct::key).toList());
        assertThat(first.products().getFirst().offers()).extracting(Offer::key)
                .containsExactly(reliable.key(), cheapUnreliable.key());
        assertThat(second.products().getFirst().offers()).extracting(Offer::key)
                .containsExactly(cheapNowTrusted.key(), reliableUnavailable.key());
    }

    @Test
    void unknownCommercialFactsStayUnknownAndAreNeverFabricatedAsZero() {
        Offer unknown = offer(
                "SOURCE_A", "unknown", "shared", "unknown-v", null,
                OfferAvailabilityStatus.UNKNOWN, null
        );
        CanonicalProduct product = product(
                "shared", "linen shirt", "SOURCE_A", "source-a", 8_000, unknown
        );

        OfferRankingExplanation explanation = service.rank(List.of(product), context("linen shirt"))
                .offerExplanations().get(unknown.key());

        assertThat(explanation.features())
                .filteredOn(feature -> feature.name() == OfferRankingExplanation.Name.LANDED_PRICE
                        || feature.name() == OfferRankingExplanation.Name.MERCHANT_TRUST
                        || feature.name() == OfferRankingExplanation.Name.RETURN_POLICY)
                .allSatisfy(feature -> {
                    assertThat(feature.availability()).isEqualTo(OfferRankingExplanation.Availability.UNKNOWN);
                    assertThat(feature.valueBasisPoints()).isNull();
                });
    }

    @Test
    void commercialOrAffiliateTextCannotBoostALessRelevantProduct() {
        CanonicalProduct relevant = product(
                "relevant", "linen shirt", "SOURCE_A", "source-a", 8_000,
                offer("SOURCE_A", "merchant-a", "relevant", "v", 5_000,
                        OfferAvailabilityStatus.IN_STOCK, null)
        );
        CanonicalProduct commercial = withAttribute(product(
                "commercial", "polyester coat", "SOURCE_B", "source-b", 3_000,
                offer("SOURCE_B", "merchant-b", "commercial", "v", 100,
                        OfferAvailabilityStatus.IN_STOCK, 10_000)
        ), new ProductAttribute("commercial", "affiliate commission", "100 percent boost"));

        ProductRankingResult result = service.rank(List.of(commercial, relevant), context("linen shirt"));

        assertThat(result.products()).extracting(CanonicalProduct::key)
                .containsExactly("relevant", "commercial");
        assertThat(result.offerExplanations().values())
                .allSatisfy(explanation -> assertThat(explanation.commercialTieBreakPolicy())
                        .isEqualTo(OfferRankingExplanation.CommercialTieBreakPolicy.NONE));
    }

    @Test
    void rankingIsPermutationStableWhenScoresTieAndModelOutputIsAbsent() {
        List<CanonicalProduct> products = List.of(
                product("a", "shirt", "SOURCE_A", "source-a", 5_000,
                        offer("SOURCE_A", "m-a", "a", "v", 1_000, OfferAvailabilityStatus.IN_STOCK, null)),
                product("b", "shirt", "SOURCE_B", "source-b", 5_000,
                        offer("SOURCE_B", "m-b", "b", "v", 1_000, OfferAvailabilityStatus.IN_STOCK, null)),
                product("c", "shirt", "SOURCE_C", "source-c", 5_000,
                        offer("SOURCE_C", "m-c", "c", "v", 1_000, OfferAvailabilityStatus.IN_STOCK, null))
        );
        List<CanonicalProduct> shuffled = new ArrayList<>(products);
        Collections.shuffle(shuffled, new Random(42));

        List<String> first = service.rank(products, context("shirt")).products().stream()
                .map(CanonicalProduct::key).toList();
        List<String> second = service.rank(shuffled, context("shirt")).products().stream()
                .map(CanonicalProduct::key).toList();

        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void tiedModelOutputRemainsPermutationStable() {
        ProductRankingModel tied = new ProductRankingModel() {
            @Override
            public String version() {
                return "tied-model-v1";
            }

            @Override
            public Map<String, Integer> rerank(List<Candidate> candidates) {
                return candidates.stream().collect(java.util.stream.Collectors.toMap(
                        Candidate::canonicalProductKey,
                        ignored -> 5_000
                ));
            }
        };
        ProductRankingService tiedService = ProductRankingTestFactory.service(List.of(tied));
        CanonicalProduct first = product("a", "shirt", "SOURCE_A", "source-a", 5_000,
                offer("SOURCE_A", "m-a", "a", "v", 1_000, OfferAvailabilityStatus.IN_STOCK, null));
        CanonicalProduct second = product("b", "shirt", "SOURCE_B", "source-b", 5_000,
                offer("SOURCE_B", "m-b", "b", "v", 1_000, OfferAvailabilityStatus.IN_STOCK, null));

        assertThat(tiedService.rank(List.of(first, second), context("shirt")).products())
                .extracting(CanonicalProduct::key)
                .containsExactlyElementsOf(tiedService.rank(List.of(second, first), context("shirt"))
                        .products().stream().map(CanonicalProduct::key).toList());
    }

    @Test
    void lowerKnownItemPriceOrdersOffersWhenOtherCommercialFactsAreUnknown() {
        Offer expensive = offer("SOURCE_A", "merchant-a", "shared", "expensive", 5_000,
                OfferAvailabilityStatus.UNKNOWN, null);
        Offer cheap = offer("SOURCE_B", "merchant-b", "shared", "cheap", 2_000,
                OfferAvailabilityStatus.UNKNOWN, null);
        CanonicalProduct product = product(
                "shared", "shirt", "SOURCE_A", "source-a", 5_000, expensive, cheap);

        assertThat(service.rank(List.of(product), context("shirt")).products().getFirst().offers())
                .extracting(Offer::key)
                .containsExactly(cheap.key(), expensive.key());
    }

    @Test
    void exactOfferIdentityAndRoutingObjectsSurviveReorderingUnchanged() {
        Offer expensive = offer(
                "SOURCE_A", "merchant-a", "shared", "variant-expensive", 9_000,
                OfferAvailabilityStatus.IN_STOCK, null
        );
        Offer cheap = offer(
                "SOURCE_A", "merchant-b", "shared", "variant-cheap", 1_000,
                OfferAvailabilityStatus.IN_STOCK, null
        );
        CanonicalProduct product = product(
                "shared", "linen shirt", "SOURCE_A", "source-a", 8_000, expensive, cheap
        );

        List<Offer> ranked = service.rank(List.of(product), context("linen shirt"))
                .products().getFirst().offers();

        assertThat(ranked).extracting(Offer::key).containsExactly(cheap.key(), expensive.key());
        assertThat(ranked.getFirst()).isSameAs(cheap);
        assertThat(ranked.get(1)).isSameAs(expensive);
        assertThat(ranked.getFirst().identity()).isSameAs(cheap.identity());
        assertThat(ranked.getFirst().provenance()).isEqualTo(cheap.provenance());
        assertThat(ranked.getFirst().provenance().getFirst().localRouting())
                .isSameAs(cheap.provenance().getFirst().localRouting());
    }

    @Test
    void modelFailureFallsBackAfterHardAvailabilityEligibility() {
        AtomicReference<List<ProductRankingModel.Candidate>> seen = new AtomicReference<>();
        ProductRankingModel failing = new ProductRankingModel() {
            @Override
            public String version() {
                return "fixture-model-v1";
            }

            @Override
            public Map<String, Integer> rerank(List<Candidate> candidates) {
                seen.set(candidates);
                throw new IllegalStateException("redacted fixture failure");
            }
        };
        ProductRankingService withFailingModel = ProductRankingTestFactory.service(List.of(failing));
        CanonicalProduct eligible = product(
                "eligible", "linen shirt", "SOURCE_A", "source-a", 7_000,
                offer("SOURCE_A", "m-a", "eligible", "v", 5_000, OfferAvailabilityStatus.IN_STOCK, null)
        );
        CanonicalProduct unavailable = product(
                "unavailable", "linen shirt", "SOURCE_B", "source-b", 10_000,
                offer("SOURCE_B", "m-b", "unavailable", "v", 1,
                        OfferAvailabilityStatus.OUT_OF_STOCK, 10_000)
        );

        ProductRankingResult result = withFailingModel.rank(List.of(unavailable, eligible), context("linen shirt"));

        assertThat(seen.get()).extracting(ProductRankingModel.Candidate::canonicalProductKey)
                .containsExactly("eligible");
        assertThat(result.products()).extracting(CanonicalProduct::key).containsExactly("eligible");
        assertThat(result.productExplanations().get("eligible").execution())
                .isEqualTo(ProductRankingExplanation.Execution.MODEL_FALLBACK);
    }

    private ProductRankingContext context(String intent) {
        return new ProductRankingContext(
                intent,
                new CatalogSearchContext(null, null, null, "en", "USD", null),
                null,
                List.of(),
                Map.of(),
                NOW,
                20
        );
    }

    private CanonicalProduct product(
            String key,
            String title,
            String provider,
            String sourceValue,
            int retrievalBasisPoints,
            Offer... offers
    ) {
        DiscoverySourceIdentity source = source(provider, sourceValue);
        List<ResultProvenance> provenance = StreamSupport.provenance(offers);
        return new CanonicalProduct(
                key,
                title,
                title + " description",
                List.of(),
                List.of(new ProductAttribute("category", "category", "apparel")),
                title.contains("linen") ? List.of(new com.meant.api.module.catalog.service.dto.ProductMaterial("linen", null)) : List.of(),
                List.of(),
                List.of(),
                List.of(),
                provenance,
                List.of(new ProductRetrievalSignal(
                        source,
                        ProductRetrievalSignal.Feature.INTENT_FIT,
                        retrievalBasisPoints,
                        provider.toLowerCase() + "-fixture-v1"
                )),
                List.of(offers)
        );
    }

    private CanonicalProduct withAttribute(CanonicalProduct product, ProductAttribute attribute) {
        List<ProductAttribute> attributes = new ArrayList<>(product.attributes());
        attributes.add(attribute);
        return new CanonicalProduct(
                product.key(), product.title(), product.description(), product.media(), attributes,
                product.materials(), product.certifications(), product.attribution(), product.identityEvidence(),
                product.provenance(), product.retrievalSignals(), product.offers()
        );
    }

    private Offer offer(
            String providerValue,
            String merchant,
            String product,
            String variant,
            Integer price,
            OfferAvailabilityStatus availability,
            Integer reliability
    ) {
        ProviderIdentity provider = new ProviderIdentity(providerValue);
        DiscoverySourceIdentity source = source(providerValue, providerValue.toLowerCase() + "-source");
        ExternalIdentifier merchantId = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, provider.value(), merchant);
        ExternalIdentifier productId = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), product);
        ExternalIdentifier variantId = new ExternalIdentifier(
                ExternalIdentifierType.VARIANT, provider.value(), variant);
        ResultProvenance provenance = new ResultProvenance(
                provider,
                source,
                new LocalMerchantRouting(java.util.UUID.nameUUIDFromBytes(
                        (providerValue + ":" + merchant).getBytes(StandardCharsets.UTF_8)
                )),
                merchantId,
                productId,
                variantId,
                new ResultFreshness(NOW.minusSeconds(60), NOW.plusSeconds(3_600)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, source.value(), null)
        );
        return new Offer(
                new OfferIdentity(
                        provider,
                        OfferMerchantScope.external(merchantId),
                        productId,
                        variantId,
                        List.of(new ProductAttribute("variant-option", "Size", "M")),
                        List.of(),
                        null
                ),
                merchant,
                variant,
                price == null ? null : new Money(price, "USD"),
                null,
                new OfferAvailability(availability, null, null),
                List.of(),
                null,
                new OfferRankingEvidence(null, reliability, null, null),
                List.of(provenance)
        );
    }

    private DiscoverySourceIdentity source(String provider, String value) {
        return new DiscoverySourceIdentity(
                new ProviderIdentity(provider),
                ResultSourceType.PROVIDER_CATALOG,
                value
        );
    }

    private static final class StreamSupport {

        private static List<ResultProvenance> provenance(Offer[] offers) {
            return java.util.Arrays.stream(offers)
                    .flatMap(offer -> offer.provenance().stream())
                    .distinct()
                    .toList();
        }
    }
}
