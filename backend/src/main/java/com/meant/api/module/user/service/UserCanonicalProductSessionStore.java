package com.meant.api.module.user.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
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
        for (CanonicalProduct product : products) {
            Map<String, OfferRankingExplanation> visibleOffers = product.offers().stream()
                    .filter(offer -> offerExplanations.containsKey(offer.key()))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            offer -> offer.key(), offer -> offerExplanations.get(offer.key())));
            entries.put(new Key(userId, product.key()), new Entry(
                    product,
                    productExplanations.get(product.key()),
                    visibleOffers,
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

    public boolean isOfferOwnedByAnotherUser(UUID userId, String offerKey) {
        UUID recentOwner = recentOfferOwners.getIfPresent(offerKey);
        return recentOwner != null && !recentOwner.equals(userId);
    }

    public record OfferEntry(String canonicalProductKey, com.meant.api.module.catalog.service.dto.Offer offer) {
    }

    record Entry(
            CanonicalProduct product,
            ProductRankingExplanation productExplanation,
            Map<String, OfferRankingExplanation> offerExplanations,
            List<UserCatalogSourceState> sourceStates
    ) {
        Entry {
            offerExplanations = Map.copyOf(offerExplanations);
            sourceStates = List.copyOf(sourceStates);
        }
    }

    private record Key(UUID userId, String canonicalProductKey) {
    }

    private record OfferKey(UUID userId, String offerKey) {
    }
}
