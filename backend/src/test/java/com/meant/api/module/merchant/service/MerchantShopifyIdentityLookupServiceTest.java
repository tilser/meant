package com.meant.api.module.merchant.service;

import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.SHOPIFY_SHOP;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.PROVIDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.service.query.FindMerchantShopifyIdentityQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantShopifyIdentityLookupServiceTest {

    @Test
    void returnsOneCanonicalVerifiedShopifyShopGid() {
        MerchantIdentityRepository repository = mock(MerchantIdentityRepository.class);
        UUID merchantId = UUID.randomUUID();
        MerchantIdentity identity = mock(MerchantIdentity.class);
        when(identity.getNormalizedValue()).thenReturn("gid://shopify/shop/123");
        when(repository.findByMerchantIdAndMerchantActiveTrueAndNamespaceAndRoleOrderByVerifiedAtAsc(
                merchantId,
                SHOPIFY_SHOP,
                PROVIDER_ID
        )).thenReturn(List.of(identity));

        var result = new MerchantShopifyIdentityLookupService(repository)
                .find(new FindMerchantShopifyIdentityQuery(merchantId));

        assertThat(result).contains("gid://shopify/Shop/123");
    }

    @Test
    void refusesAmbiguousOrMalformedIdentityEvidence() {
        MerchantIdentityRepository repository = mock(MerchantIdentityRepository.class);
        UUID merchantId = UUID.randomUUID();
        MerchantIdentity first = mock(MerchantIdentity.class);
        MerchantIdentity second = mock(MerchantIdentity.class);
        when(first.getNormalizedValue()).thenReturn("gid://shopify/shop/123");
        when(second.getNormalizedValue()).thenReturn("gid://shopify/shop/456");
        when(repository.findByMerchantIdAndMerchantActiveTrueAndNamespaceAndRoleOrderByVerifiedAtAsc(
                merchantId,
                SHOPIFY_SHOP,
                PROVIDER_ID
        )).thenReturn(List.of(first, second));

        var result = new MerchantShopifyIdentityLookupService(repository)
                .find(new FindMerchantShopifyIdentityQuery(merchantId));

        assertThat(result).isEmpty();
    }

    @Test
    void inactiveMerchantsHaveNoRoutableShopifyIdentity() {
        MerchantIdentityRepository repository = mock(MerchantIdentityRepository.class);
        UUID merchantId = UUID.randomUUID();
        when(repository.findByMerchantIdAndMerchantActiveTrueAndNamespaceAndRoleOrderByVerifiedAtAsc(
                merchantId,
                SHOPIFY_SHOP,
                PROVIDER_ID
        )).thenReturn(List.of());

        var result = new MerchantShopifyIdentityLookupService(repository)
                .find(new FindMerchantShopifyIdentityQuery(merchantId));

        assertThat(result).isEmpty();
    }
}
