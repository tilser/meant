package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.GenericUcpCatalogDataUsePolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.ExactProductGroupingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserCanonicalProductCandidateMapperTest {

    private static final UUID MERCHANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000008");
    private static final Instant OBSERVED = Instant.parse("2026-07-11T10:00:00Z");

    @Test
    void genericSelectedOptionsRemainDistinctOfferIdentityWhenVariantIdIsMissing() {
        UserCanonicalProductCandidateMapper mapper = new UserCanonicalProductCandidateMapper(List.of());
        ProductCandidate medium = mapper.from(product("M"), "merchant:product:medium", integration(), OBSERVED);
        ProductCandidate large = mapper.from(product("L"), "merchant:product:large", integration(), OBSERVED);

        assertThat(medium.offer().identity().externalVariantIdentity()).isNull();
        assertThat(medium.offer().key()).isNotEqualTo(large.offer().key());
        assertThat(medium.offer().selectedOptions()).extracting(ProductAttribute::name, ProductAttribute::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Color", "Blue"),
                        org.assertj.core.groups.Tuple.tuple("Size", "M")
                );
        assertThat(medium.retrievalSignals()).singleElement().satisfies(signal -> {
            assertThat(signal.valueBasisPoints()).isBetween(0, 10_000);
            assertThat(signal.calibrationVersion()).isEqualTo("merchant-semantic-voyage-rerank-v1");
            assertThat(signal.merchantScope()).isSameAs(medium.offer().identity().merchantScope());
        });
        assertThat(new ExactProductGroupingService().group(List.of(medium, large))).singleElement()
                .satisfies(canonical -> assertThat(canonical.offers()).hasSize(2));
    }

    @Test
    void canonicalGenericStorefrontSourceAllowsDurableSavedReference() {
        ProductCandidate candidate = new UserCanonicalProductCandidateMapper(List.of())
                .from(product("M"), "merchant:product:medium", integration(), OBSERVED);
        var source = candidate.offer().provenance().getFirst().discoverySource();
        GenericUcpCatalogDataUsePolicy policy = new GenericUcpCatalogDataUsePolicy(
                new GenericUcpCatalogDataUseProperties(Duration.ofHours(24), Duration.ofMinutes(2))
        );

        assertThat(source.value()).isEqualTo("merchant-1");
        assertThat(candidate.offer().provenance().getFirst().externalMerchantDomain())
                .isEqualTo("merchant.example");
        assertThat(policy.supports(source)).isTrue();
        assertThat(policy.decide(source, CatalogPayloadClass.SAVED_INTERACTION).mode())
                .isEqualTo(CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY);
    }

    private MerchantSemanticProductResult product(String size) {
        return new MerchantSemanticProductResult(
                MERCHANT_ID, "merchant.example", "Merchant", "https://merchant.example/mcp",
                1, 1d, 1d, "product-1", "Linen shirt", "<p>linen</p>",
                "https://merchant.example/products/1", "https://merchant.example/products/1.jpg",
                1000L, 1000L, "USD", null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true,
                null, "Linen shirt", null, List.of(), List.of(), "10.00", "10.00", "USD",
                2, false, List.of(), null, "Blue / " + size,
                List.of(
                        new ProductDetailsResponse.SelectedOption("Size", size),
                        new ProductDetailsResponse.SelectedOption("Color", "Blue")
                ),
                "10.00", "USD", null, null, true, 1, 1d, 1
        );
    }

    private MerchantIntegrationResult integration() {
        return new MerchantIntegrationResult(
                UUID.fromString("40000000-0000-0000-0000-000000000008"),
                MERCHANT_ID,
                MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                "merchant-1",
                "merchant.example",
                null,
                "https://merchant.example/mcp",
                "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY,
                OBSERVED,
                OBSERVED,
                OBSERVED
        );
    }
}
