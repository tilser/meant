package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.user.service.dto.UserCanonicalProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Rehydrates any number of canonical products in one provider-neutral batch without persisting remote facts. */
@Service
@RequiredArgsConstructor
public class UserCanonicalProductRehydrationService {

    private final CatalogProductRehydrationService rehydrationService;

    public List<UserCanonicalProductRehydrationResult> rehydrate(
            List<CanonicalProduct> products,
            CatalogRehydrationContext context
    ) {
        List<CanonicalProduct> requestedProducts = products == null
                ? List.of()
                : products.stream().filter(Objects::nonNull).toList();
        List<OfferReference> references = requestedProducts.stream()
                .flatMap(product -> references(product).stream())
                .toList();
        List<OfferObservation> observations;
        if (references.isEmpty()) {
            observations = List.of();
        } else {
            observations = pair(
                    references,
                    rehydrationService.rehydrate(
                            references.stream().map(OfferReference::reference).toList(), context)
            );
        }
        return requestedProducts.stream()
                .map(product -> current(product, observations.stream()
                        .filter(observation -> observation.productKey().equals(product.key()))
                        .toList()))
                .toList();
    }

    private UserCanonicalProductRehydrationResult current(
            CanonicalProduct product,
            List<OfferObservation> observations
    ) {
        Map<String, UserOfferCommercialState> states = new LinkedHashMap<>();
        List<Offer> offers = product.offers().stream()
                .map(offer -> refreshed(offer, observations, states))
                .toList();
        boolean currentFactsAvailable = observations.stream()
                .anyMatch(observation -> observation.result().status() == CatalogRehydrationStatus.FRESH
                        && exactReference(observation));
        return new UserCanonicalProductRehydrationResult(
                currentProduct(product, offers, observations),
                currentFactsAvailable,
                states,
                sourceStates(observations)
        );
    }

    private List<OfferReference> references(CanonicalProduct product) {
        List<OfferReference> references = new ArrayList<>();
        for (Offer offer : product.offers()) {
            for (int index = 0; index < offer.provenance().size(); index++) {
                ResultProvenance provenance = offer.provenance().get(index);
                references.add(new OfferReference(
                        product.key(),
                        offer.key(),
                        new CatalogProductReference(
                                product.key() + ":" + offer.key() + ":" + index,
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
                        )
                ));
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
                .filter(observation -> observation.offerKey().equals(offer.key()))
                .toList();
        OfferObservation freshObservation = offerObservations.stream()
                .filter(observation -> observation.result().status() == CatalogRehydrationStatus.FRESH)
                .filter(this::exactReference)
                .min(Comparator.comparing(observation -> sourceKey(observation.reference())))
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
                    failure == null ? CatalogRehydrationFailureKind.INVALID_RESPONSE : failure.failure()));
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
                offer.identity(),
                firstText(fresh.facts().merchantName(), offer.merchantName()),
                offer.variantTitle(),
                fresh.facts().price(),
                offer.listPrice(),
                fresh.facts().availability(),
                fresh.facts().fulfillment(),
                offer.checkoutUrl(),
                offer.rankingEvidence(),
                offer.provenance()
        );
    }

    private CanonicalProduct currentProduct(
            CanonicalProduct product,
            List<Offer> offers,
            List<OfferObservation> observations
    ) {
        var facts = observations.stream()
                .filter(observation -> observation.result().status() == CatalogRehydrationStatus.FRESH)
                .filter(this::exactReference)
                .sorted(Comparator.comparing(observation -> sourceKey(observation.reference())))
                .map(observation -> observation.result().facts())
                .findFirst()
                .orElse(null);
        if (facts == null) {
            return product.withOffers(offers);
        }
        return new CanonicalProduct(
                product.key(),
                firstText(product.title(), facts.title()),
                product.description(),
                product.media().isEmpty() ? facts.sourceMedia() : product.media(),
                product.attributes(),
                product.materials(),
                product.certifications(),
                product.attribution(),
                product.identityEvidence(),
                product.provenance(),
                product.retrievalSignals(),
                offers
        );
    }

    private List<UserCatalogSourceState> sourceStates(List<OfferObservation> observations) {
        List<UserCatalogSourceState> states = new ArrayList<>();
        observations.stream()
                .filter(observation -> observation.result().status() != CatalogRehydrationStatus.FRESH)
                .map(observation -> new UserCatalogSourceState(
                        observation.reference().discoverySource(),
                        CatalogSourceOperation.GET_PRODUCT,
                        true,
                        false,
                        null,
                        observation.result().failure(),
                        null
                ))
                .distinct()
                .forEach(states::add);
        observations.stream()
                .filter(observation -> observation.result().status() == CatalogRehydrationStatus.FRESH
                        && !exactReference(observation))
                .map(observation -> new UserCatalogSourceState(
                        observation.reference().discoverySource(),
                        CatalogSourceOperation.GET_PRODUCT,
                        true,
                        false,
                        null,
                        CatalogRehydrationFailureKind.INVALID_RESPONSE,
                        null
                ))
                .distinct()
                .forEach(states::add);
        return List.copyOf(states);
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
        CatalogProductReference requested = observation.reference();
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

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second;
    }

    private record OfferReference(String productKey, String offerKey, CatalogProductReference reference) {
    }

    private record OfferObservation(OfferReference requested, CatalogProductRehydrationResult result) {
        String productKey() {
            return requested.productKey();
        }

        String offerKey() {
            return requested.offerKey();
        }

        CatalogProductReference reference() {
            return requested.reference();
        }
    }
}
