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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    int copyAccess(UUID sourceUserId, UUID targetUserId, List<String> canonicalProductKeys) {
        if (sourceUserId == null || targetUserId == null || sourceUserId.equals(targetUserId)
                || canonicalProductKeys == null || canonicalProductKeys.isEmpty()) {
            return 0;
        }
        int copied = 0;
        for (String canonicalProductKey : new LinkedHashSet<>(canonicalProductKeys)) {
            Entry source = entries.getIfPresent(new Key(sourceUserId, canonicalProductKey));
            if (source == null) {
                continue;
            }
            Entry target = entries.getIfPresent(new Key(targetUserId, canonicalProductKey));
            Entry merged = merge(source, target);
            entries.put(new Key(targetUserId, canonicalProductKey), merged);
            merged.product().offers().forEach(offer -> {
                offers.put(
                        new OfferKey(targetUserId, offer.key()),
                        new OfferEntry(merged.product().key(), offer)
                );
                recentOfferOwners.put(offer.key(), targetUserId);
            });
            copied++;
        }
        return copied;
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

    private Entry merge(Entry source, Entry target) {
        if (target == null) {
            return source;
        }
        Map<String, Offer> offersByKey = new LinkedHashMap<>();
        source.product().offers().forEach(offer -> offersByKey.put(offer.key(), offer));
        target.product().offers().forEach(offer -> offersByKey.putIfAbsent(offer.key(), offer));

        Map<String, OfferRankingExplanation> offerExplanations = new LinkedHashMap<>();
        target.offerExplanations().forEach(offerExplanations::put);
        source.offerExplanations().forEach(offerExplanations::put);

        List<UserCatalogSourceState> sourceStates = new ArrayList<>(source.sourceStates());
        target.sourceStates().stream()
                .filter(state -> !sourceStates.contains(state))
                .forEach(sourceStates::add);
        return new Entry(
                source.product().withOffers(List.copyOf(offersByKey.values())),
                source.productExplanation() == null
                        ? target.productExplanation() : source.productExplanation(),
                Map.copyOf(offerExplanations),
                target.personalization(),
                List.copyOf(sourceStates)
        );
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
