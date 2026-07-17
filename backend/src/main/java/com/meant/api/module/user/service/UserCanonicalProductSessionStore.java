package com.meant.api.module.user.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Session-only catalog references for canonical-key detail navigation; nothing is persisted. */
@Component
public class UserCanonicalProductSessionStore {
    private final Cache<Key, Entry> entries;
    private final Cache<OfferKey, OfferEntry> offers;
    private final Cache<String, UUID> recentOfferOwners;

    @Autowired
    public UserCanonicalProductSessionStore(UserProductSearchProperties properties) {
        this(properties.productDetailSessionTtl(), properties.productDetailSessionMaximumSize());
    }

    UserCanonicalProductSessionStore(Duration ttl, long maximumSize) {
        entries = Caffeine.newBuilder()
                .expireAfterAccess(ttl)
                .maximumSize(maximumSize)
                .build();
        offers = Caffeine.newBuilder()
                .expireAfterAccess(ttl)
                .maximumSize(maximumSize * 8)
                .build();
        recentOfferOwners = Caffeine.newBuilder()
                .expireAfterAccess(ttl)
                .maximumSize(maximumSize * 8)
                .build();
    }

    void remember(
            UUID userId,
            List<CanonicalProduct> products,
            Map<String, ProductRankingExplanation> productExplanations,
            Map<String, OfferRankingExplanation> offerExplanations,
            List<UserCatalogSourceState> sourceStates
    ) {
        remember(
                userId,
                products,
                productExplanations,
                offerExplanations,
                Map.of(),
                sourceStates
        );
    }

    void remember(
            UUID userId,
            List<CanonicalProduct> products,
            Map<String, ProductRankingExplanation> productExplanations,
            Map<String, OfferRankingExplanation> offerExplanations,
            Map<String, UserCanonicalProductPersonalizationResult> productPersonalizations,
            List<UserCatalogSourceState> sourceStates
    ) {
        Map<String, UserCanonicalProductPersonalizationResult> safePersonalizations =
                productPersonalizations == null ? Map.of() : productPersonalizations;
        for (CanonicalProduct product : products) {
            Map<String, OfferRankingExplanation> visibleOffers = product.offers().stream()
                    .filter(offer -> offerExplanations.containsKey(offer.key()))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            offer -> offer.key(), offer -> offerExplanations.get(offer.key())));
            entries.put(new Key(userId, product.key()), new Entry(
                    product,
                    productExplanations.get(product.key()),
                    visibleOffers,
                    safePersonalizations.getOrDefault(
                            product.key(),
                            UserCanonicalProductPersonalizationResult.searchRelevance()),
                    sourceStates
            ));
            product.offers().forEach(offer -> offers.put(
                    new OfferKey(userId, offer.key()),
                    new OfferEntry(product.key(), offer)
            ));
            product.offers().forEach(offer -> recentOfferOwners.put(offer.key(), userId));
        }
    }

    Optional<Entry> find(UUID userId, String canonicalProductKey) {
        return Optional.ofNullable(entries.getIfPresent(new Key(userId, canonicalProductKey)));
    }

    public Optional<OfferEntry> findOffer(UUID userId, String offerKey) {
        return Optional.ofNullable(offers.getIfPresent(new OfferKey(userId, offerKey)));
    }

    /** Registers one exact provider-resolved offer without widening the retained canonical product snapshot. */
    public void rememberOffer(UUID userId, String canonicalProductKey, Offer offer) {
        if (userId == null || canonicalProductKey == null || canonicalProductKey.isBlank() || offer == null) {
            throw new IllegalArgumentException("User, canonical product key, and offer are required");
        }
        offers.put(new OfferKey(userId, offer.key()), new OfferEntry(canonicalProductKey.trim(), offer));
        recentOfferOwners.put(offer.key(), userId);
    }

    public boolean isOfferOwnedByAnotherUser(UUID userId, String offerKey) {
        UUID recentOwner = recentOfferOwners.getIfPresent(offerKey);
        return recentOwner != null && !recentOwner.equals(userId);
    }

    public record OfferEntry(String canonicalProductKey, Offer offer) {
    }

    record Entry(
            CanonicalProduct product,
            ProductRankingExplanation productExplanation,
            Map<String, OfferRankingExplanation> offerExplanations,
            UserCanonicalProductPersonalizationResult personalization,
            List<UserCatalogSourceState> sourceStates
    ) {
        Entry {
            offerExplanations = Map.copyOf(offerExplanations);
            personalization = personalization == null
                    ? UserCanonicalProductPersonalizationResult.searchRelevance()
                    : personalization;
            sourceStates = List.copyOf(sourceStates);
        }
    }

    private record Key(UUID userId, String canonicalProductKey) {
    }

    private record OfferKey(UUID userId, String offerKey) {
    }
}
