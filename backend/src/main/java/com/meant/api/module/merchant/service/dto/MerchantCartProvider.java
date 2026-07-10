package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CommerceOperation;
import java.util.List;
import java.util.UUID;

public record MerchantCartProvider(
        UUID merchantId,
        String domain,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint,
        List<MerchantIntegrationRouting> integrations,
        MerchantExecutionPolicy executionPolicy
) {

    public MerchantCartProvider {
        integrations = integrations == null ? List.of() : List.copyOf(integrations);
        executionPolicy = executionPolicy == null ? MerchantExecutionPolicy.unavailable() : executionPolicy;
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
                MerchantExecutionPolicy.unavailable()
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
                MerchantExecutionPolicy.legacyNativeCheckout(nativeCheckoutEnabled)
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
                executionPolicy
        );
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
