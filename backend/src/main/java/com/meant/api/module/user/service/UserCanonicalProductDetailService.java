package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserCanonicalProductDetailService {
    private final UserCanonicalProductSessionStore sessionStore;
    private final CatalogProductRehydrationService rehydrationService;
    private final UserSettingsService userSettingsService;

    public UserProductDetailResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetUserCanonicalProductDetailQuery query
    ) {
        if (!profileCommand.id().equals(query.userId())) {
            throw UserException.forbidden("Product detail user does not match authenticated user");
        }
        UserCanonicalProductSessionStore.Entry entry = sessionStore.find(query.userId(), query.canonicalProductKey())
                .orElseThrow(() -> UserException.notFound("Canonical product is unknown or expired"));
        CanonicalProduct product = entry.product();
        String recommendedOfferKey = product.offers().getFirst().key();
        String selectedOfferKey = query.selectedOfferKey() == null ? recommendedOfferKey : query.selectedOfferKey();
        if (product.offers().stream().noneMatch(offer -> offer.key().equals(selectedOfferKey))) {
            throw UserException.notFound("Selected offer does not belong to the canonical product");
        }

        UserSettingsResult settings = userSettingsService.get(profileCommand);
        CatalogRehydrationContext context = new CatalogRehydrationContext(
                settings.location() == null ? null : settings.location().code(), null);
        List<OfferReference> references = references(product);
        List<CatalogProductRehydrationResult> results = rehydrationService.rehydrate(
                references.stream().map(OfferReference::reference).toList(), context);
        Map<CatalogProductReference, CatalogProductRehydrationResult> byReference = results.stream()
                .collect(Collectors.toMap(CatalogProductRehydrationResult::reference, Function.identity()));

        Map<String, UserOfferCommercialState> states = new LinkedHashMap<>();
        List<Offer> offers = product.offers().stream()
                .map(offer -> refreshed(offer, references, byReference, states))
                .toList();
        List<UserCatalogSourceState> sourceStates = new ArrayList<>(entry.sourceStates());
        results.stream()
                .filter(result -> result.status() != CatalogRehydrationStatus.FRESH)
                .map(result -> new UserCatalogSourceState(
                        result.reference().discoverySource(),
                        CatalogSourceOperation.GET_PRODUCT,
                        true,
                        false,
                        null,
                        result.failure(),
                        null
                ))
                .distinct()
                .forEach(sourceStates::add);
        Map<String, Offer> offersByKey = product.offers().stream()
                .collect(Collectors.toMap(Offer::key, Function.identity()));
        references.stream()
                .filter(reference -> {
                    CatalogProductRehydrationResult result = byReference.get(reference.reference());
                    return result != null
                            && result.status() == CatalogRehydrationStatus.FRESH
                            && !exactIdentity(offersByKey.get(reference.offerKey()), result);
                })
                .map(reference -> new UserCatalogSourceState(
                        reference.reference().discoverySource(),
                        CatalogSourceOperation.GET_PRODUCT,
                        true,
                        false,
                        null,
                        CatalogRehydrationFailureKind.INVALID_RESPONSE,
                        null
                ))
                .distinct()
                .forEach(sourceStates::add);
        return new UserProductDetailResult(
                product.withOffers(offers),
                recommendedOfferKey,
                selectedOfferKey,
                entry.productExplanation(),
                entry.offerExplanations(),
                states,
                sourceStates
        );
    }

    private List<OfferReference> references(CanonicalProduct product) {
        List<OfferReference> references = new ArrayList<>();
        for (Offer offer : product.offers()) {
            for (int index = 0; index < offer.provenance().size(); index++) {
                ResultProvenance provenance = offer.provenance().get(index);
                references.add(new OfferReference(offer.key(), new CatalogProductReference(
                        offer.key() + ":" + index,
                        provenance.discoverySource(),
                        null,
                        provenance.localRouting(),
                        provenance.externalMerchantReference(),
                        provenance.externalProductReference(),
                        provenance.externalVariantReference(),
                        offer.selectedOptions()
                )));
            }
        }
        return List.copyOf(references);
    }

    private Offer refreshed(
            Offer offer,
            List<OfferReference> references,
            Map<CatalogProductReference, CatalogProductRehydrationResult> results,
            Map<String, UserOfferCommercialState> states
    ) {
        List<CatalogProductRehydrationResult> offerResults = references.stream()
                .filter(reference -> reference.offerKey().equals(offer.key()))
                .map(reference -> results.get(reference.reference()))
                .filter(java.util.Objects::nonNull)
                .toList();
        CatalogProductRehydrationResult fresh = offerResults.stream()
                .filter(result -> result.status() == CatalogRehydrationStatus.FRESH)
                .filter(result -> exactIdentity(offer, result))
                .min(Comparator.comparing(result -> sourceKey(result.reference())))
                .orElse(null);
        if (fresh == null) {
            CatalogProductRehydrationResult failure = offerResults.stream()
                    .filter(result -> result.status() != CatalogRehydrationStatus.FRESH)
                    .findFirst()
                    .orElse(null);
            ResultFreshness observed = latestFreshness(offer);
            states.put(offer.key(), new UserOfferCommercialState(
                    UserOfferCommercialState.Authority.DISCOVERY_OBSERVATION,
                    failure == null ? CatalogRehydrationStatus.DEGRADED : failure.status(),
                    failure == null ? CatalogRehydrationFailureKind.INVALID_RESPONSE
                            : failure.failure(),
                    offer.price() == null ? null : observed,
                    observed,
                    offer.delivery().isEmpty() ? null : observed
            ));
            return offer;
        }
        CommercialFactsFreshness freshness = fresh.facts().purchaseFreshness();
        states.put(offer.key(), new UserOfferCommercialState(
                UserOfferCommercialState.Authority.REHYDRATED_CURRENT,
                CatalogRehydrationStatus.FRESH,
                null,
                freshness.price(),
                freshness.availability(),
                freshness.fulfillment()
        ));
        return new Offer(
                offer.identity(), offer.merchantName(), offer.variantTitle(), fresh.facts().price(), offer.listPrice(),
                fresh.facts().availability(), fresh.facts().fulfillment(), offer.checkoutUrl(),
                offer.rankingEvidence(), offer.provenance());
    }

    private boolean exactIdentity(Offer offer, CatalogProductRehydrationResult result) {
        CatalogProductReference resolved = result.resolvedReference();
        return resolved.externalProductReference().equals(offer.identity().externalProductIdentity())
                && java.util.Objects.equals(
                        resolved.externalVariantReference(), offer.identity().externalVariantIdentity())
                && resolved.selectedOptions().equals(offer.selectedOptions());
    }

    private ResultFreshness latestFreshness(Offer offer) {
        return offer.provenance().stream()
                .map(ResultProvenance::freshness)
                .max(Comparator.comparing(ResultFreshness::observedAt))
                .orElseThrow();
    }

    private String sourceKey(CatalogProductReference reference) {
        var source = reference.discoverySource();
        return source.provider().value() + "\n" + source.type().name() + "\n" + source.value();
    }

    private record OfferReference(String offerKey, CatalogProductReference reference) {
    }
}
