package com.meant.api.module.merchant.service;

import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.SHOPIFY_SHOP;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.STOREFRONT_DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIntegrationStatus.ACTIVE;
import static com.meant.api.module.merchant.constant.MerchantIntegrationStatus.INACTIVE;
import static com.meant.api.module.merchant.constant.MerchantRawSource.HUGGING_FACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantPresentationOriginServiceTest {

    private final MerchantRepository merchantRepository = mock(MerchantRepository.class);
    private final MerchantIdentityRepository merchantIdentityRepository =
            mock(MerchantIdentityRepository.class);
    private final MerchantIntegrationRepository merchantIntegrationRepository =
            mock(MerchantIntegrationRepository.class);
    private final MerchantPresentationOriginService service = new MerchantPresentationOriginService(
            merchantRepository,
            merchantIdentityRepository,
            merchantIntegrationRepository
    );

    @Test
    void verifiedShopifyShopIdentityResolvesToTheActiveMerchantsOfficialDomain() {
        Merchant merchant = merchant("official-shop.example", true);
        when(merchantRepository.findActiveByIdentity(
                SHOPIFY_SHOP,
                "gid://shopify/shop/17756429"
        )).thenReturn(Optional.of(merchant));

        String origin = service.resolve(
                null,
                null,
                "shopify",
                " GID://Shopify/Shop/17756429 ",
                "transport-store.myshopify.com"
        );

        assertThat(origin).isEqualTo("official-shop.example");
    }

    @Test
    void localActiveIntegrationResolvesToTheActiveMerchantsOfficialDomain() {
        UUID integrationId = UUID.randomUUID();
        Merchant merchant = merchant("local-shop.example", true);
        MerchantIntegration integration = MerchantIntegration.builder()
                .id(integrationId)
                .merchant(merchant)
                .status(ACTIVE)
                .build();
        when(merchantIntegrationRepository.findById(integrationId))
                .thenReturn(Optional.of(integration));

        String origin = service.resolve(null, integrationId, null, null, null);

        assertThat(origin).isEqualTo("local-shop.example");
    }

    @Test
    void verifiedDomainIdentityResolvesToTheActiveMerchantsOfficialDomain() {
        Merchant merchant = merchant("official-domain.example", true);
        when(merchantRepository.findByDomainAndActiveTrue("routing-domain.example"))
                .thenReturn(Optional.empty());
        when(merchantRepository.findActiveByIdentity(DOMAIN, "routing-domain.example"))
                .thenReturn(Optional.of(merchant));

        String origin = service.resolve(
                null,
                null,
                null,
                null,
                "WWW.Routing-Domain.Example."
        );

        assertThat(origin).isEqualTo("official-domain.example");
    }

    @Test
    void unknownRoutingIdentityDoesNotBecomeABuyerVisibleOrigin() {
        when(merchantRepository.findByDomainAndActiveTrue("unknown-route.myshopify.com"))
                .thenReturn(Optional.empty());
        when(merchantRepository.findActiveByIdentity(DOMAIN, "unknown-route.myshopify.com"))
                .thenReturn(Optional.empty());

        String origin = service.resolve(
                null,
                null,
                "SHOPIFY",
                "gid://shopify/Shop/999",
                "unknown-route.myshopify.com"
        );

        assertThat(origin).isNull();
    }

    @Test
    void inactiveMerchantDoesNotResolveThroughItsLocalId() {
        UUID merchantId = UUID.randomUUID();
        when(merchantRepository.findByIdAndActiveTrue(merchantId)).thenReturn(Optional.empty());

        assertThat(service.resolve(merchantId, null, null, null, null)).isNull();
    }

    @Test
    void inactiveIntegrationDoesNotResolveEvenWhenItsMerchantIsActive() {
        UUID integrationId = UUID.randomUUID();
        MerchantIntegration integration = MerchantIntegration.builder()
                .id(integrationId)
                .merchant(merchant("active-merchant.example", true))
                .status(INACTIVE)
                .build();
        when(merchantIntegrationRepository.findById(integrationId))
                .thenReturn(Optional.of(integration));

        assertThat(service.resolve(null, integrationId, null, null, null)).isNull();
    }

    @Test
    void inactiveIntegrationDoesNotResolveInBatch() {
        UUID integrationId = UUID.randomUUID();
        MerchantIntegration integration = MerchantIntegration.builder()
                .id(integrationId)
                .merchant(merchant("active-merchant.example", true))
                .status(INACTIVE)
                .build();
        when(merchantIntegrationRepository.findByIdInOrderByCreatedAtAsc(Set.of(integrationId)))
                .thenReturn(List.of(integration));

        assertThat(service.resolveAll(List.of(provenance(integrationId)))).isEmpty();
    }

    @Test
    void identityOwnedByAnInactiveMerchantDoesNotResolveInBatch() {
        ResultProvenance provenance = provenance("inactive-route.example");
        MerchantIdentity identity = MerchantIdentity.builder()
                .merchant(merchant("inactive-official.example", false))
                .namespace(DOMAIN)
                .normalizedValue("inactive-route.example")
                .role(STOREFRONT_DOMAIN)
                .source(HUGGING_FACE)
                .verifiedAt(Instant.parse("2026-07-23T18:30:00Z"))
                .build();
        when(merchantRepository.findByDomainIn(Set.of("inactive-route.example")))
                .thenReturn(List.of());
        when(merchantIdentityRepository.findByNamespaceInAndNormalizedValueIn(
                Set.of(DOMAIN),
                Set.of("inactive-route.example")
        )).thenReturn(List.of(identity));

        assertThat(service.resolveAll(List.of(provenance))).isEmpty();
    }

    private Merchant merchant(String domain, boolean active) {
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain(domain)
                .active(active)
                .build();
    }

    private ResultProvenance provenance(String routingDomain) {
        return provenance(null, routingDomain);
    }

    private ResultProvenance provenance(UUID integrationId) {
        return provenance(integrationId, null);
    }

    private ResultProvenance provenance(UUID integrationId, String routingDomain) {
        ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
        return new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(
                        provider,
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "fixture"
                ),
                integrationId == null ? null : new LocalMerchantRouting(integrationId),
                null,
                routingDomain,
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        provider.value(),
                        "product-1"
                ),
                null,
                new ResultFreshness(Instant.parse("2026-07-23T18:30:00Z"), null),
                new ResultSourceReference(
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "fixture",
                        null
                )
        );
    }
}
