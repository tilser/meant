package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductRehydrationResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        UserCanonicalProductSessionStore.Entry entry = sessionStore.find(
                        query.userId(), query.canonicalProductKey())
                .orElseGet(() -> productReferencePersistenceService.findProduct(
                                query.userId(), query.canonicalProductKey())
                        .map(product -> new UserCanonicalProductSessionStore.Entry(
                                product,
                                null,
                                Map.of(),
                                UserCanonicalProductPersonalizationResult.searchRelevance(),
                                List.of()
                        ))
                        .orElseThrow(() -> UserException.notFound(
                                "Canonical product is unknown or expired")));
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
        UserCanonicalProductRehydrationResult rehydrated = rehydrationService
                .rehydrate(List.of(product), context)
                .getFirst();
        CanonicalProduct currentProduct = rehydrated.product();
        UserCanonicalProductPersonalizationResult personalization = preferenceMatchCuratorService
                .curateCanonical(List.of(currentProduct), settings)
                .getOrDefault(
                        currentProduct.key(),
                        UserCanonicalProductPersonalizationResult.searchRelevance()
                );
        List<UserCatalogSourceState> sourceStates = new ArrayList<>(entry.sourceStates());
        rehydrated.sourceStates().stream().filter(state -> !sourceStates.contains(state)).forEach(sourceStates::add);
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
}
