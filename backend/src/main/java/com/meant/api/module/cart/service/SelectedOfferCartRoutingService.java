package com.meant.api.module.cart.service;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.port.ExternalOfferCartRoutingProvider;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByIdsQuery;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SelectedOfferCartRoutingService {
    private final MerchantIntegrationLookupService integrationLookupService;
    private final MerchantCartProviderLookupService merchantProviderLookupService;
    private final List<ExternalOfferCartRoutingProvider> externalProviders;
    private final CartBindingMetrics metrics;

    public CartRoutingTarget resolve(ResolvedSelectedOffer offer) {
        LocalMerchantRouting local = offer.rehydratedReference().localRouting();
        if (local != null) {
            return local(offer, local);
        }
        List<ExternalOfferCartRoutingProvider> matching = externalProviders.stream()
                .filter(provider -> provider.supports(offer))
                .toList();
        if (matching.size() != 1) {
            throw failure(
                    matching.isEmpty()
                            ? CartException.BindingFailure.MISSING_ROUTING
                            : CartException.BindingFailure.AMBIGUOUS_ROUTING,
                    matching.isEmpty()
                            ? "Selected offer has no verified cart route"
                            : "Selected offer has ambiguous cart routing");
        }
        return matching.getFirst().resolve(offer)
                .orElseThrow(() -> failure(
                        CartException.BindingFailure.MISSING_ROUTING,
                        "Selected offer cart route is unavailable"));
    }

    public CartRoutingTarget resolvePersisted(
            MerchantIntegrationProvider expectedProvider,
            UUID integrationId,
            String externalMerchantId,
            String persistedScopeKey
    ) {
        List<MerchantIntegrationResult> integrations = integrationLookupService.listByIds(
                new ListMerchantIntegrationsByIdsQuery(Set.of(integrationId)));
        if (integrations.size() != 1) {
            throw failure(CartException.BindingFailure.MISSING_ROUTING,
                    "Stored cart integration is missing or ambiguous");
        }
        MerchantIntegrationResult integration = integrations.getFirst();
        validateIntegration(expectedProvider, externalMerchantId, integration);
        MerchantCartProvider provider = activeProvider(integration);
        String scopeKey = expectedProvider.name() + ":integration:" + integration.id();
        if (!scopeKey.equals(persistedScopeKey)) {
            throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Stored cart scope does not match its integration");
        }
        return new CartRoutingTarget(scopeKey, expectedProvider, integration.id(), externalMerchantId, provider);
    }

    public CartRoutingTarget resolvePersistedExternal(CartRoutingTarget persistedTarget) {
        List<ExternalOfferCartRoutingProvider> matching = externalProviders.stream()
                .filter(provider -> provider.supportsPersisted(persistedTarget))
                .toList();
        if (matching.size() != 1) {
            throw failure(
                    matching.isEmpty()
                            ? CartException.BindingFailure.MISSING_ROUTING
                            : CartException.BindingFailure.AMBIGUOUS_ROUTING,
                    "Stored external cart route is missing or ambiguous");
        }
        CartRoutingTarget restored = matching.getFirst().restore(persistedTarget)
                .orElseThrow(() -> failure(
                        CartException.BindingFailure.MISSING_ROUTING,
                        "Stored external cart route is unavailable"));
        if (restored.provider() != persistedTarget.provider()
                || !restored.scopeKey().equals(persistedTarget.scopeKey())
                || !Objects.equals(restored.externalMerchantId(), persistedTarget.externalMerchantId())) {
            throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Stored external cart identity changed during route restoration");
        }
        return restored;
    }

    private CartRoutingTarget local(ResolvedSelectedOffer offer, LocalMerchantRouting local) {
        List<MerchantIntegrationResult> integrations = integrationLookupService.listByIds(
                new ListMerchantIntegrationsByIdsQuery(Set.of(local.merchantIntegrationId())));
        if (integrations.size() != 1) {
            throw failure(
                    integrations.isEmpty()
                            ? CartException.BindingFailure.MISSING_ROUTING
                            : CartException.BindingFailure.AMBIGUOUS_ROUTING,
                    "Selected offer integration is missing or ambiguous");
        }
        MerchantIntegrationResult integration = integrations.getFirst();
        MerchantIntegrationProvider expectedProvider = provider(offer.identity().provider().value());
        ExternalIdentifier externalMerchant = offer.identity().merchantScope().externalMerchantIdentity();
        String externalMerchantId = externalMerchant == null ? null : externalMerchant.value();
        validateIntegration(expectedProvider, externalMerchantId, integration);
        MerchantCartProvider provider = activeProvider(integration);
        externalMerchantId = externalMerchantId == null ? integration.externalMerchantId() : externalMerchantId;
        return new CartRoutingTarget(
                expectedProvider.name() + ":integration:" + integration.id(),
                expectedProvider,
                integration.id(),
                externalMerchantId,
                provider
        );
    }

    private void validateIntegration(
            MerchantIntegrationProvider expectedProvider,
            String externalMerchantId,
            MerchantIntegrationResult integration
    ) {
        if (integration.provider() != expectedProvider
                || integration.status() != MerchantIntegrationStatus.ACTIVE
                || !integration.roles().contains(MerchantIntegrationRole.CART)) {
            throw failure(CartException.BindingFailure.MISSING_ROUTING,
                    "Selected offer integration is not eligible for cart");
        }
        if (externalMerchantId != null && integration.externalMerchantId() != null
                && !externalMerchantId.equals(integration.externalMerchantId())) {
            throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Selected offer merchant identity does not match its integration");
        }
    }

    private MerchantCartProvider activeProvider(MerchantIntegrationResult integration) {
        MerchantCartProvider provider = merchantProviderLookupService.findById(integration.merchantId())
                .orElseThrow(() -> failure(
                        CartException.BindingFailure.MISSING_ROUTING,
                        "Selected offer merchant route is unavailable"));
        if (!provider.executionPolicy().decision(CommerceOperation.CART).available()
                || !Objects.equals(provider.executionPolicy().decision(CommerceOperation.CART).integrationId(),
                        integration.id())) {
            throw failure(CartException.BindingFailure.MISSING_ROUTING,
                    "Selected offer integration is not the active cart execution route");
        }
        return provider;
    }

    private MerchantIntegrationProvider provider(String provider) {
        try {
            return MerchantIntegrationProvider.valueOf(provider);
        } catch (IllegalArgumentException exception) {
            throw failure(CartException.BindingFailure.MISSING_ROUTING,
                    "Selected offer provider is unsupported");
        }
    }

    private CartException failure(CartException.BindingFailure failure, String message) {
        metrics.record(failure);
        return CartException.binding(failure, message);
    }
}
