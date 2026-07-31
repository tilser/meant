package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductsRehydrationResult;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import com.meant.api.module.user.service.query.RehydrateUserCanonicalProductsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserCanonicalProductDetailService {
    private final UserCanonicalProductSessionStore sessionStore;
    private final UserCanonicalProductReferencePersistenceService productReferencePersistenceService;
    private final UserCanonicalProductRehydrationService rehydrationService;
    private final UserSettingsService userSettingsService;
    private final UserProductPreferenceMatchCuratorService preferenceMatchCuratorService;

    public UserProductDetailResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetUserCanonicalProductDetailQuery query
    ) {
        if (!profileCommand.id().equals(query.userId())) {
            throw UserException.forbidden("Product detail user does not match authenticated user");
        }
        UserCanonicalProductSessionStore.Entry entry = entry(query.userId(), query.canonicalProductKey())
                .orElseThrow(() -> UserException.notFound("Canonical product is unknown or expired"));
        CanonicalProduct product = entry.product();
        String recommendedOfferKey = product.offers().getFirst().key();
        String selectedOfferKey = query.selectedOfferKey() == null ? recommendedOfferKey : query.selectedOfferKey();
        if (product.offers().stream().noneMatch(offer -> offer.key().equals(selectedOfferKey))) {
            throw UserException.notFound("Selected offer does not belong to the canonical product");
        }

        UserSettingsResult settings = userSettingsService.get(profileCommand);
        UserCanonicalProductRehydrationResult rehydrated = rehydrationService
                .rehydrate(List.of(product), context(settings))
                .getFirst();
        CanonicalProduct currentProduct = rehydrated.product();
        UserCanonicalProductPersonalizationResult personalization = preferenceMatchCuratorService
                .curateCanonical(List.of(currentProduct), settings)
                .getOrDefault(
                        currentProduct.key(),
                        UserCanonicalProductPersonalizationResult.searchRelevance()
                );
        List<UserCatalogSourceState> sourceStates = sourceStates(entry, rehydrated);
        sessionStore.remember(
                query.userId(),
                List.of(currentProduct),
                entry.productExplanation() == null
                        ? Map.of() : Map.of(currentProduct.key(), entry.productExplanation()),
                entry.offerExplanations(),
                Map.of(currentProduct.key(), personalization),
                sourceStates
        );
        return new UserProductDetailResult(
                currentProduct,
                recommendedOfferKey,
                selectedOfferKey,
                entry.productExplanation(),
                personalization,
                entry.offerExplanations(),
                rehydrated.commercialStates(),
                sourceStates
        );
    }

    public UserCanonicalProductsRehydrationResult rehydrate(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid RehydrateUserCanonicalProductsQuery query
    ) {
        if (!profileCommand.id().equals(query.userId())) {
            throw UserException.forbidden("Product rehydration user does not match authenticated user");
        }
        List<String> requestedKeys = List.copyOf(new LinkedHashSet<>(query.canonicalProductKeys()));
        Map<String, UserCanonicalProductSessionStore.Entry> entries = entries(query.userId(), requestedKeys);
        List<CanonicalProduct> anchoredProducts = requestedKeys.stream()
                .filter(entries::containsKey)
                .map(key -> entries.get(key).product())
                .toList();
        if (anchoredProducts.isEmpty()) {
            return new UserCanonicalProductsRehydrationResult(List.of(), requestedKeys);
        }
        UserSettingsResult settings = userSettingsService.get(profileCommand);
        Map<String, UserCanonicalProductRehydrationResult> rehydrated = rehydrationService
                .rehydrate(anchoredProducts, context(settings))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> result.product().key(),
                        result -> result,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<CanonicalProduct> currentProducts = requestedKeys.stream()
                .map(rehydrated::get)
                .filter(java.util.Objects::nonNull)
                .filter(UserCanonicalProductRehydrationResult::currentFactsAvailable)
                .map(UserCanonicalProductRehydrationResult::product)
                .toList();
        Map<String, UserCanonicalProductPersonalizationResult> personalizations =
                preferenceMatchCuratorService.curateCanonical(currentProducts, settings);

        List<UserProductDetailResult> products = new ArrayList<>();
        List<String> unavailableKeys = new ArrayList<>();
        for (String key : requestedKeys) {
            UserCanonicalProductSessionStore.Entry entry = entries.get(key);
            UserCanonicalProductRehydrationResult current = rehydrated.get(key);
            if (entry == null || current == null || !current.currentFactsAvailable()) {
                unavailableKeys.add(key);
                continue;
            }
            CanonicalProduct product = current.product();
            UserCanonicalProductPersonalizationResult personalization = personalizations.getOrDefault(
                    key, UserCanonicalProductPersonalizationResult.searchRelevance());
            List<UserCatalogSourceState> sourceStates = sourceStates(entry, current);
            sessionStore.remember(
                    query.userId(),
                    List.of(product),
                    entry.productExplanation() == null
                            ? Map.of() : Map.of(key, entry.productExplanation()),
                    entry.offerExplanations(),
                    Map.of(key, personalization),
                    sourceStates
            );
            String recommendedOfferKey = product.offers().getFirst().key();
            products.add(new UserProductDetailResult(
                    product,
                    recommendedOfferKey,
                    recommendedOfferKey,
                    entry.productExplanation(),
                    personalization,
                    entry.offerExplanations(),
                    current.commercialStates(),
                    sourceStates
            ));
        }
        return new UserCanonicalProductsRehydrationResult(products, unavailableKeys);
    }

    private java.util.Optional<UserCanonicalProductSessionStore.Entry> entry(UUID userId, String key) {
        return sessionStore.find(userId, key)
                .or(() -> productReferencePersistenceService.findProduct(userId, key)
                        .map(this::durableEntry));
    }

    private Map<String, UserCanonicalProductSessionStore.Entry> entries(UUID userId, List<String> keys) {
        Map<String, UserCanonicalProductSessionStore.Entry> resolved = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            sessionStore.find(userId, key).ifPresentOrElse(
                    entry -> resolved.put(key, entry),
                    () -> missing.add(key)
            );
        }
        productReferencePersistenceService.findProducts(userId, missing)
                .forEach((key, product) -> resolved.put(key, durableEntry(product)));
        return resolved;
    }

    private UserCanonicalProductSessionStore.Entry durableEntry(CanonicalProduct product) {
        return new UserCanonicalProductSessionStore.Entry(
                product,
                null,
                Map.of(),
                UserCanonicalProductPersonalizationResult.searchRelevance(),
                List.of()
        );
    }

    private CatalogRehydrationContext context(UserSettingsResult settings) {
        return new CatalogRehydrationContext(
                settings.location() == null
                        ? null
                        : CountryCodeNormalizer.normalizeAlpha2(settings.location().code()),
                null,
                settings.currency()
        );
    }

    private List<UserCatalogSourceState> sourceStates(
            UserCanonicalProductSessionStore.Entry entry,
            UserCanonicalProductRehydrationResult rehydrated
    ) {
        List<UserCatalogSourceState> sourceStates = new ArrayList<>(entry.sourceStates());
        rehydrated.sourceStates().stream()
                .filter(state -> !sourceStates.contains(state))
                .forEach(sourceStates::add);
        return List.copyOf(sourceStates);
    }
}
