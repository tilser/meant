package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.GenericUcpCatalogDataUsePolicy;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.provider.shopify.catalog.ShopifyCatalogDataUsePolicy;
import com.meant.api.provider.shopify.catalog.ShopifyCatalogDataUseProperties;
import com.meant.api.provider.shopify.catalog.ShopifyGlobalCatalogNormalizer;
import com.meant.api.provider.shopify.catalog.ShopifyGlobalCatalogProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogDataUsePolicyResolverTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shopifySearchFactsAndMediaDefaultToSessionOnlyAloneOrMixed() {
        CatalogDataUsePolicyResolver resolver = resolver(genericPolicy(), shopifyPolicy(false));

        assertThat(resolver.admitSearch(List.of(shopifySource())).admitted()).isFalse();
        assertThat(resolver.admitSearch(List.of(MerchantCatalogSourceIdentity.DISCOVERY_SOURCE, shopifySource())).admitted())
                .isFalse();
    }

    @Test
    void unknownSourceAndPayloadFailClosedWithoutRawSourceMetricTags() {
        DiscoverySourceIdentity unknown = new DiscoverySourceIdentity(
                new ProviderIdentity("UNREVIEWED_PROVIDER"),
                ResultSourceType.DATASET_IMPORT,
                "user-controlled-source-value"
        );
        CatalogDataUsePolicyResolver resolver = resolver(genericPolicy());

        for (CatalogPayloadClass payloadClass : CatalogPayloadClass.values()) {
            assertThat(resolver.resolve(unknown, payloadClass).mode())
                    .as(payloadClass.name())
                    .isEqualTo(CatalogRetentionMode.SESSION_ONLY);
        }
        assertThat(registry.find("commerce.catalog.data_use.decisions").counters())
                .flatExtracting(counter -> counter.getId().getTags())
                .extracting(tag -> tag.getValue())
                .doesNotContain("UNREVIEWED_PROVIDER", "user-controlled-source-value");
    }

    @Test
    void approvedGenericSourceGetsItsSourceSpecificBoundedTtl() {
        CatalogDataUsePolicyResolver resolver = resolver(genericPolicy());

        var admission = resolver.admitSearch(List.of(MerchantCatalogSourceIdentity.DISCOVERY_SOURCE));

        assertThat(admission.admitted()).isTrue();
        assertThat(admission.maximumRetention()).isEqualTo(Duration.ofHours(6));
        assertThat(admission.policyFingerprint()).hasSize(64);
    }

    @Test
    void genericSavedInteractionPolicyCoversServerResolvedStorefrontIdentities() {
        CatalogDataUsePolicyResolver resolver = resolver(genericPolicy());
        List<DiscoverySourceIdentity> storefronts = List.of(
                new DiscoverySourceIdentity(
                        MerchantCatalogSourceIdentity.PROVIDER,
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "merchant-1"
                ),
                new DiscoverySourceIdentity(
                        MerchantCatalogSourceIdentity.PROVIDER,
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "LOCAL_STOREFRONT:00000000-0000-0000-0000-000000000001"
                )
        );

        assertThat(storefronts)
                .allSatisfy(source -> assertThat(resolver.resolve(
                        source,
                        CatalogPayloadClass.SAVED_INTERACTION
                )).satisfies(decision -> {
                    assertThat(decision.mode()).isEqualTo(CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY);
                    assertThat(decision.policyKey()).isEqualTo("generic-ucp-storefront-v2");
                }));
    }

    @Test
    void genericPolicyDoesNotAdmitAnotherSourceTypeFromTheSameProvider() {
        CatalogDataUsePolicyResolver resolver = resolver(genericPolicy());
        DiscoverySourceIdentity providerCatalog = new DiscoverySourceIdentity(
                MerchantCatalogSourceIdentity.PROVIDER,
                ResultSourceType.PROVIDER_CATALOG,
                "unreviewed-generic-provider-catalog"
        );

        assertThat(resolver.resolve(providerCatalog, CatalogPayloadClass.SAVED_INTERACTION).mode())
                .isEqualTo(CatalogRetentionMode.SESSION_ONLY);
    }

    @Test
    void transactionSnapshotsRemainSessionOnlyUntilTheirOwningTicketAddsApproval() {
        CatalogDataUsePolicyResolver resolver = resolver(genericPolicy(), shopifyPolicy(false));

        assertThat(resolver.resolve(
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                CatalogPayloadClass.TRANSACTION_SNAPSHOT
        ).mode()).isEqualTo(CatalogRetentionMode.SESSION_ONLY);
        assertThat(resolver.resolve(shopifySource(), CatalogPayloadClass.TRANSACTION_SNAPSHOT).mode())
                .isEqualTo(CatalogRetentionMode.SESSION_ONLY);
    }

    @Test
    void repeatedPageSourceIsResolvedOncePerPayloadClassRatherThanPerCandidate() {
        AtomicInteger decisions = new AtomicInteger();
        CatalogDataUsePolicy policy = new CatalogDataUsePolicy() {
            @Override
            public boolean supports(DiscoverySourceIdentity source) {
                return MerchantCatalogSourceIdentity.DISCOVERY_SOURCE.equals(source);
            }

            @Override
            public com.meant.api.module.catalog.service.dto.CatalogRetentionDecision decide(
                    DiscoverySourceIdentity source,
                    CatalogPayloadClass payloadClass
            ) {
                decisions.incrementAndGet();
                return com.meant.api.module.catalog.service.dto.CatalogRetentionDecision.bounded(
                        "counted-v1",
                        Duration.ofMinutes(30)
                );
            }
        };
        CatalogDataUsePolicyResolver resolver = resolver(policy);

        resolver.admitSearch(java.util.Collections.nCopies(100, MerchantCatalogSourceIdentity.DISCOVERY_SOURCE));

        assertThat(decisions).hasValue(2);
    }

    private GenericUcpCatalogDataUsePolicy genericPolicy() {
        return new GenericUcpCatalogDataUsePolicy(
                new GenericUcpCatalogDataUseProperties(
                        Duration.ofHours(6),
                        Duration.ofMinutes(2)
                )
        );
    }

    private ShopifyCatalogDataUsePolicy shopifyPolicy(boolean approved) {
        ShopifyGlobalCatalogProperties properties = mock(ShopifyGlobalCatalogProperties.class);
        when(properties.sourceIdentity()).thenReturn("SHOPIFY_GLOBAL_CATALOG");
        return new ShopifyCatalogDataUsePolicy(
                properties,
                new ShopifyCatalogDataUseProperties(
                        approved,
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(2)
                )
        );
    }

    private DiscoverySourceIdentity shopifySource() {
        return new DiscoverySourceIdentity(
                ShopifyGlobalCatalogNormalizer.SHOPIFY,
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG"
        );
    }

    private CatalogDataUsePolicyResolver resolver(CatalogDataUsePolicy... policies) {
        return new CatalogDataUsePolicyResolver(
                List.of(policies),
                new CatalogDataUsePolicyMetrics(registry)
        );
    }
}
