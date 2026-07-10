package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.exception.UserProductSearchGroupingException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.service.ExactProductGroupingService;
import com.meant.api.plugin.catalog.common.service.FederatedCatalogDiscoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserGroupedProductSearchService {

    private final UserProductSearchPreparationService preparationService;
    private final FederatedCatalogDiscoveryService federatedDiscoveryService;
    private final ExactProductGroupingService exactProductGroupingService;

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command
    ) {
        var preparation = preparationService.prepare(profileCommand, command);
        FederatedCatalogDiscoveryResult discovery = federatedDiscoveryService.search(new CatalogDiscoveryRequest(
                preparation.catalogInput().searchQuery(),
                command.merchantId(),
                preparation.fetchLimit(),
                preparation.catalogInput().context(),
                preparation.catalogInput().signals(),
                preparation.catalogInput().filters()
        ));
        if (discovery.status() == CatalogDiscoveryTerminalStatus.FAILED) {
            throw new UserProductSearchGroupingException("Every catalog discovery source failed");
        }

        List<ProductCandidate> page = discovery.candidates().stream()
                .skip(preparation.offset())
                .limit(preparation.limit())
                .toList();
        boolean hasMore = preparation.fetchLimit() < UserProductSearchPagination.MAX_RESULT_WINDOW
                && (discovery.truncated() || discovery.candidates().size() >= preparation.fetchLimit());
        return new UserGroupedProductSearchResult(
                preparation.query(),
                preparation.normalizedQuery(),
                preparation.profileHash(),
                false,
                preparation.offset(),
                preparation.limit(),
                hasMore ? Math.min(
                        preparation.offset() + preparation.limit(),
                        UserProductSearchPagination.MAX_RESULT_WINDOW
                ) : null,
                hasMore,
                exactProductGroupingService.group(page)
        );
    }
}
