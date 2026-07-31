package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.port.CatalogSimilarityReferenceResolver;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserSimilarProductSearchService {

    private final UserCanonicalProductSessionStore productSessionStore;
    private final UserCanonicalProductReferencePersistenceService productReferencePersistenceService;
    private final UserGroupedProductSearchService groupedProductSearchService;
    private final CatalogSimilarityReferenceResolver similarityReferenceResolver;
    private final UserQualifiedProductSearchResolver qualifiedSearchResolver;

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchSimilarUserProductsCommand command
    ) {
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Similarity search user does not match authenticated user");
        }
        if (command.qualificationId() == null) {
            throw new UserException("A READY product-search qualification is required for similarity search");
        }
        CanonicalProduct anchor = productSessionStore.find(command.userId(), command.canonicalProductKey())
                .map(UserCanonicalProductSessionStore.Entry::product)
                .or(() -> productReferencePersistenceService.findProduct(
                        command.userId(), command.canonicalProductKey()))
                .orElseThrow(() -> UserException.notFound("Canonical product reference was not found"));
        UserQualifiedProductSearchInput qualified =
                qualifiedSearchResolver.resolve(command.userId(), command.qualificationId());
        if (command.merchantId() != null
                && !Objects.equals(qualified.merchantId(), command.merchantId())) {
            throw UserException.notFound("Product-search qualification not found");
        }
        var merchantId = qualified.merchantId();
        CatalogSimilarityReference reference = similarityReferenceResolver.resolve(anchor)
                .orElseThrow(() -> UserException.notFound(
                        "Canonical product has no supported product-level similarity reference"));
        String query = qualified.effectiveQuery();
        CatalogDiscoveryFilters discoveryFilters = qualified.filters();
        if (discoveryFilters == null || !Boolean.TRUE.equals(discoveryFilters.available())) {
            throw new UserException(
                    "A complete sale-ready product-search qualification is required for similarity search");
        }
        var explicitAnyTargets = qualified.explicitAnyTargets();
        var profileSuppressionTargets = qualified.profileSuppressionTargets();
        SearchUserProductsCommand searchCommand = new SearchUserProductsCommand(
                command.userId(),
                query,
                merchantId,
                command.buyerIp(),
                command.userAgent(),
                command.language(),
                UserProductSearchPagination.DEFAULT_OFFSET,
                UserProductSearchPagination.DEFAULT_LIMIT
        );
        return groupedProductSearchService.searchSimilar(
                profileCommand,
                searchCommand,
                discoveryFilters,
                explicitAnyTargets,
                profileSuppressionTargets,
                anchor,
                reference
        );
    }

    /** Executes similarity search with conversation-scoped advisory filters, including partial plans. */
    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchSimilarUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Similarity search user does not match authenticated user");
        }
        CanonicalProduct anchor = productSessionStore.find(command.userId(), command.canonicalProductKey())
                .map(UserCanonicalProductSessionStore.Entry::product)
                .or(() -> productReferencePersistenceService.findProduct(
                        command.userId(), command.canonicalProductKey()))
                .orElseThrow(() -> UserException.notFound("Canonical product reference was not found"));
        CatalogSimilarityReference reference = similarityReferenceResolver.resolve(anchor)
                .orElseThrow(() -> UserException.notFound(
                        "Canonical product has no supported product-level similarity reference"));
        SearchUserProductsCommand searchCommand = new SearchUserProductsCommand(
                command.userId(),
                command.query(),
                command.merchantId(),
                command.buyerIp(),
                command.userAgent(),
                command.language(),
                UserProductSearchPagination.DEFAULT_OFFSET,
                UserProductSearchPagination.DEFAULT_LIMIT
        );
        return groupedProductSearchService.searchSimilar(
                profileCommand,
                searchCommand,
                discoveryFilters,
                explicitAnyTargets,
                profileSuppressionTargets,
                anchor,
                reference
        );
    }

}
