package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.UpsertUserCommand;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductDiscoveryService {

    private final UserSettingsService userSettingsService;
    private final UserSavedProductService userSavedProductService;
    private final UserProductSearchHashService userProductSearchHashService;
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final UserCollectionProperties userCollectionProperties;
    private final OpenRouterProperties openRouterProperties;

    public UserProductDiscoveryResult get(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid GetUserProductDiscoveryQuery query
    ) {
        validateUser(upsertCommand, query.userId());
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        String profileHash = userProductSearchHashService.profileHash(settings);
        List<UserSavedProductResult> savedProducts = userSavedProductService.list(
                upsertCommand,
                new ListSavedProductsQuery(
                        query.userId(),
                        0,
                        userCollectionProperties.savedProducts().discoveryLimit()));
        List<UserSavedProductResult> filteredSavedProducts = savedProducts.stream()
                .filter(product -> matches(product, query.search()))
                .toList();
        Set<String> savedProductKeys = savedProducts.stream()
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
                .filter(product -> matches(product, query.search()))
                .toList();

        return new UserProductDiscoveryResult(
                sortedSavedProducts(filteredSavedProducts, query.sortBy(), query.sortDirection()),
                sortedRecentProducts(recentProducts, query.sortBy(), query.sortDirection()));
    }

    private void validateUser(UpsertUserCommand upsertCommand, UUID userId) {
        if (!upsertCommand.id().equals(userId)) {
            throw UserException.forbidden("Product discovery user does not match authenticated user");
        }
    }

    private List<UserSavedProductResult> sortedSavedProducts(
            List<UserSavedProductResult> products,
            UserProductDiscoverySortField sortBy,
            UserProductDiscoverySortDirection sortDirection
    ) {
        Comparator<UserSavedProductResult> comparator = switch (sortBy) {
            case MATCH -> Comparator.comparingInt(UserSavedProductResult::match);
            case RECENT -> Comparator.comparing(UserSavedProductResult::createdAt);
            case NAME -> Comparator.comparing(product -> normalized(product.name()));
            case PRICE -> Comparator.comparingDouble(UserSavedProductResult::priceFrom);
            case RATING -> Comparator.comparingDouble(product -> product.review().score());
        };
        return sorted(products, comparator, sortDirection);
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
        Comparator<UserProductSearchProductResult> comparator = switch (sortBy) {
            case MATCH -> Comparator.comparingInt(UserProductSearchProductResult::matchScore);
            case RECENT -> throw new IllegalStateException("Recent products are already sorted by discovery recency");
            case NAME -> Comparator.comparing(product -> normalized(product.title()));
            case PRICE -> Comparator.comparing(
                    UserProductDiscoveryService::priceAmount,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case RATING -> Comparator.comparing(
                    UserProductSearchProductResult::ratingScore,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return sorted(products, comparator, sortDirection);
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

    private <T> List<T> reversed(List<T> products) {
        List<T> reversed = new ArrayList<>(products);
        Collections.reverse(reversed);
        return reversed;
    }

    private static Long priceAmount(UserProductSearchProductResult product) {
        return firstNonNull(product.priceMinAmount(), product.listPriceAmount(), product.priceMaxAmount());
    }

    private static Long firstNonNull(Long... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean matches(UserSavedProductResult product, String search) {
        return matches(search, Stream.of(
                product.name(),
                product.brand(),
                product.category(),
                product.tone(),
                product.note(),
                product.review().insight(),
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

    private boolean matches(UserProductSearchProductResult product, String search) {
        return matches(search, Stream.of(
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

    private boolean matches(String search, Stream<String> fields) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String haystack = fields
                .filter(Objects::nonNull)
                .map(this::normalized)
                .collect(Collectors.joining(" "));
        return Arrays.stream(normalized(search).split("\\s+"))
                .filter(token -> !token.isBlank())
                .allMatch(haystack::contains);
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
        return value.replaceAll("<[^>]+>", " ")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
