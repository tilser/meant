package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserProductDiscoveryResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserProductDiscoveryQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductDiscoveryService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final UserSettingsService userSettingsService;
    private final UserSavedProductService userSavedProductService;
    private final UserProductSearchHashService userProductSearchHashService;
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final UserInventoryService userInventoryService;
    private final UserTasteProfileService userTasteProfileService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final UserCollectionProperties userCollectionProperties;
    private final OpenRouterProperties openRouterProperties;

    public UserProductDiscoveryResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetUserProductDiscoveryQuery query
    ) {
        validateUser(profileCommand, query.userId());
        UserSettingsResult settings = userSettingsService.get(profileCommand);
        String profileHash = userProductSearchHashService.searchProfileHash(
                settings,
                userInventoryService.inventoryProfileHash(query.userId()),
                userTasteProfileService.profile(query.userId(), settings).profileHash()
        );
        List<UserSavedProductResult> savedProducts = userSavedProductService.list(
                profileCommand,
                new ListSavedProductsQuery(
                        query.userId(),
                        0,
                        userCollectionProperties.savedProducts().discoveryLimit()));
        List<String> searchTokens = searchTokens(query.search());
        List<UserSavedProductResult> filteredSavedProducts = savedProducts.stream()
                .filter(product -> matches(product, searchTokens))
                .toList();
        Set<String> savedProductKeys = filteredSavedProducts.stream()
                .map(UserSavedProductResult::id)
                .collect(Collectors.toSet());
        List<UserProductSearchProductResult> recentProducts = userProductSearchPersistenceService.findRecentProducts(
                        query.userId(),
                        profileHash,
                        userProductSearchProperties.searchVersion(),
                        openRouterProperties.models().productRecommendationExplainer(),
                        userProductSearchProperties.explanationPromptVersion(),
                        Instant.now(),
                        userProductSearchProperties.discoveryRecentSearchLimit(),
                        userProductSearchProperties.discoveryRecentProductLimit()
                )
                .stream()
                .filter(product -> !savedProductKeys.contains(product.productKey()))
                .filter(product -> matches(product, searchTokens))
                .toList();

        return new UserProductDiscoveryResult(
                sortedSavedProducts(filteredSavedProducts, query.sortBy(), query.sortDirection()),
                sortedRecentProducts(recentProducts, query.sortBy(), query.sortDirection()));
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Product discovery user does not match authenticated user");
        }
    }

    private List<UserSavedProductResult> sortedSavedProducts(
            List<UserSavedProductResult> products,
            UserProductDiscoverySortField sortBy,
            UserProductDiscoverySortDirection sortDirection
    ) {
        if (sortBy == UserProductDiscoverySortField.RECENT) {
            return sorted(products, Comparator.comparing(UserSavedProductResult::createdAt), sortDirection);
        }
        return switch (sortBy) {
            case MATCH -> sortedNullsLast(products, UserSavedProductResult::match, sortDirection);
            case RECENT -> throw new IllegalStateException("Handled above");
            case NAME -> sortedNullsLast(
                    products,
                    product -> product.name() == null ? null : normalized(product.name()),
                    sortDirection
            );
            case PRICE -> sortedNullsLast(products, UserSavedProductResult::priceFrom, sortDirection);
            case RATING -> sortedNullsLast(
                    products,
                    product -> product.review() == null ? null : product.review().score(),
                    sortDirection
            );
        };
    }

    private List<UserProductSearchProductResult> sortedRecentProducts(
            List<UserProductSearchProductResult> products,
            UserProductDiscoverySortField sortBy,
            UserProductDiscoverySortDirection sortDirection
    ) {
        if (sortBy == UserProductDiscoverySortField.RECENT) {
            return sortDirection == UserProductDiscoverySortDirection.DESC
                    ? products
                    : reversed(products);
        }
        // PRICE/RATING carry nullable values; their comparators bake the sort direction into the
        // value ordering so unknown values stay last in both ASC and DESC (reversing a nullsLast
        // comparator would float nulls to the top, which we never want).
        return switch (sortBy) {
            case MATCH -> sorted(products,
                    Comparator.comparingInt(UserProductSearchProductResult::matchScore), sortDirection);
            case RECENT -> throw new IllegalStateException("Recent products are already sorted by discovery recency");
            case NAME -> sorted(products,
                    Comparator.comparing(product -> normalized(product.title())), sortDirection);
            case PRICE -> sortedNullsLast(products,
                    UserProductDiscoveryService::priceAmount, sortDirection);
            case RATING -> sortedNullsLast(products,
                    UserProductSearchProductResult::ratingScore, sortDirection);
        };
    }

    private <T> List<T> sorted(
            List<T> products,
            Comparator<T> comparator,
            UserProductDiscoverySortDirection sortDirection
    ) {
        Comparator<T> stableComparator = sortDirection == UserProductDiscoverySortDirection.DESC
                ? comparator.reversed()
                : comparator;
        return products.stream()
                .sorted(stableComparator)
                .toList();
    }

    private <T, U extends Comparable<? super U>> List<T> sortedNullsLast(
            List<T> products,
            Function<? super T, ? extends U> keyExtractor,
            UserProductDiscoverySortDirection sortDirection
    ) {
        Comparator<U> valueOrder = sortDirection == UserProductDiscoverySortDirection.DESC
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
        return products.stream()
                .sorted(Comparator.comparing(keyExtractor, Comparator.nullsLast(valueOrder)))
                .toList();
    }

    private <T> List<T> reversed(List<T> products) {
        List<T> reversed = new ArrayList<>(products);
        Collections.reverse(reversed);
        return reversed;
    }

    private static Long priceAmount(UserProductSearchProductResult product) {
        if (product.priceMinAmount() != null) {
            return product.priceMinAmount();
        }
        if (product.listPriceAmount() != null) {
            return product.listPriceAmount();
        }
        return product.priceMaxAmount();
    }

    private boolean matches(UserSavedProductResult product, List<String> searchTokens) {
        return matches(searchTokens, Stream.of(
                product.name(),
                product.brand(),
                product.category(),
                product.tone(),
                product.note(),
                product.review() == null ? null : product.review().insight(),
                product.needs(),
                String.join(" ", product.satisfies()),
                String.join(" ", product.misses()),
                String.join(" ", product.pros()),
                String.join(" ", product.cons()),
                String.join(" ", product.provides()),
                product.offers().stream()
                        .map(UserSavedProductResult.Offer::merchant)
                        .collect(Collectors.joining(" "))));
    }

    private boolean matches(UserProductSearchProductResult product, List<String> searchTokens) {
        return matches(searchTokens, Stream.of(
                product.title(),
                product.descriptionHtml(),
                product.merchantName(),
                product.merchantDomain(),
                product.detailDescription(),
                product.selectedVariantTitle(),
                product.whyMeantForYou(),
                String.join(" ", product.certifications()),
                String.join(" ", product.materials()),
                String.join(" ", product.skus()),
                String.join(" ", product.collections()),
                product.categories().stream()
                        .map(ProductCatalogCategory::value)
                        .collect(Collectors.joining(" ")),
                product.attributes().stream()
                        .map(UserProductDiscoveryService::attributeText)
                        .collect(Collectors.joining(" "))));
    }

    private boolean matches(List<String> searchTokens, Stream<String> fields) {
        if (searchTokens.isEmpty()) {
            return true;
        }
        String haystack = fields
                .filter(Objects::nonNull)
                .map(this::normalized)
                .collect(Collectors.joining(" "));
        return searchTokens.stream().allMatch(haystack::contains);
    }

    private List<String> searchTokens(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized(search).split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String attributeText(ProductCatalogAttribute attribute) {
        return Stream.of(attribute.name(), attribute.value())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private String normalized(String value) {
        if (value == null) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(value).replaceAll(" ")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
