package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Batched current-eligibility gate run before mutating an existing remote cart. */
@Service
@RequiredArgsConstructor
public class CartOfferRevalidationService {
    private static final TypeReference<List<ProductAttribute>> OPTIONS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<OfferComponentIdentity>> COMPONENTS_TYPE = new TypeReference<>() {
    };

    private final CatalogProductRehydrationService rehydrationService;
    private final ObjectMapper objectMapper;
    private final CartBindingMetrics metrics;

    public void revalidate(Cart cart, String countryCode) {
        List<CartLine> boundLines = cart.getLines().stream().filter(line -> line.getOfferKey() != null).toList();
        if (boundLines.isEmpty()) {
            return;
        }
        List<CatalogProductReference> references = boundLines.stream().map(line -> reference(cart, line)).toList();
        List<CatalogProductRehydrationResult> results = rehydrationService.rehydrate(
                references, new CatalogRehydrationContext(countryCode, null));
        if (results.size() != references.size()) {
            throw failure(CartException.BindingFailure.PROVIDER_FAILURE,
                    "Cart offer revalidation returned an incomplete result set");
        }
        for (int index = 0; index < results.size(); index++) {
            CatalogProductRehydrationResult result = results.get(index);
            if (result.status() != CatalogRehydrationStatus.FRESH) {
                throw failure(CartException.BindingFailure.STALE_OR_UNAVAILABLE,
                        "A cart offer is stale or unavailable");
            }
            if (!exact(references.get(index), result.resolvedReference())) {
                throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                        "A cart offer identity no longer matches its selection");
            }
            if (references.get(index).externalVariantReference() != null
                    && !Objects.equals(
                            references.get(index).externalVariantReference(), result.facts().selectedVariant())) {
                throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                        "A cart offer selected variant no longer matches its selection");
            }
            if (!references.get(index).selectedOptions().equals(result.facts().selectedOptions())) {
                throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                        "A cart offer selected options no longer match its selection");
            }
            OfferAvailabilityStatus availability = result.facts().availability().status();
            if (availability == OfferAvailabilityStatus.OUT_OF_STOCK
                    || availability == OfferAvailabilityStatus.DISCONTINUED
                    || availability == OfferAvailabilityStatus.UNKNOWN) {
                throw failure(CartException.BindingFailure.STALE_OR_UNAVAILABLE,
                        "A cart offer is not currently available");
            }
        }
    }

    private CatalogProductReference reference(Cart cart, CartLine line) {
        ProviderIdentity provider = new ProviderIdentity(line.getProvider());
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.valueOf(line.getSourceType()), line.getSourceIdentity());
        return new CatalogProductReference(
                line.getOfferKey(), source, null,
                line.getMerchantIntegrationId() == null ? null : new LocalMerchantRouting(line.getMerchantIntegrationId()),
                ExternalIdentifier.optional(ExternalIdentifierType.MERCHANT, provider.value(), line.getExternalMerchantId()),
                routingDomain(cart),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, provider.value(), line.getExternalProductId()),
                ExternalIdentifier.optional(ExternalIdentifierType.VARIANT, provider.value(), line.getExternalVariantId()),
                options(line.getSelectedOptionsJson()),
                components(line.getComponentsJson()),
                sellingPlan(line.getSellingPlanJson())
        );
    }

    private String routingDomain(Cart cart) {
        return cart.getRoutingDomain() == null || cart.getRoutingDomain().isBlank()
                ? cart.getMerchantDomain()
                : cart.getRoutingDomain();
    }

    private List<ProductAttribute> options(String json) {
        return list(json, OPTIONS_TYPE, "Stored cart offer options are invalid");
    }

    private List<OfferComponentIdentity> components(String json) {
        return list(json, COMPONENTS_TYPE, "Stored cart offer components are invalid");
    }

    private <T> List<T> list(String json, TypeReference<List<T>> type, String message) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(json, type);
            return values == null ? List.of() : values;
        } catch (JacksonException exception) {
            throw failure(CartException.BindingFailure.IDENTITY_MISMATCH, message);
        }
    }

    private SellingPlanIdentity sellingPlan(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SellingPlanIdentity.class);
        } catch (JacksonException exception) {
            throw failure(CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Stored cart offer selling plan is invalid");
        }
    }

    private boolean exact(CatalogProductReference requested, CatalogProductReference resolved) {
        return requested.interactionKey().equals(resolved.interactionKey())
                && requested.discoverySource().equals(resolved.discoverySource())
                && Objects.equals(requested.localRouting(), resolved.localRouting())
                && Objects.equals(requested.externalMerchantReference(), resolved.externalMerchantReference())
                && Objects.equals(requested.externalMerchantDomain(), resolved.externalMerchantDomain())
                && requested.externalProductReference().equals(resolved.externalProductReference())
                && Objects.equals(requested.externalVariantReference(), resolved.externalVariantReference())
                && requested.selectedOptions().equals(resolved.selectedOptions())
                && requested.components().equals(resolved.components())
                && Objects.equals(requested.sellingPlanIdentity(), resolved.sellingPlanIdentity());
    }

    private CartException failure(CartException.BindingFailure failure, String message) {
        metrics.record(failure);
        return failure == CartException.BindingFailure.PROVIDER_FAILURE
                ? CartException.bindingUpstream(message, null)
                : CartException.binding(failure, message);
    }
}
