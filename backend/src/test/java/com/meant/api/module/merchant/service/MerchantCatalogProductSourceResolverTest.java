package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantCatalogProductSourceResolverTest {
    @Test
    void resolvesAllPageSourcesInOneLookupAndDoesNotLabelShopifyAsGeneric() {
        UUID genericMerchant = UUID.randomUUID();
        UUID shopifyMerchant = UUID.randomUUID();
        MerchantIntegrationLookupService integrations = mock(MerchantIntegrationLookupService.class);
        when(integrations.listByMerchants(any())).thenReturn(List.of(
                integration(genericMerchant, MerchantIntegrationProvider.GENERIC_UCP, "https://generic.test/mcp"),
                integration(shopifyMerchant, MerchantIntegrationProvider.SHOPIFY, "https://shopify.test/mcp")
        ));
        MerchantCatalogProductSourceResolver resolver = new MerchantCatalogProductSourceResolver(integrations);

        var sources = resolver.resolve(List.of(
                product(genericMerchant, "https://generic.test/mcp"),
                product(shopifyMerchant, "https://shopify.test/mcp")
        ));

        assertThat(sources.get(genericMerchant)).isEqualTo(MerchantCatalogSourceIdentity.DISCOVERY_SOURCE);
        assertThat(sources.get(shopifyMerchant).provider().value()).isEqualTo("SHOPIFY");
        assertThat(sources.get(shopifyMerchant)).isNotEqualTo(MerchantCatalogSourceIdentity.DISCOVERY_SOURCE);
        verify(integrations).listByMerchants(any());
    }

    @Test
    void unresolvedOrAmbiguousMerchantSourceGetsFailClosedIdentity() {
        UUID merchantId = UUID.randomUUID();
        MerchantIntegrationLookupService integrations = mock(MerchantIntegrationLookupService.class);
        when(integrations.listByMerchants(any())).thenReturn(List.of());

        var sources = new MerchantCatalogProductSourceResolver(integrations)
                .resolve(List.of(product(merchantId, "https://unknown.test/mcp")));

        assertThat(sources.get(merchantId)).isEqualTo(MerchantCatalogProductSourceResolver.UNRESOLVED_SOURCE);
    }

    private MerchantIntegrationResult integration(
            UUID merchantId,
            MerchantIntegrationProvider provider,
            String endpoint
    ) {
        UUID id = UUID.randomUUID();
        return new MerchantIntegrationResult(
                id,
                merchantId,
                provider,
                null,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                null,
                null,
                null,
                endpoint,
                null,
                null,
                MerchantIntegrationStatus.ACTIVE,
                null,
                null,
                null,
                null
        );
    }

    private MerchantSemanticProductResult product(UUID merchantId, String endpoint) {
        MerchantSemanticProductResult product = mock(MerchantSemanticProductResult.class);
        when(product.merchantId()).thenReturn(merchantId);
        when(product.endpoint()).thenReturn(endpoint);
        return product;
    }
}
