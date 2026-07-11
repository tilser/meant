package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantCatalogDiscoveryEligibilityPolicyTest {

    @Test
    void excludesOnlyMerchantsCoveredByTheScheduledProviderCatalog() {
        UUID shopifyMerchant = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID genericMerchant = UUID.fromString("10000000-0000-0000-0000-000000000002");
        StubIntegrationLookupService lookup = new StubIntegrationLookupService(List.of(
                integration(shopifyMerchant, MerchantIntegrationProvider.SHOPIFY),
                integration(genericMerchant, MerchantIntegrationProvider.GENERIC_UCP)
        ));
        MerchantCatalogDiscoveryEligibilityPolicy policy = new MerchantCatalogDiscoveryEligibilityPolicy(lookup);

        List<MerchantSemanticSearchResult> eligible = policy.eligible(
                List.of(merchant(shopifyMerchant, 1), merchant(genericMerchant, 2)),
                Set.of(new ProviderIdentity("SHOPIFY")),
                null
        );

        assertThat(eligible).extracting(MerchantSemanticSearchResult::merchantId)
                .containsExactly(genericMerchant);
        assertThat(lookup.requestedMerchantIds).containsExactlyInAnyOrder(shopifyMerchant, genericMerchant);
    }

    @Test
    void explicitMerchantScopeBypassesProviderWideCoverageExclusion() {
        UUID shopifyMerchant = UUID.fromString("10000000-0000-0000-0000-000000000001");
        StubIntegrationLookupService lookup = new StubIntegrationLookupService(List.of(
                integration(shopifyMerchant, MerchantIntegrationProvider.SHOPIFY)
        ));
        MerchantCatalogDiscoveryEligibilityPolicy policy = new MerchantCatalogDiscoveryEligibilityPolicy(lookup);

        List<MerchantSemanticSearchResult> eligible = policy.eligible(
                List.of(merchant(shopifyMerchant, 1)),
                Set.of(new ProviderIdentity("SHOPIFY")),
                shopifyMerchant
        );

        assertThat(eligible).extracting(MerchantSemanticSearchResult::merchantId)
                .containsExactly(shopifyMerchant);
        assertThat(lookup.requestedMerchantIds).isEmpty();
    }

    private MerchantSemanticSearchResult merchant(UUID merchantId, int rank) {
        return new MerchantSemanticSearchResult(
                merchantId,
                "merchant-" + rank + ".example",
                "Merchant " + rank,
                "https://merchant-" + rank + ".example/api/ucp/mcp",
                null,
                "catalog",
                0.9,
                0.8,
                rank
        );
    }

    private MerchantIntegrationResult integration(
            UUID merchantId,
            MerchantIntegrationProvider provider
    ) {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        return new MerchantIntegrationResult(
                UUID.randomUUID(),
                merchantId,
                provider,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                provider.name() + "-merchant",
                "merchant.example",
                null,
                "https://merchant.example/api/ucp/mcp",
                "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY,
                now,
                now,
                now
        );
    }

    private static final class StubIntegrationLookupService extends MerchantIntegrationLookupService {

        private final List<MerchantIntegrationResult> integrations;
        private Set<UUID> requestedMerchantIds = Set.of();

        private StubIntegrationLookupService(List<MerchantIntegrationResult> integrations) {
            super(null);
            this.integrations = integrations;
        }

        @Override
        public List<MerchantIntegrationResult> listByMerchants(ListMerchantIntegrationsByMerchantsQuery query) {
            requestedMerchantIds = query.merchantIds();
            return integrations;
        }
    }
}
