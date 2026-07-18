package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserDiscoverProductResult;
import com.meant.api.module.user.service.dto.UserDiscoverProductResultSetResult;
import com.meant.api.module.user.service.dto.UserDiscoverProductResultSetSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserDiscoverProductResultSetQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Loads durable result identifiers locally, then rehydrates current facts outside any database transaction. */
@Service
@Validated
@RequiredArgsConstructor
public class UserDiscoverProductResultSetService {

    private final UserDiscoverProductResultSetPersistenceService resultSetPersistenceService;
    private final UserCanonicalProductReferencePersistenceService productReferencePersistenceService;
    private final UserCanonicalProductRehydrationService productRehydrationService;
    private final UserSettingsService userSettingsService;
    private final UserProductPreferenceMatchCuratorService preferenceMatchCuratorService;
    private final UserCanonicalProductSessionStore productSessionStore;

    public UserDiscoverProductResultSetResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetUserDiscoverProductResultSetQuery query
    ) {
        if (!profileCommand.id().equals(query.userId())) {
            throw UserException.forbidden("Discover product result-set user does not match authenticated user");
        }
        UserDiscoverProductResultSetSnapshot snapshot = resultSetPersistenceService.getOwned(query);
        List<CanonicalProduct> anchoredProducts = snapshot.canonicalProductKeys().stream()
                .map(key -> productReferencePersistenceService.findProduct(query.userId(), key).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (anchoredProducts.isEmpty()) {
            return new UserDiscoverProductResultSetResult(
                    snapshot.resultSetId(), List.of(), snapshot.resultCount());
        }

        UserSettingsResult settings = userSettingsService.get(profileCommand);
        CatalogRehydrationContext context = new CatalogRehydrationContext(
                settings.location() == null
                        ? null
                        : CountryCodeNormalizer.normalizeAlpha2(settings.location().code()),
                null
        );
        List<UserCanonicalProductRehydrationResult> rehydrated = productRehydrationService
                .rehydrate(anchoredProducts, context);
        List<UserCanonicalProductRehydrationResult> available = rehydrated.stream()
                .filter(UserCanonicalProductRehydrationResult::currentFactsAvailable)
                .toList();
        List<CanonicalProduct> currentProducts = available.stream()
                .map(UserCanonicalProductRehydrationResult::product)
                .toList();
        Map<String, UserCanonicalProductPersonalizationResult> personalizations =
                preferenceMatchCuratorService.curateCanonical(currentProducts, settings);
        List<UserDiscoverProductResult> products = available.stream()
                .map(result -> new UserDiscoverProductResult(
                        result.product(),
                        personalizations.get(result.product().key()),
                        result.commercialStates()
                ))
                .toList();

        remember(query, products, available, personalizations);
        int unavailableCount = Math.max(0, snapshot.resultCount() - products.size());
        return new UserDiscoverProductResultSetResult(
                snapshot.resultSetId(), products, unavailableCount);
    }

    private void remember(
            GetUserDiscoverProductResultSetQuery query,
            List<UserDiscoverProductResult> products,
            List<UserCanonicalProductRehydrationResult> rehydrated,
            Map<String, UserCanonicalProductPersonalizationResult> personalizations
    ) {
        if (products.isEmpty()) {
            return;
        }
        var sourceStates = rehydrated.stream()
                .flatMap(result -> result.sourceStates().stream())
                .distinct()
                .toList();
        productSessionStore.remember(
                query.userId(),
                products.stream().map(UserDiscoverProductResult::product).toList(),
                Map.of(),
                Map.of(),
                new LinkedHashMap<>(personalizations),
                sourceStates
        );
    }
}
