package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopifyCatalogPurchaseReferencePolicyTest {
    private static final UUID INTEGRATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000019");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            ShopifyOfferIdentityStrategy.PROVIDER,
            ResultSourceType.PROVIDER_CATALOG,
            "SHOPIFY_GLOBAL_CATALOG"
    );

    private final ShopifyCatalogPurchaseReferencePolicy policy = new ShopifyCatalogPurchaseReferencePolicy();

    @Test
    void externalOnlyReferenceRequiresVerifiedMerchantDomain() {
        assertThat(policy.allows(reference(null, null))).isFalse();
        assertThat(policy.allows(reference("merchant.example", null))).isTrue();
    }

    @Test
    void verifiedLocalRouteDoesNotRequireExternalMerchantDomain() {
        assertThat(policy.allows(reference(null, new LocalMerchantRouting(INTEGRATION_ID)))).isTrue();
    }

    private CatalogProductReference reference(String domain, LocalMerchantRouting routing) {
        return new CatalogProductReference(
                "product-1",
                SOURCE,
                null,
                routing,
                identifier(ExternalIdentifierType.MERCHANT, "merchant-1"),
                domain,
                identifier(ExternalIdentifierType.PRODUCT, "product-1"),
                identifier(ExternalIdentifierType.VARIANT, "variant-1"),
                List.of()
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, ShopifyOfferIdentityStrategy.PROVIDER.value(), value);
    }
}
