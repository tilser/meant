package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.support.OfferIdentityStrategy;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Restores a cart selection from a user-owned durable saved-product reference. */
@Service
@RequiredArgsConstructor
public class UserSavedProductOfferResolutionService {
    private final UserSavedProductPersistenceService persistenceService;
    private final UserSavedProductResultMapper resultMapper;
    private final CatalogDataUsePolicyResolver policyResolver;
    private final List<OfferIdentityStrategy> offerIdentityStrategies;

    Selection selection(
            UUID userId,
            SavedProductOfferKeyCodec.Selection savedOfferSelection,
            String savedOfferKey
    ) {
        UserSavedProduct saved = persistenceService.findVerified(userId, savedOfferSelection.savedProductId())
                .orElseThrow(SelectedOfferResolutionException::unknownOrExpired);
        if (!SavedProductOfferKeyCodec.verify(savedOfferSelection, saved)) {
            throw rejected(
                    SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH,
                    "Saved offer key no longer matches the durable reference");
        }
        CatalogProductReference stored = resultMapper.reference(saved);
        if (stored == null) {
            throw rejected(
                    SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH,
                    "Saved offer identifiers are invalid");
        }
        CatalogRetentionDecision policy = policyResolver.resolve(
                stored.discoverySource(), CatalogPayloadClass.SAVED_INTERACTION);
        if (policy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY
                || !policy.policyKey().equals(saved.getRetentionPolicyKey())) {
            throw rejected(
                    SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION,
                    "Saved offer retention policy is no longer eligible for cart");
        }
        if (stored.externalVariantReference() == null) {
            throw rejected(
                    SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION,
                    "Saved offer does not identify an exact variant");
        }
        return new Selection(
                saved.getProductKey(),
                savedOfferKey,
                withInteractionKey(stored, savedOfferKey)
        );
    }

    boolean eligible(Selection selection, CatalogProductRehydrationResult result) {
        if (result.status() != CatalogRehydrationStatus.FRESH || !exactIdentity(selection, result)) {
            return false;
        }
        OfferAvailabilityStatus availability = result.facts().availability().status();
        return availability != OfferAvailabilityStatus.OUT_OF_STOCK
                && availability != OfferAvailabilityStatus.DISCONTINUED
                && availability != OfferAvailabilityStatus.UNKNOWN;
    }

    boolean exactIdentity(Selection selection, CatalogProductRehydrationResult result) {
        CatalogProductReference requested = selection.reference();
        CatalogProductReference resolved = result.resolvedReference();
        return resolved != null
                && exactReference(requested, resolved)
                && Objects.equals(requested.externalVariantReference(), result.facts().selectedVariant())
                && resolved.selectedOptions().equals(result.facts().selectedOptions());
    }

    ResolvedSelectedOffer resolved(
            Selection selection,
            CatalogProductRehydrationResult result
    ) {
        if (!eligible(selection, result)) {
            throw rejected(
                    SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE,
                    "Saved offer is not currently eligible for cart");
        }
        CatalogProductReference reference = result.resolvedReference();
        ProviderIdentity provider = reference.discoverySource().provider();
        OfferMerchantScope merchantScope = merchantScope(reference);
        ExternalIdentifier productIdentity = offerIdentityStrategies.stream()
                .filter(strategy -> strategy.supports(provider))
                .findFirst()
                .map(strategy -> strategy.product(
                        provider,
                        reference.externalProductReference(),
                        reference.externalVariantReference()
                ))
                .orElse(reference.externalProductReference());
        OfferIdentity identity = new OfferIdentity(
                provider,
                merchantScope,
                productIdentity,
                reference.externalVariantReference(),
                reference.selectedOptions(),
                reference.components(),
                reference.sellingPlanIdentity()
        );
        ResultProvenance provenance = new ResultProvenance(
                provider,
                reference.discoverySource(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                result.facts().freshness(),
                new ResultSourceReference(
                        reference.discoverySource().type(),
                        reference.discoverySource().value(),
                        null
                )
        );
        return new ResolvedSelectedOffer(
                selection.canonicalProductKey(),
                selection.offerKey(),
                identity,
                provenance,
                reference,
                result.facts()
        );
    }

    private CatalogProductReference withInteractionKey(CatalogProductReference reference, String interactionKey) {
        return new CatalogProductReference(
                interactionKey,
                reference.discoverySource(),
                reference.localMerchantId(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                reference.selectedOptions(),
                reference.components(),
                reference.sellingPlanIdentity()
        );
    }

    private boolean exactReference(CatalogProductReference requested, CatalogProductReference resolved) {
        return requested.interactionKey().equals(resolved.interactionKey())
                && requested.discoverySource().equals(resolved.discoverySource())
                && localMerchantMatches(requested, resolved)
                && localRoutingMatches(requested, resolved)
                && optionalEnrichmentMatches(
                        requested.externalMerchantReference(),
                        resolved.externalMerchantReference(),
                        hasLocalAuthority(requested)
                )
                && optionalEnrichmentMatches(
                        requested.externalMerchantDomain(),
                        resolved.externalMerchantDomain(),
                        true
                )
                && requested.externalProductReference().equals(resolved.externalProductReference())
                && Objects.equals(requested.externalVariantReference(), resolved.externalVariantReference())
                && selectedOptionsMatch(requested, resolved)
                && requested.components().equals(resolved.components())
                && Objects.equals(requested.sellingPlanIdentity(), resolved.sellingPlanIdentity());
    }

    private boolean selectedOptionsMatch(CatalogProductReference requested, CatalogProductReference resolved) {
        if (requested.selectedOptions().equals(resolved.selectedOptions())) {
            return true;
        }
        return requested.selectedOptions().isEmpty()
                && requested.externalVariantReference() != null
                && requested.externalVariantReference().equals(resolved.externalVariantReference());
    }

    private boolean localMerchantMatches(CatalogProductReference requested, CatalogProductReference resolved) {
        if (requested.localMerchantId() != null) {
            return requested.localMerchantId().equals(resolved.localMerchantId());
        }
        return resolved.localMerchantId() == null || requested.localRouting() != null;
    }

    private boolean localRoutingMatches(CatalogProductReference requested, CatalogProductReference resolved) {
        if (requested.localRouting() != null) {
            return requested.localRouting().equals(resolved.localRouting());
        }
        return resolved.localRouting() == null || requested.localMerchantId() != null;
    }

    private <T> boolean optionalEnrichmentMatches(T requested, T resolved, boolean enrichmentAllowed) {
        return requested == null ? enrichmentAllowed || resolved == null : requested.equals(resolved);
    }

    private boolean hasLocalAuthority(CatalogProductReference reference) {
        return reference.localMerchantId() != null || reference.localRouting() != null;
    }

    private OfferMerchantScope merchantScope(CatalogProductReference reference) {
        if (reference.externalMerchantReference() != null) {
            return OfferMerchantScope.external(reference.externalMerchantReference());
        }
        if (reference.localRouting() != null) {
            return OfferMerchantScope.localIntegrationFallback(
                    reference.localRouting().merchantIntegrationId());
        }
        throw rejected(
                SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION,
                "Saved offer has no verified merchant identity or route");
    }

    private SelectedOfferResolutionException rejected(
            SelectedOfferResolutionException.Failure failure,
            String message
    ) {
        return SelectedOfferResolutionException.rejected(failure, message);
    }

    record Selection(
            String canonicalProductKey,
            String offerKey,
            CatalogProductReference reference
    ) {
    }
}
