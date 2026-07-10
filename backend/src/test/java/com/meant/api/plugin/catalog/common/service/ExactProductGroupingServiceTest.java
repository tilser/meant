package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.IdentityEvidenceStrength;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
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
    void groupsOneTrustedProductAcrossThreeMerchantsAndPreservesDuplicateOfferObservations() {
        ProductIdentityEvidence gtin = evidence(
                ProductIdentityEvidenceKind.GTIN,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                ExternalIdentifierType.GTIN,
                "00012345678905",
                "manufacturer-feed"
        );
        UUID firstIntegration = id(1);
        List<ProductCandidate> candidates = List.of(
                candidate("SHOPIFY", firstIntegration, "merchant-1", "gid://shopify/Product/10", "variant-1", null,
                        gtin, "merchant-search", OBSERVED_AT),
                candidate("SHOPIFY", firstIntegration, "merchant-1", "gid://shopify/Product/10", "variant-1", null,
                        gtin, "provider-catalog", OBSERVED_AT.plusSeconds(1)),
                candidate("GENERIC_UCP", id(2), "merchant-2", "product-20", "variant-2", null,
                        gtin, "merchant-search", OBSERVED_AT),
                candidate("FUTURE_PROVIDER", id(3), "merchant-3", "product-30", "variant-3", null,
                        gtin, "merchant-search", OBSERVED_AT)
        );

        List<CanonicalProduct> products = service.group(candidates);
        List<ProductCandidate> reversedCandidates = new ArrayList<>(candidates);
        Collections.reverse(reversedCandidates);

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.key()).startsWith("product_v1_");
            assertThat(product.offers()).hasSize(3);
            assertThat(product.provenance()).hasSize(4);
            assertThat(product.offers())
                    .filteredOn(offer -> offer.identity().merchantIntegrationId().equals(firstIntegration))
                    .singleElement()
                    .satisfies(offer -> assertThat(offer.provenance()).hasSize(2));
        });
        assertThat(service.group(reversedCandidates)).isEqualTo(products);
    }

    @Test
    void variantAndSellingPlanContextRemainDistinctOffers() {
        UUID integration = id(10);
        ProductIdentityEvidence upid = evidence(
                ProductIdentityEvidenceKind.UPID,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                ExternalIdentifierType.UPID,
                "upid:example:product-1",
                "provider-catalog"
        );
        SellingPlanIdentity monthly = sellingPlan("monthly");
        SellingPlanIdentity yearly = sellingPlan("yearly");

        CanonicalProduct product = service.group(List.of(
                candidate("SHOPIFY", integration, "gid://shopify/Shop/1", "gid://shopify/Product/1",
                        "gid://shopify/ProductVariant/1", monthly, upid, "source-1", OBSERVED_AT),
                candidate("SHOPIFY", integration, "gid://shopify/Shop/1", "gid://shopify/Product/1",
                        "gid://shopify/ProductVariant/2", monthly, upid, "source-2", OBSERVED_AT),
                candidate("SHOPIFY", integration, "gid://shopify/Shop/1", "gid://shopify/Product/1",
                        "gid://shopify/ProductVariant/1", yearly, upid, "source-3", OBSERVED_AT)
        )).getFirst();

        assertThat(product.offers()).hasSize(3);
        assertThat(product.identityEvidence().getFirst().identifiers().getFirst().type())
                .isEqualTo(ExternalIdentifierType.UPID);
        assertThat(product.offers().getFirst().identity().externalProductIdentity().value())
                .startsWith("gid://shopify/");
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
        ProductCandidate first = candidate(
                "GENERIC_UCP", id(20), "merchant-a", "product-a", null, null,
                semantic, "source-a", OBSERVED_AT);
        ProductCandidate second = candidate(
                "FUTURE_PROVIDER", id(21), "merchant-b", "product-b", null, null,
                semantic, "source-b", OBSERVED_AT);

        List<CanonicalProduct> forward = service.group(List.of(first, second));
        List<CanonicalProduct> reversed = service.group(List.of(second, first));

        assertThat(forward).hasSize(2).isEqualTo(reversed);
        assertThat(forward).extracting(CanonicalProduct::key).doesNotHaveDuplicates();
    }

    @Test
    void contractCollectionsAreDefensivelyCopiedAndGroupedOutputsAreImmutable() {
        List<ProductIdentityEvidence> mutableEvidence = new ArrayList<>();
        ProductCandidate candidate = candidate(
                "GENERIC_UCP", id(30), "merchant", "product", null, null,
                null, "source", OBSERVED_AT, mutableEvidence);

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

    private ProductCandidate candidate(
            String providerValue,
            UUID integrationId,
            String merchant,
            String product,
            String variant,
            SellingPlanIdentity sellingPlan,
            ProductIdentityEvidence evidence,
            String source,
            Instant observedAt
    ) {
        return candidate(
                providerValue,
                integrationId,
                merchant,
                product,
                variant,
                sellingPlan,
                evidence,
                source,
                observedAt,
                evidence == null ? List.of() : List.of(evidence)
        );
    }

    private ProductCandidate candidate(
            String providerValue,
            UUID integrationId,
            String merchant,
            String product,
            String variant,
            SellingPlanIdentity sellingPlan,
            ProductIdentityEvidence evidence,
            String source,
            Instant observedAt,
            List<ProductIdentityEvidence> evidenceValues
    ) {
        ProviderIdentity provider = new ProviderIdentity(providerValue);
        ExternalIdentifier merchantIdentity = identifier(ExternalIdentifierType.MERCHANT, provider, merchant);
        ExternalIdentifier productIdentity = identifier(ExternalIdentifierType.PRODUCT, provider, product);
        ExternalIdentifier variantIdentity = ExternalIdentifier.optional(
                ExternalIdentifierType.VARIANT, provider.value(), variant);
        ResultSourceReference sourceReference = new ResultSourceReference(
                ResultSourceType.PROVIDER_CATALOG,
                source,
                URI.create("https://catalog.example/" + source)
        );
        ResultProvenance provenance = new ResultProvenance(
                provider,
                integrationId,
                merchantIdentity,
                productIdentity,
                variantIdentity,
                new ResultFreshness(observedAt, observedAt.plusSeconds(300)),
                sourceReference
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        provider,
                        integrationId,
                        merchantIdentity,
                        productIdentity,
                        variantIdentity,
                        sellingPlan
                ),
                merchant,
                variant,
                new Money(2999, "usd"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 5, null),
                List.of(),
                URI.create("https://checkout.example/" + source),
                List.of(),
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
