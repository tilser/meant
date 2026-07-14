package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
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
import java.util.Objects;
import java.util.stream.IntStream;
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
                settings.location() == null
                        ? null
                        : CountryCodeNormalizer.normalizeAlpha2(settings.location().code()),
                null);
        List<OfferReference> references = references(product);
        List<CatalogProductRehydrationResult> results = rehydrationService.rehydrate(
                references.stream().map(OfferReference::reference).toList(), context);
        List<OfferObservation> observations = pair(references, results);

        Map<String, UserOfferCommercialState> states = new LinkedHashMap<>();
        List<Offer> offers = product.offers().stream()
                .map(offer -> refreshed(offer, observations, states))
                .toList();
        List<UserCatalogSourceState> sourceStates = new ArrayList<>(entry.sourceStates());
        observations.stream()
                .filter(observation -> observation.result().status() != CatalogRehydrationStatus.FRESH)
                .map(observation -> new UserCatalogSourceState(
                        observation.requested().reference().discoverySource(),
                        CatalogSourceOperation.GET_PRODUCT,
                        true,
                        false,
                        null,
                        observation.result().failure(),
                        null
                ))
                .distinct()
                .forEach(sourceStates::add);
        observations.stream()
                .filter(observation -> observation.result().status() == CatalogRehydrationStatus.FRESH
                        && !exactReference(observation))
                .map(observation -> new UserCatalogSourceState(
                        observation.requested().reference().discoverySource(),
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
                        provenance.externalMerchantDomain(),
                        provenance.externalProductReference(),
                        provenance.externalVariantReference(),
                        offer.selectedOptions(),
                        offer.identity().components(),
                        offer.identity().sellingPlanIdentity()
                )));
            }
        }
        return List.copyOf(references);
    }

    private Offer refreshed(
            Offer offer,
            List<OfferObservation> observations,
            Map<String, UserOfferCommercialState> states
    ) {
        List<OfferObservation> offerObservations = observations.stream()
                .filter(observation -> observation.requested().offerKey().equals(offer.key()))
                .toList();
        OfferObservation freshObservation = offerObservations.stream()
                .filter(observation -> observation.result().status() == CatalogRehydrationStatus.FRESH)
                .filter(this::exactReference)
                .min(Comparator.comparing(observation -> sourceKey(observation.requested().reference())))
                .orElse(null);
        if (freshObservation == null) {
            CatalogProductRehydrationResult failure = offerObservations.stream()
                    .map(OfferObservation::result)
                    .filter(result -> result.status() != CatalogRehydrationStatus.FRESH)
                    .findFirst()
                    .orElse(null);
            states.put(offer.key(), UserOfferCommercialState.discovery(
                    offer,
                    failure == null ? CatalogRehydrationStatus.DEGRADED : failure.status(),
                    failure == null ? CatalogRehydrationFailureKind.INVALID_RESPONSE
                            : failure.failure()));
            return offer;
        }
        CatalogProductRehydrationResult fresh = freshObservation.result();
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

    private List<OfferObservation> pair(
            List<OfferReference> references,
            List<CatalogProductRehydrationResult> results
    ) {
        if (references.size() != results.size()) {
            throw new IllegalStateException("Rehydration service must return one result per requested reference");
        }
        return IntStream.range(0, references.size())
                .mapToObj(index -> new OfferObservation(references.get(index), results.get(index)))
                .toList();
    }

    private boolean exactReference(OfferObservation observation) {
        CatalogProductReference requested = observation.requested().reference();
        CatalogProductReference resolved = observation.result().resolvedReference();
        return requested.interactionKey().equals(resolved.interactionKey())
                && requested.discoverySource().equals(resolved.discoverySource())
                && localMerchantMatches(requested, resolved)
                && Objects.equals(requested.localRouting(), resolved.localRouting())
                && Objects.equals(requested.externalMerchantReference(), resolved.externalMerchantReference())
                && requested.externalProductReference().equals(resolved.externalProductReference())
                && Objects.equals(requested.externalVariantReference(), resolved.externalVariantReference())
                && requested.selectedOptions().equals(resolved.selectedOptions())
                && requested.components().equals(resolved.components())
                && Objects.equals(requested.sellingPlanIdentity(), resolved.sellingPlanIdentity());
    }

    private boolean localMerchantMatches(
            CatalogProductReference requested,
            CatalogProductReference resolved
    ) {
        if (Objects.equals(requested.localMerchantId(), resolved.localMerchantId())) {
            return true;
        }
        return requested.localMerchantId() == null
                && requested.localRouting() != null
                && resolved.localMerchantId() != null;
    }

    private String sourceKey(CatalogProductReference reference) {
        var source = reference.discoverySource();
        return source.provider().value() + "\n" + source.type().name() + "\n" + source.value();
    }

    private record OfferReference(String offerKey, CatalogProductReference reference) {
    }

    private record OfferObservation(OfferReference requested, CatalogProductRehydrationResult result) {
    }
}
