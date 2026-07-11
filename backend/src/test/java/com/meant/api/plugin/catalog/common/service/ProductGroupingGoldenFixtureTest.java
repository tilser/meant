package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.IdentityEvidenceStrength;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferComponentIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionOutcome;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecisionReason;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingResult;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityContradictionKind;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductGroupingGoldenFixtureTest {

    private static final Instant OBSERVED = Instant.parse("2026-07-11T10:00:00Z");
    private final ExactProductGroupingService service = new ExactProductGroupingService();

    @Test
    void duplicateObservationFromSameMerchantBecomesOneOfferWithMergedProvenance() {
        ProductCandidate first = fixture("UCP", "merchant-a", "product-a", "variant-a")
                .source(ResultSourceType.PROVIDER_CATALOG, "provider-catalog")
                .build();
        ProductCandidate second = fixture("UCP", "merchant-a", "product-a", "variant-a")
                .source(ResultSourceType.MERCHANT_STOREFRONT, "merchant-storefront")
                .observed(OBSERVED.plusSeconds(30))
                .build();

        assertThat(service.group(List.of(first, second))).singleElement().satisfies(product ->
                assertThat(product.offers()).singleElement().satisfies(offer ->
                        assertThat(offer.provenance()).hasSize(2)));
    }

    @Test
    void universalIdentifiersGroupCrossMerchantOffersAcrossGtinUpcAndEanForms() {
        ProductCandidate gtin = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                        "00012345678905", IdentityEvidenceStrength.TRUSTED_EXACT, 10_000))
                .build();
        ProductCandidate upc = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(universal(ProductIdentityEvidenceKind.UPC, ExternalIdentifierType.UPC,
                        "012345678905", IdentityEvidenceStrength.TRUSTED_EXACT, 10_000))
                .build();
        ProductCandidate ean = fixture("SOURCE_C", "merchant-c", "product-c", "variant-c")
                .evidence(universal(ProductIdentityEvidenceKind.EAN, ExternalIdentifierType.EAN,
                        "0012345678905", IdentityEvidenceStrength.TRUSTED_EXACT, 10_000))
                .build();

        assertThat(service.group(List.of(gtin, upc, ean))).singleElement()
                .satisfies(product -> assertThat(product.offers()).hasSize(3));
    }

    @Test
    void trustedUpidGroupsMultipleSellersAndVariantsWithoutCollapsingOffers() {
        ProductIdentityEvidence upid = evidence(
                ProductIdentityEvidenceKind.UPID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                10_000,
                "SHOPIFY_GLOBAL",
                id(ExternalIdentifierType.UPID, "SHOPIFY", "gid://shopify/p/redacted-upid")
        );
        ProductCandidate blue = fixture("SHOPIFY", "seller-a", "product-a", "variant-blue")
                .option("Color", "Blue").evidence(upid).build();
        ProductCandidate red = fixture("SHOPIFY", "seller-b", "product-b", "variant-red")
                .option("Color", "Red").evidence(upid).build();

        ProductGroupingResult result = service.evaluate(List.of(blue, red));

        assertThat(result.products()).singleElement()
                .satisfies(product -> assertThat(product.offers()).hasSize(2));
        assertThat(result.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(ProductGroupingDecisionOutcome.GROUPED);
            assertThat(decision.reason()).isEqualTo(ProductGroupingDecisionReason.TRUSTED_PROVIDER_GROUP);
            assertThat(decision.contradictions()).containsExactly(ProductIdentityContradictionKind.COLOR);
        });
    }

    @Test
    void normalizedVerifiedBrandAndModelGroupsCompatibleOffers() {
        ProductCandidate first = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(brandModel("Acme, Inc.", "Model-X 200"))
                .option("Size", "Medium")
                .build();
        ProductCandidate second = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(brandModel(" ACME INC ", "model x-200"))
                .option("Size", "Medium")
                .build();

        assertThat(service.evaluate(List.of(first, second)).products()).singleElement()
                .satisfies(product -> assertThat(product.offers()).hasSize(2));
    }

    @Test
    void trustedEligibleEvidenceWinsOverHigherPrecedenceAssertedEvidenceDeterministically() {
        ProductIdentityEvidence assertedGtin = universal(
                ProductIdentityEvidenceKind.GTIN,
                ExternalIdentifierType.GTIN,
                gtin14("7234567890123"),
                IdentityEvidenceStrength.ASSERTED,
                9_900
        );
        ProductIdentityEvidence trustedBrandModel = brandModel("Acme", "Model 200");
        ProductCandidate first = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(assertedGtin).evidence(trustedBrandModel).build();
        ProductCandidate second = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(trustedBrandModel).evidence(assertedGtin).build();

        ProductGroupingResult forward = service.evaluate(List.of(first, second));
        ProductGroupingResult reversed = service.evaluate(List.of(second, first));

        assertThat(forward).isEqualTo(reversed);
        assertThat(forward.products()).singleElement()
                .satisfies(product -> assertThat(product.offers()).hasSize(2));
        assertThat(forward.decisions()).singleElement().satisfies(decision ->
                assertThat(decision.reason()).isEqualTo(ProductGroupingDecisionReason.VERIFIED_BRAND_MODEL));
    }

    @Test
    void verifiedProviderMappingGroupsIndependentOffers() {
        ProductIdentityEvidence mapping = evidence(
                ProductIdentityEvidenceKind.PROVIDER_GROUPING_ID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                10_000,
                "verified-mapping",
                id(ExternalIdentifierType.PROVIDER_GROUPING, "MEANT_MAPPING", "mapping-redacted-1")
        );

        assertThat(service.group(List.of(
                fixture("SOURCE_A", "merchant-a", "product-a", "variant-a").evidence(mapping).build(),
                fixture("SOURCE_B", "merchant-b", "product-b", "variant-b").evidence(mapping).build()
        ))).singleElement().satisfies(product -> assertThat(product.offers()).hasSize(2));
    }

    @Test
    void verifiedOtherUniversalStandardGroupsOnlyInsideItsTypedNamespace() {
        ProductIdentityEvidence standard = evidence(
                ProductIdentityEvidenceKind.UNIVERSAL_PRODUCT_ID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                10_000,
                "verified-standard",
                id(ExternalIdentifierType.UNIVERSAL_PRODUCT_ID, "ISBN-13", "978-1-4028-9462-6")
        );

        assertThat(service.group(List.of(
                fixture("SOURCE_A", "merchant-a", "book-a", "edition-a").evidence(standard).build(),
                fixture("SOURCE_B", "merchant-b", "book-b", "edition-b").evidence(standard).build()
        ))).singleElement().satisfies(product -> assertThat(product.offers()).hasSize(2));
    }

    @Test
    void canonicalUrlGroupsOnlyWithinTheSameMerchantAndNormalizesTrackingConservatively() {
        ProductCandidate first = fixture("UCP", "merchant-a", "product-a", "variant-a")
                .evidence(canonicalUrl("http://SHOP.EXAMPLE/products/item/?utm_source=redacted"))
                .build();
        ProductCandidate duplicate = fixture("UCP", "merchant-a", "product-b", "variant-b")
                .evidence(canonicalUrl("https://shop.example/products/item"))
                .build();
        ProductCandidate otherMerchant = fixture("UCP", "merchant-b", "product-c", "variant-c")
                .evidence(canonicalUrl("https://shop.example/products/item"))
                .build();

        List<CanonicalProduct> products = service.group(List.of(first, duplicate, otherMerchant));

        assertThat(products).hasSize(2);
        assertThat(products).extracting(CanonicalProduct::key).doesNotHaveDuplicates();
        assertThat(products).filteredOn(product -> product.offers().size() == 2).singleElement();
        assertThat(products.stream().flatMap(product -> product.offers().stream())
                .map(offer -> offer.identity().merchantScope()).distinct()).hasSize(2);
    }

    @Test
    void sizeAndColorContradictionsVetoUniversalIdentifierMatch() {
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("1234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductCandidate first = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(gtin).option("Size", "Small").option("Color", "Blue").build();
        ProductCandidate second = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(gtin).option("Size", "Large").option("Color", "Red").build();

        ProductGroupingResult result = service.evaluate(List.of(first, second));

        assertThat(result.products()).hasSize(2);
        assertThat(result.products()).extracting(CanonicalProduct::key).doesNotHaveDuplicates();
        assertThat(result.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.reason()).isEqualTo(ProductGroupingDecisionReason.CONTRADICTION_VETO);
            assertThat(decision.contradictions())
                    .containsExactly(ProductIdentityContradictionKind.SIZE, ProductIdentityContradictionKind.COLOR);
        });
    }

    @Test
    void contradictionVetoedSiblingNeverRewritesAnIntrinsicCanonicalKey() {
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("1234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductCandidate blue = fixture("SOURCE_A", "merchant-a", "product-a", "variant-blue")
                .evidence(gtin).option("Color", "Blue").build();
        ProductCandidate red = fixture("SOURCE_B", "merchant-b", "product-b", "variant-red")
                .evidence(gtin).option("Color", "Red").build();

        String singletonKey = service.evaluate(List.of(blue)).products().getFirst().key();
        ProductGroupingResult withSibling = service.evaluate(List.of(blue, red));
        ProductGroupingResult reversed = service.evaluate(List.of(red, blue));

        assertThat(withSibling).isEqualTo(reversed);
        assertThat(withSibling.products()).hasSize(2).extracting(CanonicalProduct::key).doesNotHaveDuplicates();
        assertThat(withSibling.products()).filteredOn(product -> product.offers().contains(blue.offer()))
                .singleElement().extracting(CanonicalProduct::key).isEqualTo(singletonKey);
        assertThat(service.evaluate(List.of(blue)).products().getFirst().key()).isEqualTo(singletonKey);
    }

    @Test
    void partialSignalCoverageUsesEveryStableClusterMemberIdentity() {
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("9234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductIdentityEvidence brandModel = brandModel("Acme", "Model 900");
        ProductCandidate first = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(gtin).build();
        ProductCandidate bridge = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(gtin).evidence(brandModel).build();
        ProductCandidate third = fixture("SOURCE_C", "merchant-c", "product-c", "variant-c")
                .evidence(brandModel).build();

        ProductGroupingResult result = service.evaluate(List.of(first, bridge, third));

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.offers()).hasSize(3);
            assertThat(product.key()).isEqualTo(CanonicalCommerceKey.clusteredProductKey(List.of(
                    first.fallbackProductKey(),
                    bridge.fallbackProductKey(),
                    third.fallbackProductKey()
            )));
            assertThat(product.key()).isNotEqualTo(service.group(List.of(first, bridge)).getFirst().key());
        });
        assertThat(result).isEqualTo(service.evaluate(List.of(third, bridge, first)));
    }

    @Test
    void bundleAndPackContradictionsRemainSeparate() {
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("2234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductCandidate bundle = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(gtin).component("part-a", 2).attribute("Pack quantity", "2").build();
        ProductCandidate single = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(gtin).attribute("Pack quantity", "1").build();

        ProductGroupingResult result = service.evaluate(List.of(bundle, single));

        assertThat(result.products()).hasSize(2);
        assertThat(result.decisions()).singleElement().satisfies(decision ->
                assertThat(decision.contradictions()).containsExactly(
                        ProductIdentityContradictionKind.BUNDLE,
                        ProductIdentityContradictionKind.PACK_QUANTITY
                ));
    }

    @Test
    void modelAndGenerationContradictionsRemainSeparate() {
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("3234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductCandidate first = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .evidence(gtin).attribute("Model", "X1").attribute("Generation", "2").build();
        ProductCandidate second = fixture("SOURCE_B", "merchant-b", "product-b", "variant-b")
                .evidence(gtin).attribute("Model", "X2").attribute("Generation", "3").build();

        assertThat(service.evaluate(List.of(first, second)).decisions()).singleElement().satisfies(decision ->
                assertThat(decision.contradictions()).containsExactly(
                        ProductIdentityContradictionKind.GENERATION,
                        ProductIdentityContradictionKind.MODEL
                ));
    }

    @Test
    void sellingPlansRemainDistinctOffersInsideAnAuthoritativeProduct() {
        ProductIdentityEvidence upid = evidence(
                ProductIdentityEvidenceKind.UPID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                10_000,
                "SHOPIFY_GLOBAL",
                id(ExternalIdentifierType.UPID, "SHOPIFY", "gid://shopify/p/plan-upid")
        );
        ProductCandidate monthly = fixture("SHOPIFY", "seller-a", "product-a", "variant-a")
                .evidence(upid).sellingPlan("monthly").build();
        ProductCandidate yearly = fixture("SHOPIFY", "seller-a", "product-a", "variant-a")
                .evidence(upid).sellingPlan("yearly").build();

        assertThat(service.group(List.of(monthly, yearly))).singleElement().satisfies(product -> {
            assertThat(product.offers()).hasSize(2);
            assertThat(product.offers()).extracting(Offer::key).doesNotHaveDuplicates();
            assertThat(product.offers()).extracting(Offer::sellingPlanIdentity).doesNotContainNull();
        });
    }

    @Test
    void semanticAndLowConfidenceEvidenceRemainSeparateAndMeasurable() {
        ProductIdentityEvidence semantic = evidence(
                ProductIdentityEvidenceKind.SEMANTIC,
                IdentityEvidenceStrength.SEMANTIC,
                9_900,
                "measured-similarity-v1",
                id(ExternalIdentifierType.SEMANTIC_FINGERPRINT, "MEASURED", "redacted-fingerprint")
        );
        ProductIdentityEvidence assertedGtin = universal(
                ProductIdentityEvidenceKind.GTIN,
                ExternalIdentifierType.GTIN,
                gtin14("4234567890123"),
                IdentityEvidenceStrength.ASSERTED,
                7_000
        );
        ProductGroupingResult semanticResult = service.evaluate(List.of(
                fixture("SOURCE_A", "merchant-a", "semantic-a", "variant-a").evidence(semantic).build(),
                fixture("SOURCE_B", "merchant-b", "semantic-b", "variant-b").evidence(semantic).build()
        ));
        ProductGroupingResult lowConfidenceResult = service.evaluate(List.of(
                fixture("SOURCE_A", "merchant-a", "asserted-a", "variant-a").evidence(assertedGtin).build(),
                fixture("SOURCE_B", "merchant-b", "asserted-b", "variant-b").evidence(assertedGtin).build()
        ));

        assertThat(semanticResult.products()).hasSize(2);
        assertThat(semanticResult.decisions()).singleElement()
                .extracting(decision -> decision.reason())
                .isEqualTo(ProductGroupingDecisionReason.SEMANTIC_EVIDENCE_ONLY);
        assertThat(lowConfidenceResult.products()).hasSize(2);
        assertThat(lowConfidenceResult.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.reason()).isEqualTo(ProductGroupingDecisionReason.LOW_CONFIDENCE_EVIDENCE);
            assertThat(decision.confidenceBasisPoints()).isEqualTo(7_000);
        });
    }

    @Test
    void identicalTitlesAndFreeTextAttributesNeverCreateIdentityEvidence() {
        ProductCandidate first = fixture("SOURCE_A", "merchant-a", "title-a", "variant-a")
                .title("Ambiguous redacted product")
                .attribute("Material", "Cotton")
                .build();
        ProductCandidate second = fixture("SOURCE_B", "merchant-b", "title-b", "variant-b")
                .title("Ambiguous redacted product")
                .attribute("Material", "Cotton")
                .build();

        assertThat(service.evaluate(List.of(first, second))).satisfies(result -> {
            assertThat(result.products()).hasSize(2);
            assertThat(result.decisions()).isEmpty();
        });
    }

    @Test
    void invalidGs1CheckDigitsAndRepeatedPlaceholdersNeverBecomeTrustedMatches() {
        ProductIdentityEvidence valid = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("8234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductIdentityEvidence invalidCheckDigit = universal(
                ProductIdentityEvidenceKind.GTIN,
                ExternalIdentifierType.GTIN,
                invalidCheckDigit(gtin14("8234567890123")),
                IdentityEvidenceStrength.TRUSTED_EXACT,
                10_000
        );
        ProductIdentityEvidence placeholder = universal(ProductIdentityEvidenceKind.UPC, ExternalIdentifierType.UPC,
                "000000000000", IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);

        assertThat(service.group(List.of(
                fixture("SOURCE_A", "merchant-a", "valid-a", "variant-a").evidence(valid).build(),
                fixture("SOURCE_B", "merchant-b", "valid-b", "variant-b").evidence(valid).build()
        ))).singleElement();
        assertThat(service.evaluate(List.of(
                fixture("SOURCE_A", "merchant-a", "invalid-a", "variant-a").evidence(invalidCheckDigit).build(),
                fixture("SOURCE_B", "merchant-b", "invalid-b", "variant-b").evidence(invalidCheckDigit).build(),
                fixture("SOURCE_C", "merchant-c", "placeholder-a", "variant-c").evidence(placeholder).build(),
                fixture("SOURCE_D", "merchant-d", "placeholder-b", "variant-d").evidence(placeholder).build()
        ))).satisfies(result -> {
            assertThat(result.products()).hasSize(4);
            assertThat(result.decisions()).isEmpty();
        });
    }

    @Test
    void inputPermutationProducesIdenticalProductsOffersKeysAndDecisions() {
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("5234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        List<ProductCandidate> candidates = List.of(
                fixture("SOURCE_A", "merchant-a", "product-a", "variant-a").evidence(gtin).build(),
                fixture("SOURCE_B", "merchant-b", "product-b", "variant-b").evidence(gtin).build(),
                fixture("SOURCE_C", "merchant-c", "product-c", "variant-c").build()
        );
        List<ProductCandidate> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);

        assertThat(service.evaluate(candidates)).isEqualTo(service.evaluate(reversed));
    }

    @Test
    void preferredDisplayObservationNeverMutatesSelectedOfferIdentityOrRouting() {
        UUID routing = UUID.fromString("00000000-0000-0000-0000-000000000808");
        ProductIdentityEvidence gtin = universal(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN,
                gtin14("6234567890123"), IdentityEvidenceStrength.TRUSTED_EXACT, 10_000);
        ProductCandidate selected = fixture("SOURCE_A", "merchant-a", "product-a", "variant-a")
                .title("Selected source title")
                .evidence(gtin)
                .option("Color", "Blue")
                .component("part-a", 1)
                .sellingPlan("monthly")
                .routing(routing)
                .build();
        ProductCandidate preferredDisplay = fixture("SOURCE_A", "merchant-b", "product-b", "variant-b")
                .title("Preferred display title")
                .evidence(gtin)
                .option("Color", "Blue")
                .component("part-a", 1)
                .sellingPlan("monthly")
                .observed(OBSERVED.plusSeconds(60))
                .build();
        OfferIdentity selectedIdentity = selected.offer().identity();

        CanonicalProduct product = service.group(List.of(selected, preferredDisplay)).getFirst();

        assertThat(product.title()).isEqualTo("Preferred display title");
        assertThat(product.offers()).filteredOn(offer -> offer.key().equals(selected.offer().key()))
                .singleElement().satisfies(offer -> {
                    assertThat(offer.identity()).isEqualTo(selectedIdentity);
                    assertThat(offer.identity().selectedOptions()).isEqualTo(selectedIdentity.selectedOptions());
                    assertThat(offer.identity().components()).isEqualTo(selectedIdentity.components());
                    assertThat(offer.identity().sellingPlanIdentity()).isEqualTo(selectedIdentity.sellingPlanIdentity());
                    assertThat(offer.provenance()).singleElement().satisfies(provenance ->
                            assertThat(provenance.localRouting().merchantIntegrationId()).isEqualTo(routing));
                });
    }

    @Test
    void priceAndAvailabilityChangesDoNotChangeIdentityAndNewestObservationWinsDisplay() {
        ProductCandidate oldObservation = fixture("UCP", "merchant-a", "product-a", "variant-a")
                .price(2_000).available(false).build();
        ProductCandidate newObservation = fixture("UCP", "merchant-a", "product-a", "variant-a")
                .price(1_500).available(true).observed(OBSERVED.plusSeconds(60)).build();

        assertThat(oldObservation.offer().key()).isEqualTo(newObservation.offer().key());
        assertThat(service.group(List.of(oldObservation, newObservation))).singleElement().satisfies(product ->
                assertThat(product.offers()).singleElement().satisfies(offer -> {
                    assertThat(offer.price()).isEqualTo(new Money(1_500, "USD"));
                    assertThat(offer.availability().status()).isEqualTo(OfferAvailabilityStatus.IN_STOCK);
                    assertThat(offer.provenance()).hasSize(2);
                }));
    }

    private Fixture fixture(String provider, String merchant, String product, String variant) {
        return new Fixture(provider, merchant, product, variant);
    }

    private ProductIdentityEvidence universal(
            ProductIdentityEvidenceKind kind,
            ExternalIdentifierType type,
            String value,
            IdentityEvidenceStrength strength,
            int confidence
    ) {
        return evidence(kind, strength, confidence, "verified-standard",
                id(type, null, value));
    }

    private ProductIdentityEvidence brandModel(String brand, String model) {
        return evidence(
                ProductIdentityEvidenceKind.BRAND_MPN,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                9_800,
                "verified-brand-model",
                id(ExternalIdentifierType.BRAND, null, brand),
                id(ExternalIdentifierType.MPN, null, model)
        );
    }

    private ProductIdentityEvidence canonicalUrl(String url) {
        return evidence(
                ProductIdentityEvidenceKind.CANONICAL_URL,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                9_500,
                "merchant-canonical-url",
                id(ExternalIdentifierType.CANONICAL_URL, null, url)
        );
    }

    private ProductIdentityEvidence evidence(
            ProductIdentityEvidenceKind kind,
            IdentityEvidenceStrength strength,
            int confidence,
            String source,
            ExternalIdentifier... identifiers
    ) {
        return new ProductIdentityEvidence(
                kind,
                strength,
                confidence,
                List.of(identifiers),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, source, null)
        );
    }

    private ExternalIdentifier id(ExternalIdentifierType type, String namespace, String value) {
        return new ExternalIdentifier(type, namespace, value);
    }

    private String gtin14(String thirteenDigits) {
        int sum = 0;
        boolean triple = true;
        for (int index = thirteenDigits.length() - 1; index >= 0; index--) {
            sum += (thirteenDigits.charAt(index) - '0') * (triple ? 3 : 1);
            triple = !triple;
        }
        return thirteenDigits + (10 - sum % 10) % 10;
    }

    private String invalidCheckDigit(String valid) {
        int last = valid.charAt(valid.length() - 1) - '0';
        return valid.substring(0, valid.length() - 1) + (last + 1) % 10;
    }

    private static final class Fixture {

        private final ProviderIdentity provider;
        private final String merchant;
        private final String product;
        private final String variant;
        private String title;
        private final List<ProductIdentityEvidence> evidence = new ArrayList<>();
        private final List<ProductAttribute> attributes = new ArrayList<>();
        private final List<ProductAttribute> options = new ArrayList<>();
        private final List<OfferComponentIdentity> components = new ArrayList<>();
        private SellingPlanIdentity sellingPlan;
        private ResultSourceType sourceType = ResultSourceType.PROVIDER_CATALOG;
        private String source = "fixture-catalog";
        private LocalMerchantRouting routing;
        private Instant observed = OBSERVED;
        private long price = 1_000;
        private boolean available = true;

        private Fixture(String provider, String merchant, String product, String variant) {
            this.provider = new ProviderIdentity(provider);
            this.merchant = merchant;
            this.product = product;
            this.variant = variant;
            this.title = "Product " + product;
        }

        private Fixture title(String value) {
            title = value;
            return this;
        }

        private Fixture evidence(ProductIdentityEvidence value) {
            evidence.add(value);
            return this;
        }

        private Fixture attribute(String name, String value) {
            attributes.add(new ProductAttribute(null, name, value));
            return this;
        }

        private Fixture option(String name, String value) {
            options.add(new ProductAttribute("variant-option", name, value));
            return this;
        }

        private Fixture component(String componentProduct, int quantity) {
            components.add(new OfferComponentIdentity(
                    idFor(ExternalIdentifierType.PRODUCT, componentProduct),
                    null,
                    quantity,
                    List.of()
            ));
            return this;
        }

        private Fixture sellingPlan(String plan) {
            sellingPlan = new SellingPlanIdentity(
                    idFor(ExternalIdentifierType.SELLING_PLAN_GROUP, "subscriptions"),
                    idFor(ExternalIdentifierType.SELLING_PLAN, plan),
                    List.of(new SellingPlanOption("frequency", plan))
            );
            return this;
        }

        private Fixture source(ResultSourceType type, String value) {
            sourceType = type;
            source = value;
            return this;
        }

        private Fixture routing(UUID value) {
            routing = new LocalMerchantRouting(value);
            return this;
        }

        private Fixture observed(Instant value) {
            observed = value;
            return this;
        }

        private Fixture price(long value) {
            price = value;
            return this;
        }

        private Fixture available(boolean value) {
            available = value;
            return this;
        }

        private ProductCandidate build() {
            ExternalIdentifier merchantId = idFor(ExternalIdentifierType.MERCHANT, merchant);
            ExternalIdentifier productId = idFor(ExternalIdentifierType.PRODUCT, product);
            ExternalIdentifier variantId = idFor(ExternalIdentifierType.VARIANT, variant);
            ResultSourceReference sourceReference = new ResultSourceReference(
                    sourceType,
                    source + ":" + merchant + ":" + product,
                    URI.create("https://source.example/" + source)
            );
            ResultProvenance provenance = new ResultProvenance(
                    provider,
                    new DiscoverySourceIdentity(provider, sourceType, source),
                    routing,
                    merchantId,
                    productId,
                    variantId,
                    new ResultFreshness(observed, observed.plusSeconds(300)),
                    sourceReference
            );
            Offer offer = new Offer(
                    new OfferIdentity(
                            provider,
                            OfferMerchantScope.external(merchantId),
                            productId,
                            variantId,
                            options,
                            components,
                            sellingPlan
                    ),
                    merchant,
                    variant,
                    new Money(price, "USD"),
                    null,
                    new OfferAvailability(
                            available ? OfferAvailabilityStatus.IN_STOCK : OfferAvailabilityStatus.OUT_OF_STOCK,
                            null,
                            null
                    ),
                    List.of(),
                    URI.create("https://checkout.example/" + merchant + "/" + variant),
                    List.of(provenance)
            );
            return new ProductCandidate(
                    title,
                    "Redacted deterministic fixture",
                    List.of(),
                    attributes,
                    List.of(),
                    List.of(),
                    List.of(),
                    evidence,
                    List.of(provenance),
                    offer
            );
        }

        private ExternalIdentifier idFor(ExternalIdentifierType type, String value) {
            return new ExternalIdentifier(type, provider.value(), value);
        }
    }
}
