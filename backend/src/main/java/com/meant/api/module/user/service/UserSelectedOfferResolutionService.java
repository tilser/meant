package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.query.ResolveUserSelectedOfferQuery;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Resolves an authenticated server-issued live or saved offer key to a current executable identity. */
@Service
@Validated
@RequiredArgsConstructor
public class UserSelectedOfferResolutionService {
    private final UserCanonicalProductSessionStore sessionStore;
    private final CatalogProductRehydrationService rehydrationService;
    private final UserSavedProductOfferResolutionService savedProductOfferResolutionService;
    private final SelectedOfferResolutionMetrics metrics;

    public ResolvedSelectedOffer resolve(@NotNull @Valid ResolveUserSelectedOfferQuery query) {
        return resolveAll(new ResolveUserSelectedOffersQuery(
                query.userId(), List.of(query.offerKey()), query.countryCode())).getFirst();
    }

    public List<ResolvedSelectedOffer> resolveAll(@NotNull @Valid ResolveUserSelectedOffersQuery query) {
        List<Selection> selections = query.offerKeys().stream()
                .map(offerKey -> selection(query.userId(), offerKey))
                .toList();
        List<OwnedReference> references = java.util.stream.IntStream.range(0, selections.size())
                .boxed()
                .flatMap(index -> selections.get(index).references().stream()
                        .map(reference -> new OwnedReference(index, reference)))
                .toList();
        List<CatalogProductRehydrationResult> results = rehydrationService.rehydrate(
                references.stream().map(value -> value.reference().reference()).toList(),
                new CatalogRehydrationContext(query.countryCode(), null)
        );
        if (results.size() != references.size()) {
            throw failure(SelectedOfferResolutionException.rejected(
                    SelectedOfferResolutionException.Failure.PROVIDER_FAILURE,
                    "Offer rehydration returned an incomplete result set"));
        }
        List<OwnedResolved> resolved = java.util.stream.IntStream.range(0, results.size())
                .mapToObj(index -> new OwnedResolved(
                        references.get(index).selectionIndex(),
                        new Resolved(references.get(index).reference(), results.get(index))))
                .toList();
        return java.util.stream.IntStream.range(0, selections.size())
                .mapToObj(index -> resolveSelection(selections.get(index), resolved.stream()
                        .filter(value -> value.selectionIndex() == index)
                        .map(OwnedResolved::resolved)
                        .toList()))
                .toList();
    }

    private Selection selection(UUID userId, String offerKey) {
        var savedSelection = SavedProductOfferKeyCodec.decode(offerKey);
        if (savedSelection.isPresent()) {
            UserSavedProductOfferResolutionService.Selection saved;
            try {
                saved = savedProductOfferResolutionService.selection(
                        userId, savedSelection.get(), offerKey);
            } catch (SelectedOfferResolutionException exception) {
                throw failure(exception);
            }
            return new Selection(
                    null,
                    null,
                    saved,
                    List.of(new Reference(null, saved.reference(), saved))
            );
        }
        UserCanonicalProductSessionStore.OfferEntry entry = sessionStore.findOffer(userId, offerKey)
                .orElseThrow(() -> failure(sessionStore.isOfferOwnedByAnotherUser(userId, offerKey)
                        ? SelectedOfferResolutionException.wrongUser()
                        : SelectedOfferResolutionException.unknownOrExpired()));
        Offer offer = entry.offer();
        List<Reference> references = offer.provenance().stream()
                .map(provenance -> new Reference(provenance, reference(offer, provenance), null))
                .toList();
        return new Selection(entry, offer, null, references);
    }

    private ResolvedSelectedOffer resolveSelection(Selection selection, List<Resolved> resolved) {
        List<Resolved> eligible = resolved.stream()
                .filter(this::eligible)
                .sorted(Comparator.comparing(value -> sourceKey(value.reference())))
                .toList();
        if (eligible.isEmpty()) {
            boolean freshMismatch = resolved.stream()
                    .anyMatch(value -> value.result().status() == CatalogRehydrationStatus.FRESH
                            && !exactIdentity(value.reference(), value.result()));
            throw failure(SelectedOfferResolutionException.rejected(
                    freshMismatch
                            ? SelectedOfferResolutionException.Failure.IDENTITY_MISMATCH
                            : SelectedOfferResolutionException.Failure.STALE_OR_UNAVAILABLE,
                    freshMismatch ? "Rehydrated offer identity did not match selection" : "Offer is not currently available"));
        }
        Resolved selected = eligible.getFirst();
        if (eligible.stream().skip(1).anyMatch(value -> !sameRouting(eligible.getFirst(), value))) {
            throw failure(SelectedOfferResolutionException.rejected(
                    SelectedOfferResolutionException.Failure.AMBIGUOUS_PROVENANCE,
                    "Selected offer has ambiguous executable routing"));
        }
        if (selection.saved() != null) {
            try {
                ResolvedSelectedOffer saved = savedProductOfferResolutionService.resolved(
                        selection.saved(), selected.result());
                metrics.recordSuccess();
                return saved;
            } catch (SelectedOfferResolutionException exception) {
                throw failure(exception);
            }
        }
        metrics.recordSuccess();
        return new ResolvedSelectedOffer(
                selection.entry().canonicalProductKey(), selection.offer().key(), selection.offer().identity(),
                selected.reference().provenance(), selected.result().resolvedReference(), selected.result().facts());
    }

    private CatalogProductReference reference(Offer offer, ResultProvenance provenance) {
        return new CatalogProductReference(
                offer.key(), provenance.discoverySource(), null, provenance.localRouting(),
                provenance.externalMerchantReference(), provenance.externalMerchantDomain(),
                provenance.externalProductReference(),
                provenance.externalVariantReference(),
                offer.selectedOptions(),
                offer.identity().components(),
                offer.identity().sellingPlanIdentity());
    }

    private boolean eligible(Resolved value) {
        if (value.reference().saved() != null) {
            return savedProductOfferResolutionService.eligible(value.reference().saved(), value.result());
        }
        CatalogProductRehydrationResult result = value.result();
        return result.status() == CatalogRehydrationStatus.FRESH
                && exactIdentity(value.reference().reference(), result)
                && result.facts().availability().status() != OfferAvailabilityStatus.OUT_OF_STOCK
                && result.facts().availability().status() != OfferAvailabilityStatus.DISCONTINUED
                && result.facts().availability().status() != OfferAvailabilityStatus.UNKNOWN;
    }

    private boolean exactIdentity(CatalogProductReference requested, CatalogProductRehydrationResult result) {
        if (!exact(requested, result.resolvedReference())) {
            return false;
        }
        if (requested.externalVariantReference() != null
                && !Objects.equals(requested.externalVariantReference(), result.facts().selectedVariant())) {
            return false;
        }
        return requested.selectedOptions().equals(result.facts().selectedOptions());
    }

    private boolean exactIdentity(Reference requested, CatalogProductRehydrationResult result) {
        return requested.saved() == null
                ? exactIdentity(requested.reference(), result)
                : savedProductOfferResolutionService.exactIdentity(requested.saved(), result);
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

    private boolean sameRouting(Resolved first, Resolved second) {
        CatalogProductReference a = first.result().resolvedReference();
        CatalogProductReference b = second.result().resolvedReference();
        return Objects.equals(a.localRouting(), b.localRouting())
                && Objects.equals(a.externalMerchantReference(), b.externalMerchantReference())
                && Objects.equals(a.externalMerchantDomain(), b.externalMerchantDomain())
                && a.discoverySource().provider().equals(b.discoverySource().provider());
    }

    private String sourceKey(Reference reference) {
        var source = reference.reference().discoverySource();
        return source.provider().value() + "\n" + source.type() + "\n" + source.value();
    }

    private SelectedOfferResolutionException failure(SelectedOfferResolutionException exception) {
        metrics.record(exception.getFailure());
        return exception;
    }

    private record Reference(
            ResultProvenance provenance,
            CatalogProductReference reference,
            UserSavedProductOfferResolutionService.Selection saved
    ) {
    }

    private record Resolved(Reference reference, CatalogProductRehydrationResult result) {
    }

    private record Selection(
            UserCanonicalProductSessionStore.OfferEntry entry,
            Offer offer,
            UserSavedProductOfferResolutionService.Selection saved,
            List<Reference> references
    ) {
    }

    private record OwnedReference(int selectionIndex, Reference reference) {
    }

    private record OwnedResolved(int selectionIndex, Resolved resolved) {
    }
}
