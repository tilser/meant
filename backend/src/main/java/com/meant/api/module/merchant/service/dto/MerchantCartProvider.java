package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CommerceOperation;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

public record MerchantCartProvider(
        UUID merchantId,
        String domain,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint,
        List<MerchantIntegrationRouting> integrations,
        MerchantExecutionPolicy executionPolicy,
        Instant profileCapturedAt,
        Set<String> advertisedCapabilities
) {

    public MerchantCartProvider {
        integrations = integrations == null ? List.of() : List.copyOf(integrations);
        executionPolicy = executionPolicy == null ? MerchantExecutionPolicy.unavailable() : executionPolicy;
        advertisedCapabilities = advertisedCapabilities == null ? Set.of() : Set.copyOf(advertisedCapabilities);
    }

    public MerchantCartProvider(
            UUID merchantId,
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            List<MerchantIntegrationRouting> integrations,
            MerchantExecutionPolicy executionPolicy,
            Instant profileCapturedAt
    ) {
        this(merchantId, domain, advertisedMcpEndpoint, profileMcpEndpoint, integrations, executionPolicy,
                profileCapturedAt, Set.of());
    }

    public MerchantCartProvider(
            UUID merchantId,
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            List<MerchantIntegrationRouting> integrations,
            MerchantExecutionPolicy executionPolicy
    ) {
        this(merchantId, domain, advertisedMcpEndpoint, profileMcpEndpoint, integrations, executionPolicy, null,
                Set.of());
    }

    public MerchantCartProvider(
            UUID merchantId,
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint
    ) {
        this(
                merchantId,
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint,
                List.of(),
                MerchantExecutionPolicy.unavailable(),
                null,
                Set.of()
        );
    }

    /**
     * Temporary compatibility constructor for internal callers that still supply the legacy flag.
     * New routing code must pass an integration-backed execution policy.
     */
    @Deprecated(forRemoval = true)
    public MerchantCartProvider(
            UUID merchantId,
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            boolean nativeCheckoutEnabled
    ) {
        this(
                merchantId,
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint,
                List.of(),
                MerchantExecutionPolicy.legacyNativeCheckout(nativeCheckoutEnabled),
                null,
                Set.of()
        );
    }

    public String advertisedMcpEndpoint(CommerceOperation operation) {
        UUID integrationId = executionPolicy.decision(operation).integrationId();
        if (integrationId == null) {
            return advertisedMcpEndpoint;
        }
        return integrations.stream()
                .filter(integration -> integrationId.equals(integration.integrationId()))
                .map(MerchantIntegrationRouting::endpoint)
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .findFirst()
                .orElse(advertisedMcpEndpoint);
    }

    public MerchantCartProvider forOperation(CommerceOperation operation) {
        return new MerchantCartProvider(
                merchantId,
                domain,
                advertisedMcpEndpoint(operation),
                profileMcpEndpoint,
                integrations,
                executionPolicy,
                profileCapturedAt,
                advertisedCapabilities
        );
    }

    public MerchantCartProvider forIntegration(UUID integrationId) {
        if (integrationId == null) {
            return this;
        }
        String endpoint = integrations.stream()
                .filter(integration -> integrationId.equals(integration.integrationId()))
                .map(MerchantIntegrationRouting::endpoint)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Merchant integration endpoint is unavailable"));
        return new MerchantCartProvider(
                merchantId, domain, endpoint, profileMcpEndpoint, integrations, executionPolicy,
                profileCapturedAt, advertisedCapabilities);
    }

    /**
     * Deprecated compatibility view. It is derived from the direct-completion policy and is not an
     * input to migrated checkout routing.
     */
    @Deprecated(forRemoval = true)
    public boolean nativeCheckoutEnabled() {
        return executionPolicy.isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
    }
}
