package com.meant.api.module.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.MerchantLookupService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.GetMerchantIntegrationByProviderIdentityQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByVerifiedDomainQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsQuery;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class MerchantIntegrationRepositoryTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T09:00:00Z");

    @Autowired
    private MerchantIntegrationRepository merchantIntegrationRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private MerchantIntegrationLookupService merchantIntegrationLookupService;

    @Autowired
    private MerchantCartProviderLookupService merchantCartProviderLookupService;

    @Autowired
    private MerchantLookupService merchantLookupService;

    @AfterEach
    void cleanUpIntegrations() {
        merchantIntegrationRepository.deleteAllInBatch();
    }

    @Test
    void oneMerchantCanResolveGenericUcpAndShopifyConnectionsWithDifferentAuthStrategies() {
        Merchant merchant = saveMerchant("dual-provider-%s.example".formatted(UUID.randomUUID()));
        MerchantIntegration genericUcp = saveIntegration(
                merchant,
                MerchantIntegrationProvider.GENERIC_UCP,
                null,
                merchant.getDomain(),
                "https://%s/mcp".formatted(merchant.getDomain()),
                MerchantIntegrationAuthStrategy.NONE,
                Set.of(
                        MerchantIntegrationRole.STOREFRONT_CATALOG,
                        MerchantIntegrationRole.CART,
                        MerchantIntegrationRole.CHECKOUT
                )
        );
        MerchantIntegration shopify = saveIntegration(
                merchant,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/12345",
                merchant.getDomain(),
                "https://shop.example/api/ucp/mcp",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.of(
                        MerchantIntegrationRole.CATALOG_PROVENANCE,
                        MerchantIntegrationRole.STOREFRONT_CATALOG,
                        MerchantIntegrationRole.CART,
                        MerchantIntegrationRole.CHECKOUT,
                        MerchantIntegrationRole.ORDERS
                )
        );

        List<MerchantIntegrationResult> byMerchant = merchantIntegrationLookupService.listByMerchant(
                new ListMerchantIntegrationsQuery(merchant.getId())
        );
        MerchantIntegrationResult byProviderIdentity = merchantIntegrationLookupService.findByProviderIdentity(
                new GetMerchantIntegrationByProviderIdentityQuery(
                        MerchantIntegrationProvider.SHOPIFY,
                        "  gid://shopify/Shop/12345  "
                )
        ).orElseThrow();
        List<MerchantIntegrationResult> byDomain = merchantIntegrationLookupService.listByVerifiedDomain(
                new ListMerchantIntegrationsByVerifiedDomainQuery(merchant.getDomain().toUpperCase() + ".")
        );

        assertThat(byMerchant)
                .extracting(MerchantIntegrationResult::id)
                .containsExactlyInAnyOrder(genericUcp.getId(), shopify.getId());
        assertThat(byMerchant)
                .extracting(MerchantIntegrationResult::authStrategy)
                .containsExactlyInAnyOrder(
                        MerchantIntegrationAuthStrategy.NONE,
                        MerchantIntegrationAuthStrategy.OAUTH_BEARER
                );
        assertThat(byProviderIdentity.id()).isEqualTo(shopify.getId());
        assertThat(byProviderIdentity.roles()).contains(MerchantIntegrationRole.ORDERS);
        assertThat(byDomain)
                .extracting(MerchantIntegrationResult::provider)
                .containsExactlyInAnyOrder(
                        MerchantIntegrationProvider.GENERIC_UCP,
                        MerchantIntegrationProvider.SHOPIFY
                );

        assertThat(merchantCartProviderLookupService.findById(merchant.getId()))
                .get()
                .satisfies(provider -> {
                    assertThat(provider.domain()).isEqualTo(merchant.getDomain());
                    assertThat(provider.advertisedMcpEndpoint()).isEqualTo(merchant.getAdvertisedMcpEndpoint());
                });
        assertThat(merchantLookupService.activeSearchResult(merchant.getId()).domain())
                .isEqualTo(merchant.getDomain());
    }

    @Test
    void providerExternalIdentityCannotBeClaimedByTwoConnections() {
        Merchant firstMerchant = saveMerchant("identity-owner-%s.example".formatted(UUID.randomUUID()));
        Merchant secondMerchant = saveMerchant("identity-claimant-%s.example".formatted(UUID.randomUUID()));
        saveIntegration(
                firstMerchant,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/duplicate",
                firstMerchant.getDomain(),
                "https://first-shop.example/api/ucp/mcp",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
        );

        MerchantIntegration duplicate = integration(
                secondMerchant,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/duplicate",
                secondMerchant.getDomain(),
                "https://second-shop.example/api/ucp/mcp",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
        );

        assertThatThrownBy(() -> merchantIntegrationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void verifiedDomainCannotBeClaimedTwiceForTheSameProvider() {
        Merchant firstMerchant = saveMerchant("domain-owner-%s.example".formatted(UUID.randomUUID()));
        Merchant secondMerchant = saveMerchant("domain-claimant-%s.example".formatted(UUID.randomUUID()));
        String verifiedDomain = "shared-%s.example".formatted(UUID.randomUUID());
        saveIntegration(
                firstMerchant,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/first-" + UUID.randomUUID(),
                verifiedDomain,
                "https://first-domain.example/api/ucp/mcp",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
        );

        MerchantIntegration duplicate = integration(
                secondMerchant,
                MerchantIntegrationProvider.SHOPIFY,
                "gid://shopify/Shop/second-" + UUID.randomUUID(),
                verifiedDomain.toUpperCase(),
                "https://second-domain.example/api/ucp/mcp",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
        );

        assertThatThrownBy(() -> merchantIntegrationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lookupServiceRejectsMalformedVerifiedDomain() {
        assertThatThrownBy(() -> merchantIntegrationLookupService.listByVerifiedDomain(
                new ListMerchantIntegrationsByVerifiedDomainQuery("https://shop.example/path")
        )).isInstanceOf(ConstraintViolationException.class);
    }

    private MerchantIntegration saveIntegration(
            Merchant merchant,
            MerchantIntegrationProvider provider,
            String externalMerchantId,
            String verifiedDomain,
            String endpoint,
            MerchantIntegrationAuthStrategy authStrategy,
            Set<MerchantIntegrationRole> roles
    ) {
        return merchantIntegrationRepository.saveAndFlush(integration(
                merchant,
                provider,
                externalMerchantId,
                verifiedDomain,
                endpoint,
                authStrategy,
                roles
        ));
    }

    private MerchantIntegration integration(
            Merchant merchant,
            MerchantIntegrationProvider provider,
            String externalMerchantId,
            String verifiedDomain,
            String endpoint,
            MerchantIntegrationAuthStrategy authStrategy,
            Set<MerchantIntegrationRole> roles
    ) {
        return MerchantIntegration.builder()
                .merchant(merchant)
                .provider(provider)
                .kind(MerchantIntegrationKind.MERCHANT_CONNECTION)
                .roles(roles)
                .externalMerchantId(externalMerchantId)
                .verifiedDomain(verifiedDomain)
                .verifiedShopIdentity(provider == MerchantIntegrationProvider.SHOPIFY
                        ? "shop-%s.myshopify.com".formatted(UUID.randomUUID())
                        : null)
                .endpoint(endpoint)
                .protocolVersion("2026-04-08")
                .authStrategy(authStrategy)
                .status(MerchantIntegrationStatus.ACTIVE)
                .source(MerchantIntegrationSource.DISCOVERY)
                .rawMetadata("{\"captured\":true}")
                .capturedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private Merchant saveMerchant(String domain) {
        MerchantRaw merchantRaw = merchantRawRepository.save(MerchantRaw.builder()
                .datasetRowIdx(Math.abs(domain.hashCode()))
                .domain(domain)
                .status("verified")
                .ucpUrl("https://%s/.well-known/ucp".formatted(domain))
                .httpStatus(200)
                .ucpVersion("2026-04-08")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(true)
                .hasOrder(true)
                .hasPaymentToken(false)
                .capabilityCount(4)
                .transports("[\"mcp\"]")
                .fetchedAt(NOW)
                .processed(true)
                .sourceHash("source-" + UUID.randomUUID())
                .active(true)
                .lastSeenAt(NOW)
                .build());
        return merchantRepository.save(Merchant.builder()
                .merchantRaw(merchantRaw)
                .domain(domain)
                .ucpUrl(merchantRaw.getUcpUrl())
                .ucpVersion(merchantRaw.getUcpVersion())
                .advertisedMcpEndpoint("https://%s/api/ucp/mcp".formatted(domain))
                .profileMcpEndpoint("https://%s/api/mcp".formatted(domain))
                .profileHash("profile-" + UUID.randomUUID())
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build());
    }
}
