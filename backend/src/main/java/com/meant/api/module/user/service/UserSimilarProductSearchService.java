package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.port.CatalogSimilarityReferenceResolver;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
        CanonicalProduct anchor = productSessionStore.find(command.userId(), command.canonicalProductKey())
                .map(UserCanonicalProductSessionStore.Entry::product)
                .or(() -> productReferencePersistenceService.findProduct(
                        command.userId(), command.canonicalProductKey()))
                .orElseThrow(() -> UserException.notFound("Canonical product reference was not found"));
        CatalogSimilarityReference reference = similarityReferenceResolver.resolve(anchor)
                .orElseThrow(() -> UserException.notFound(
                        "Canonical product has no supported product-level similarity reference"));
        UserQualifiedProductSearchInput qualified = command.qualificationId() == null
                ? null
                : qualifiedSearchResolver.resolve(command.userId(), command.qualificationId());
        if (qualified != null && qualified.merchantId() != null) {
            throw new UserException("Similarity search qualification must target the global catalog");
        }
        String query = qualified == null ? command.query() : qualified.effectiveQuery();
        CatalogDiscoveryFilters discoveryFilters = qualified == null ? null : qualified.filters();
        SearchUserProductsCommand searchCommand = new SearchUserProductsCommand(
                command.userId(),
                query,
                null,
                command.buyerIp(),
                command.userAgent(),
                UserProductSearchPagination.DEFAULT_OFFSET,
                UserProductSearchPagination.DEFAULT_LIMIT
        );
        return groupedProductSearchService.searchSimilar(
                profileCommand,
                searchCommand,
                discoveryFilters,
                anchor,
                reference
        );
    }

}
