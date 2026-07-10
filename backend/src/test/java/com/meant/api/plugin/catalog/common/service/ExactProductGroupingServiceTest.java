package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExactProductGroupingServiceTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-07-10T10:00:00Z");
    private final ExactProductGroupingService service = new ExactProductGroupingService();

    @Test
    void acceptsGlobalCatalogOfferForUnknownSellerWithoutLocalMerchantRouting() {
        ProductCandidate globalOffer = externalCandidate(
                "SHOPIFY",
                "gid://shopify/Shop/42",
                "gid://shopify/Product/10",
                "gid://shopify/ProductVariant/100",
                List.of(),
                List.of(),
                null,
                null,
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG",
                null,
                OBSERVED_AT
        );

        CanonicalProduct grouped = service.group(List.of(globalOffer)).getFirst();

        assertThat(grouped.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.key()).startsWith("offer_v2_");
            assertThat(offer.identity().merchantScope().externalMerchantIdentity().value())
                    .isEqualTo("gid://shopify/Shop/42");
            assertThat(offer.provenance()).singleElement().satisfies(provenance -> {
                assertThat(provenance.discoverySource().value()).isEqualTo("SHOPIFY_GLOBAL_CATALOG");
                assertThat(provenance.localRouting()).isNull();
            });
        });
    }

    @Test
    void mergesGlobalAndStorefrontObservationsWithoutChangingExternalSellerOfferKey() {
        UUID localIntegrationId = id(1);
        SellingPlanIdentity monthly = sellingPlan("monthly");
        ProductIdentityEvidence upid = evidence(
                ProductIdentityEvidenceKind.UPID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                ExternalIdentifierType.UPID,
                "gid://shopify/p/upid-1",
                "global-catalog"
        );
        ProductCandidate global = externalCandidate(
                "SHOPIFY", "gid://shopify/Shop/42", "gid://shopify/Product/10",
                "gid://shopify/ProductVariant/100", List.of(), List.of(), monthly, upid,
                ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG", null, OBSERVED_AT);
        ProductCandidate storefront = externalCandidate(
                "SHOPIFY", "gid://shopify/Shop/42", "gid://shopify/Product/10",
                "gid://shopify/ProductVariant/100", List.of(), List.of(), monthly, upid,
                ResultSourceType.MERCHANT_STOREFRONT, "gid://shopify/Shop/42",
                localIntegrationId, OBSERVED_AT.plusSeconds(1));

        assertThat(global.offer().key()).isEqualTo(storefront.offer().key());
        CanonicalProduct grouped = service.group(List.of(global, storefront)).getFirst();

        assertThat(grouped.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.key()).isEqualTo(global.offer().key());
            assertThat(offer.provenance()).hasSize(2);
            assertThat(offer.provenance())
                    .extracting(value -> value.discoverySource().type())
                    .containsExactly(ResultSourceType.MERCHANT_STOREFRONT, ResultSourceType.PROVIDER_CATALOG);
            assertThat(offer.provenance())
                    .filteredOn(value -> value.localRouting() != null)
                    .singleElement()
                    .extracting(value -> value.localRouting().merchantIntegrationId())
                    .isEqualTo(localIntegrationId);
        });
    }

    @Test
    void localIntegrationFallbackSupportsGenericUcpMerchantsAndRemainsCollisionSafe() {
        ProductCandidate first = localFallbackCandidate(id(10), "product", "variant");
        ProductCandidate second = localFallbackCandidate(id(11), "product", "variant");

        assertThat(first.offer().identity().merchantScope().externalMerchantIdentity()).isNull();
        assertThat(first.offer().key()).isNotEqualTo(second.offer().key());
        assertThat(service.group(List.of(first, second))).hasSize(2);
    }

    @Test
    void differentExternalSellersRemainDistinctOffersInsideOneTrustedProduct() {
        ProductIdentityEvidence upid = evidence(
                ProductIdentityEvidenceKind.UPID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                ExternalIdentifierType.UPID,
                "gid://shopify/p/upid-2",
                "global-catalog"
        );
        ProductCandidate first = externalCandidate(
                "SHOPIFY", "gid://shopify/Shop/1", "shared-product", "shared-variant",
                List.of(), List.of(), null, upid, ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG", null, OBSERVED_AT);
        ProductCandidate second = externalCandidate(
                "SHOPIFY", "gid://shopify/Shop/2", "shared-product", "shared-variant",
                List.of(), List.of(), null, upid, ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG", null, OBSERVED_AT);

        assertThat(service.group(List.of(first, second))).singleElement()
                .satisfies(product -> assertThat(product.offers()).hasSize(2));
    }

    @Test
    void variantSelectedOptionComponentAndSellingPlanDifferencesRemainDistinctOffers() {
        ProductIdentityEvidence upid = evidence(
                ProductIdentityEvidenceKind.UPID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                ExternalIdentifierType.UPID,
                "gid://shopify/p/upid-3",
                "global-catalog"
        );
        ProductAttribute blue = new ProductAttribute(null, "Color", "Blue");
        ProductAttribute red = new ProductAttribute(null, "Color", "Red");
        OfferComponentIdentity belt = component("belt", "brown-belt", 1);
        OfferComponentIdentity hat = component("hat", "blue-hat", 1);

        CanonicalProduct product = service.group(List.of(
                externalCandidate("SHOPIFY", "shop", "product", "variant-1", List.of(blue),
                        List.of(belt), sellingPlan("monthly"), upid, ResultSourceType.PROVIDER_CATALOG,
                        "global", null, OBSERVED_AT),
                externalCandidate("SHOPIFY", "shop", "product", "variant-2", List.of(blue),
                        List.of(belt), sellingPlan("monthly"), upid, ResultSourceType.PROVIDER_CATALOG,
                        "global", null, OBSERVED_AT),
                externalCandidate("SHOPIFY", "shop", "product", "variant-1", List.of(red),
                        List.of(belt), sellingPlan("monthly"), upid, ResultSourceType.PROVIDER_CATALOG,
                        "global", null, OBSERVED_AT),
                externalCandidate("SHOPIFY", "shop", "product", "variant-1", List.of(blue),
                        List.of(hat), sellingPlan("monthly"), upid, ResultSourceType.PROVIDER_CATALOG,
                        "global", null, OBSERVED_AT),
                externalCandidate("SHOPIFY", "shop", "product", "variant-1", List.of(blue),
                        List.of(belt), sellingPlan("yearly"), upid, ResultSourceType.PROVIDER_CATALOG,
                        "global", null, OBSERVED_AT)
        )).getFirst();

        assertThat(product.offers()).hasSize(5);
        assertThat(product.offers()).extracting(Offer::key).doesNotHaveDuplicates();
    }

    @Test
    void semanticEvidenceDoesNotCollapseProductsAndOutputDoesNotDependOnInputOrder() {
        ProductIdentityEvidence semantic = evidence(
                ProductIdentityEvidenceKind.SEMANTIC,
                IdentityEvidenceStrength.SEMANTIC,
                ExternalIdentifierType.SEMANTIC_FINGERPRINT,
                "same-looking-product",
                "semantic-model-v1"
        );
        ProductCandidate first = externalCandidate(
                "GENERIC_UCP", "merchant-a", "product-a", null, List.of(), List.of(), null,
                semantic, ResultSourceType.MERCHANT_STOREFRONT, "merchant-a", id(20), OBSERVED_AT);
        ProductCandidate second = externalCandidate(
                "FUTURE_PROVIDER", "merchant-b", "product-b", null, List.of(), List.of(), null,
                semantic, ResultSourceType.MERCHANT_STOREFRONT, "merchant-b", id(21), OBSERVED_AT);

        List<CanonicalProduct> forward = service.group(List.of(first, second));
        List<CanonicalProduct> reversed = service.group(List.of(second, first));

        assertThat(forward).hasSize(2).isEqualTo(reversed);
        assertThat(forward).extracting(CanonicalProduct::key).doesNotHaveDuplicates();
    }

    @Test
    void contractCollectionsAreDefensivelyCopiedAndGroupedOutputsAreImmutable() {
        List<ProductIdentityEvidence> mutableEvidence = new ArrayList<>();
        ProductCandidate candidate = externalCandidate(
                "GENERIC_UCP", "merchant", "product", null, List.of(), List.of(), null,
                null, ResultSourceType.MERCHANT_STOREFRONT, "merchant", id(30), OBSERVED_AT,
                mutableEvidence);

        mutableEvidence.add(evidence(
                ProductIdentityEvidenceKind.GTIN,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                ExternalIdentifierType.GTIN,
                "99999999999999",
                "late-mutation"
        ));
        List<CanonicalProduct> grouped = service.group(List.of(candidate));

        assertThat(candidate.identityEvidence()).isEmpty();
        assertThatThrownBy(() -> grouped.add(grouped.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> grouped.getFirst().offers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ProductCandidate externalCandidate(
            String providerValue,
            String merchant,
            String product,
            String variant,
            List<ProductAttribute> selectedOptions,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlan,
            ProductIdentityEvidence evidence,
            ResultSourceType sourceType,
            String sourceIdentity,
            UUID localRoutingId,
            Instant observedAt
    ) {
        return externalCandidate(
                providerValue, merchant, product, variant, selectedOptions, components, sellingPlan,
                evidence, sourceType, sourceIdentity, localRoutingId, observedAt,
                evidence == null ? List.of() : List.of(evidence));
    }

    private ProductCandidate externalCandidate(
            String providerValue,
            String merchant,
            String product,
            String variant,
            List<ProductAttribute> selectedOptions,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlan,
            ProductIdentityEvidence evidence,
            ResultSourceType sourceType,
            String sourceIdentity,
            UUID localRoutingId,
            Instant observedAt,
            List<ProductIdentityEvidence> evidenceValues
    ) {
        ProviderIdentity provider = new ProviderIdentity(providerValue);
        ExternalIdentifier merchantIdentity = identifier(ExternalIdentifierType.MERCHANT, provider, merchant);
        return candidate(
                provider,
                OfferMerchantScope.external(merchantIdentity),
                merchantIdentity,
                product,
                variant,
                selectedOptions,
                components,
                sellingPlan,
                evidenceValues,
                sourceType,
                sourceIdentity,
                localRoutingId,
                observedAt
        );
    }

    private ProductCandidate localFallbackCandidate(UUID integrationId, String product, String variant) {
        ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
        return candidate(
                provider,
                OfferMerchantScope.localIntegrationFallback(integrationId),
                null,
                product,
                variant,
                List.of(),
                List.of(),
                null,
                List.of(),
                ResultSourceType.MERCHANT_STOREFRONT,
                "LOCAL_STOREFRONT:" + integrationId,
                integrationId,
                OBSERVED_AT
        );
    }

    private ProductCandidate candidate(
            ProviderIdentity provider,
            OfferMerchantScope merchantScope,
            ExternalIdentifier externalMerchantReference,
            String product,
            String variant,
            List<ProductAttribute> selectedOptions,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlan,
            List<ProductIdentityEvidence> evidenceValues,
            ResultSourceType sourceType,
            String sourceIdentity,
            UUID localRoutingId,
            Instant observedAt
    ) {
        ExternalIdentifier productIdentity = identifier(ExternalIdentifierType.PRODUCT, provider, product);
        ExternalIdentifier variantIdentity = ExternalIdentifier.optional(
                ExternalIdentifierType.VARIANT, provider.value(), variant);
        ResultSourceReference sourceReference = new ResultSourceReference(
                sourceType,
                sourceIdentity + ":" + product,
                URI.create("https://catalog.example/" + product)
        );
        ResultProvenance provenance = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(provider, sourceType, sourceIdentity),
                localRoutingId == null ? null : new LocalMerchantRouting(localRoutingId),
                externalMerchantReference,
                productIdentity,
                variantIdentity,
                new ResultFreshness(observedAt, observedAt.plusSeconds(300)),
                sourceReference
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        provider,
                        merchantScope,
                        productIdentity,
                        variantIdentity,
                        selectedOptions,
                        components,
                        sellingPlan
                ),
                externalMerchantReference == null ? "Generic merchant" : externalMerchantReference.value(),
                variant,
                new Money(2999, "usd"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                List.of(),
                URI.create("https://checkout.example/" + product),
                List.of(provenance)
        );
        return new ProductCandidate(
                "Stable shared title",
                "Description copy is not part of canonical identity",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProductAttribution("Catalog", null, sourceReference)),
                evidenceValues,
                List.of(provenance),
                offer
        );
    }

    private ProductIdentityEvidence evidence(
            ProductIdentityEvidenceKind kind,
            IdentityEvidenceStrength strength,
            ExternalIdentifierType identifierType,
            String value,
            String source
    ) {
        ProviderIdentity provider = new ProviderIdentity("EVIDENCE");
        return new ProductIdentityEvidence(
                kind,
                strength,
                strength == IdentityEvidenceStrength.SEMANTIC ? 8_500 : 10_000,
                List.of(identifier(identifierType, provider, value)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, source, null)
        );
    }

    private OfferComponentIdentity component(String product, String variant, int quantity) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        return new OfferComponentIdentity(
                identifier(ExternalIdentifierType.PRODUCT, provider, product),
                identifier(ExternalIdentifierType.VARIANT, provider, variant),
                quantity,
                List.of()
        );
    }

    private SellingPlanIdentity sellingPlan(String value) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        return new SellingPlanIdentity(
                identifier(ExternalIdentifierType.SELLING_PLAN_GROUP, provider, "subscriptions"),
                identifier(ExternalIdentifierType.SELLING_PLAN, provider, value),
                Collections.singletonList(new SellingPlanOption("frequency", value))
        );
    }

    private ExternalIdentifier identifier(
            ExternalIdentifierType type,
            ProviderIdentity provider,
            String value
    ) {
        return new ExternalIdentifier(type, provider.value(), value);
    }

    private UUID id(int value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }
}
