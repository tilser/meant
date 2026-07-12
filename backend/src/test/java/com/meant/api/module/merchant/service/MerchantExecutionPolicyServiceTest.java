package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.properties.MerchantExecutionPolicyProperties;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.query.EvaluateObservedProviderPolicyQuery;
import com.meant.api.module.merchant.service.GenericUcpCapabilityReadinessAdapter;
import com.meant.api.module.merchant.properties.GenericUcpCapabilityReadinessProperties;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.capability.ShopifyAuthorizationTier;
import com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessAdapter;
import com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MerchantExecutionPolicyServiceTest {

    @Test
    void observedExternalShopifyCheckoutEnablesEmbeddedRailWithoutALocalIntegration() {
        ShopifyCapabilityReadinessProperties readiness = shopify(
                ShopifyAuthorizationTier.TOKEN,
                Set.of(),
                true,
                true,
                false,
                false,
                Set.of()
        );
        MerchantExecutionPolicy policy = service(
                rollouts(true, false), generic(false), readiness, enabledAgentAuth()
        ).evaluateObservedProvider(new EvaluateObservedProviderPolicyQuery(
                MerchantIntegrationProvider.SHOPIFY,
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.of(MerchantIntegrationRole.CART, MerchantIntegrationRole.CHECKOUT),
                Set.of("dev.ucp.shopping.cart", "dev.ucp.shopping.checkout")
        ));

        assertThat(policy.decision(CommerceOperation.CHECKOUT_SESSION).available()).isTrue();
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).available()).isTrue();
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).selectedRail())
                .isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).integrationId()).isNull();
    }

    @Test
    void authoritativeShopifyProfileEvidenceEnablesEmbeddedSurfaceForAGenericUcpTransport() {
        MerchantIntegration integration = integration(
                MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationStatus.ACTIVE,
                Set.of(MerchantIntegrationRole.CHECKOUT)
        );
        MerchantExecutionPolicy policy = service(
                rollouts(true, false),
                generic(false),
                shopify(ShopifyAuthorizationTier.STANDARD, Set.of(), true, true, false, false, Set.of())
        ).evaluate(
                merchant(false),
                List.of(integration),
                Set.of("dev.ucp.shopping.checkout", "dev.shopify.catalog")
        );

        assertThat(policy.decision(CommerceOperation.CHECKOUT_SESSION).available()).isTrue();
        assertThat(policy.decision(CommerceOperation.CHECKOUT_SESSION).provider())
                .isEqualTo(MerchantIntegrationProvider.GENERIC_UCP);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).available()).isTrue();
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).selectedRail())
                .isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).provider())
                .isEqualTo(MerchantIntegrationProvider.GENERIC_UCP);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).integrationId())
                .isEqualTo(integration.getId());
    }

    @Test
    void genericUcpCheckoutWithoutPlatformEvidenceDoesNotEnableShopifyCheckoutKit() {
        MerchantExecutionPolicy policy = service(
                rollouts(true, false),
                generic(false),
                shopify(ShopifyAuthorizationTier.STANDARD, Set.of(), true, true, false, false, Set.of())
        ).evaluate(
                merchant(false),
                List.of(integration(
                        MerchantIntegrationProvider.GENERIC_UCP,
                        MerchantIntegrationStatus.ACTIVE,
                        Set.of(MerchantIntegrationRole.CHECKOUT)
                )),
                Set.of("dev.ucp.shopping.checkout")
        );

        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).available()).isFalse();
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).ineligibilityReasons())
                .contains(CapabilityIneligibilityReason.NOT_ADVERTISED,
                        CapabilityIneligibilityReason.OPERATION_UNSUPPORTED);
    }

    @Test
    void currentMerchantCartCapabilityRepairsAStaleBackfillRoleForTheAdvertisedEndpoint() {
        Merchant merchant = merchant(false, "https://merchant.example/api/ucp/mcp");
        MerchantIntegration integration = integration(
                MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationStatus.ACTIVE,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
        );

        MerchantExecutionPolicy policy = service(
                rollouts(false, false),
                generic(false),
                shopifyDefaults()
        ).evaluate(merchant, List.of(integration), Set.of("dev.ucp.shopping.cart"));

        assertThat(policy.decision(CommerceOperation.CART).available()).isTrue();
        assertThat(policy.decision(CommerceOperation.CART).integrationId()).isEqualTo(integration.getId());
        assertThat(policy.decision(CommerceOperation.CART).provider())
                .isEqualTo(MerchantIntegrationProvider.GENERIC_UCP);
    }

    @Test
    void merchantCapabilityCannotGrantCartToADifferentIntegrationEndpoint() {
        Merchant merchant = merchant(false, "https://other.example/api/ucp/mcp");
        MerchantIntegration integration = integration(
                MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationStatus.ACTIVE,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
        );

        MerchantExecutionPolicy policy = service(
                rollouts(false, false),
                generic(false),
                shopifyDefaults()
        ).evaluate(merchant, List.of(integration), Set.of("dev.ucp.shopping.cart"));

        assertThat(policy.decision(CommerceOperation.CART).available()).isFalse();
    }

    @Test
    void advertisedGenericCheckoutCannotEnableDirectCompletionWithoutExplicitGrant() {
        MerchantExecutionPolicy policy = service(
                rollouts(false, false),
                generic(false),
                shopifyDefaults()
        ).evaluate(
                merchant(true),
                List.of(integration(
                        MerchantIntegrationProvider.GENERIC_UCP,
                        MerchantIntegrationStatus.ACTIVE,
                        Set.of(MerchantIntegrationRole.CHECKOUT)
                )),
                Set.of("dev.ucp.shopping.checkout")
        );

        var decision = policy.decision(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
        assertThat(decision.advertised()).isTrue();
        assertThat(decision.authorization().status()).isEqualTo(CapabilityAuthorizationStatus.NOT_AUTHORIZED);
        assertThat(decision.rolloutEnabled()).isTrue();
        assertThat(decision.availability()).isEqualTo(CapabilityAvailability.FALLBACK_AVAILABLE);
        assertThat(decision.selectedRail()).isEqualTo(CommerceExecutionRail.MERCHANT_HANDOFF);
    }

    @Test
    void legacyMerchantFlagContributesOnlyToRolloutCompatibility() {
        MerchantExecutionPolicy policy = service(
                rollouts(false, false),
                generic(true),
                shopifyDefaults()
        ).evaluate(
                merchant(true),
                List.of(integration(
                        MerchantIntegrationProvider.GENERIC_UCP,
                        MerchantIntegrationStatus.ACTIVE,
                        Set.of(MerchantIntegrationRole.CHECKOUT)
                )),
                Set.of()
        );

        var decision = policy.decision(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
        assertThat(decision.rolloutEnabled()).isTrue();
        assertThat(decision.authorization().status()).isEqualTo(CapabilityAuthorizationStatus.READY);
        assertThat(decision.advertised()).isFalse();
        assertThat(decision.availability()).isEqualTo(CapabilityAvailability.FALLBACK_AVAILABLE);
    }

    @Test
    void shopifyEmbeddedCheckoutCanBeAvailableWhileDirectCompletionIsDisabled() {
        ShopifyCapabilityReadinessProperties readiness = shopify(
                ShopifyAuthorizationTier.STANDARD,
                Set.of(),
                true,
                true,
                true,
                false,
                Set.of("complete_checkout")
        );
        MerchantExecutionPolicy policy = service(
                rollouts(true, true),
                generic(false),
                readiness
        ).evaluate(
                merchant(false),
                List.of(
                        integration(
                                MerchantIntegrationProvider.GENERIC_UCP,
                                MerchantIntegrationStatus.ACTIVE,
                                Set.of(MerchantIntegrationRole.CHECKOUT)
                        ),
                        integration(
                                MerchantIntegrationProvider.SHOPIFY,
                                MerchantIntegrationStatus.ACTIVE,
                                Set.of(MerchantIntegrationRole.CHECKOUT)
                        )
                ),
                Set.of()
        );

        assertThat(policy.decision(CommerceOperation.CHECKOUT_SESSION).provider())
                .isEqualTo(MerchantIntegrationProvider.SHOPIFY);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).availability())
                .isEqualTo(CapabilityAvailability.AVAILABLE);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).selectedRail())
                .isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
        assertThat(policy.decision(CommerceOperation.DIRECT_CHECKOUT_COMPLETION).authorization().status())
                .isEqualTo(CapabilityAuthorizationStatus.NOT_AUTHORIZED);
    }

    @Test
    void missingShopifyTokenScopeProducesTypedFallbackWithoutFetchingAToken() {
        ShopifyCapabilityReadinessProperties readiness = shopify(
                ShopifyAuthorizationTier.TOKEN,
                Set.of("catalog_read"),
                false,
                false,
                true,
                true,
                Set.of("complete_checkout")
        );
        MerchantExecutionPolicy policy = service(
                rollouts(false, true),
                generic(false),
                readiness,
                enabledAgentAuth()
        ).evaluate(
                merchant(false),
                List.of(integration(
                        MerchantIntegrationProvider.SHOPIFY,
                        MerchantIntegrationStatus.ACTIVE,
                        Set.of(MerchantIntegrationRole.CHECKOUT)
                )),
                Set.of()
        );

        var decision = policy.decision(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
        assertThat(decision.authorization().status()).isEqualTo(CapabilityAuthorizationStatus.MISSING_SCOPES);
        assertThat(decision.authorization().missingScopes()).containsExactly("complete_checkout");
        assertThat(decision.ineligibilityReasons()).contains(CapabilityIneligibilityReason.MISSING_SCOPES);
        assertThat(decision.selectedRail()).isEqualTo(CommerceExecutionRail.MERCHANT_HANDOFF);
    }

    @Test
    void shopifyTokenRailRequiresIntegrationOAuthBearerOptIn() {
        ShopifyCapabilityReadinessProperties readiness = shopify(
                ShopifyAuthorizationTier.TOKEN,
                Set.of("complete_checkout"),
                false,
                false,
                true,
                true,
                Set.of("complete_checkout")
        );
        MerchantIntegration integration = integration(
                MerchantIntegrationProvider.SHOPIFY,
                MerchantIntegrationStatus.ACTIVE,
                Set.of(MerchantIntegrationRole.CHECKOUT)
        );
        MerchantIntegration nonBearerIntegration = MerchantIntegration.builder()
                .id(integration.getId())
                .provider(integration.getProvider())
                .kind(integration.getKind())
                .roles(integration.getRoles())
                .endpoint(integration.getEndpoint())
                .authStrategy(MerchantIntegrationAuthStrategy.NONE)
                .status(integration.getStatus())
                .source(integration.getSource())
                .capturedAt(integration.getCapturedAt())
                .createdAt(integration.getCreatedAt())
                .updatedAt(integration.getUpdatedAt())
                .build();

        MerchantExecutionPolicy policy = service(
                rollouts(false, true),
                generic(false),
                readiness,
                enabledAgentAuth()
        ).evaluate(merchant(false), List.of(nonBearerIntegration), Set.of());

        var decision = policy.decision(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
        assertThat(decision.authorization().status())
                .isEqualTo(CapabilityAuthorizationStatus.AUTHENTICATION_DISABLED);
        assertThat(decision.ineligibilityReasons())
                .contains(CapabilityIneligibilityReason.AUTHENTICATION_DISABLED);
        assertThat(decision.selectedRail()).isEqualTo(CommerceExecutionRail.MERCHANT_HANDOFF);
    }

    @Test
    void unhealthyCheckoutIntegrationDoesNotDisableHealthyCatalogRail() {
        ShopifyCapabilityReadinessProperties readiness = shopify(
                ShopifyAuthorizationTier.STANDARD,
                Set.of(),
                true,
                true,
                false,
                false,
                Set.of()
        );
        MerchantExecutionPolicy policy = service(
                rollouts(true, false),
                generic(false),
                readiness
        ).evaluate(
                merchant(false),
                List.of(
                        integration(
                                MerchantIntegrationProvider.GENERIC_UCP,
                                MerchantIntegrationStatus.ACTIVE,
                                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG)
                        ),
                        integration(
                                MerchantIntegrationProvider.SHOPIFY,
                                MerchantIntegrationStatus.SUSPENDED,
                                Set.of(MerchantIntegrationRole.CHECKOUT)
                        )
                ),
                Set.of()
        );

        assertThat(policy.decision(CommerceOperation.CATALOG).availability())
                .isEqualTo(CapabilityAvailability.AVAILABLE);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).availability())
                .isEqualTo(CapabilityAvailability.FALLBACK_AVAILABLE);
        assertThat(policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).ineligibilityReasons())
                .contains(CapabilityIneligibilityReason.INTEGRATION_UNHEALTHY);
    }

    private MerchantExecutionPolicyService service(
            MerchantExecutionPolicyProperties rollouts,
            GenericUcpCapabilityReadinessProperties generic,
            ShopifyCapabilityReadinessProperties shopify
    ) {
        return service(rollouts, generic, shopify, disabledAgentAuth());
    }

    private MerchantExecutionPolicyService service(
            MerchantExecutionPolicyProperties rollouts,
            GenericUcpCapabilityReadinessProperties generic,
            ShopifyCapabilityReadinessProperties shopify,
            ShopifyAgentAuthProperties agentAuth
    ) {
        return new MerchantExecutionPolicyService(
                rollouts,
                new CapabilityExecutionPolicyEvaluator(),
                List.of(
                        new GenericUcpCapabilityReadinessAdapter(generic),
                        new ShopifyCapabilityReadinessAdapter(shopify, agentAuth)
                )
        );
    }

    private MerchantExecutionPolicyProperties rollouts(boolean embedded, boolean direct) {
        return new MerchantExecutionPolicyProperties(true, true, true, embedded, direct, true, true);
    }

    private GenericUcpCapabilityReadinessProperties generic(boolean directAuthorized) {
        return new GenericUcpCapabilityReadinessProperties(directAuthorized, true, false);
    }

    private ShopifyCapabilityReadinessProperties shopifyDefaults() {
        return shopify(ShopifyAuthorizationTier.NONE, Set.of(), false, false, false, false, Set.of());
    }

    private ShopifyCapabilityReadinessProperties shopify(
            ShopifyAuthorizationTier tier,
            Set<String> grantedScopes,
            boolean embeddedAdvertised,
            boolean embeddedAuthorized,
            boolean directAdvertised,
            boolean directAuthorized,
            Set<String> directScopes
    ) {
        return new ShopifyCapabilityReadinessProperties(
                tier,
                grantedScopes,
                embeddedAdvertised,
                embeddedAuthorized,
                directAdvertised,
                directAuthorized,
                directScopes,
                false,
                Set.of("read_orders"),
                false
        );
    }

    private ShopifyAgentAuthProperties disabledAgentAuth() {
        return agentAuth(false, "", "");
    }

    private ShopifyAgentAuthProperties enabledAgentAuth() {
        return agentAuth(true, "client", "secret");
    }

    private ShopifyAgentAuthProperties agentAuth(boolean enabled, String clientId, String clientSecret) {
        return new ShopifyAgentAuthProperties(
                enabled,
                "test",
                clientId,
                clientSecret,
                URI.create("https://api.shopify.com/auth/access_token"),
                Duration.ofMinutes(5),
                Duration.ofHours(1)
        );
    }

    private Merchant merchant(boolean legacyRollout) {
        return merchant(legacyRollout, null);
    }

    private Merchant merchant(boolean legacyRollout, String advertisedMcpEndpoint) {
        return Merchant.builder()
                .id(UUID.randomUUID())
                .advertisedMcpEndpoint(advertisedMcpEndpoint)
                .nativeCheckoutEnabled(legacyRollout)
                .build();
    }

    private MerchantIntegration integration(
            MerchantIntegrationProvider provider,
            MerchantIntegrationStatus status,
            Set<MerchantIntegrationRole> roles
    ) {
        Instant now = Instant.parse("2026-07-10T12:00:00Z");
        return MerchantIntegration.builder()
                .id(UUID.randomUUID())
                .provider(provider)
                .kind(MerchantIntegrationKind.MERCHANT_CONNECTION)
                .roles(roles)
                .endpoint("https://merchant.example/api/ucp/mcp")
                .authStrategy(provider == MerchantIntegrationProvider.SHOPIFY
                        ? MerchantIntegrationAuthStrategy.OAUTH_BEARER
                        : MerchantIntegrationAuthStrategy.NONE)
                .status(status)
                .source(MerchantIntegrationSource.DISCOVERY)
                .capturedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
