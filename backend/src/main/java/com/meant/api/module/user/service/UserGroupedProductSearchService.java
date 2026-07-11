package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.exception.UserProductSearchGroupingException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecision;
import com.meant.api.module.catalog.service.dto.ProductGroupingResult;
import com.meant.api.module.catalog.service.dto.ProductRankingResult;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.ExactProductGroupingService;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryService;
import com.meant.api.module.catalog.service.ProductRankingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserGroupedProductSearchService {

    static final int MAX_PUBLIC_GROUPING_DECISIONS = 100;

    private final UserProductSearchPreparationService preparationService;
    private final FederatedCatalogDiscoveryService federatedDiscoveryService;
    private final ExactProductGroupingService exactProductGroupingService;
    private final ProductRankingService productRankingService;
    private final UserProductRankingContextFactory rankingContextFactory;
    private final UserCanonicalProductSessionStore productSessionStore;

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command
    ) {
        var preparation = preparationService.prepare(profileCommand, command);
        FederatedCatalogDiscoveryResult discovery = federatedDiscoveryService.search(new CatalogDiscoveryRequest(
                preparation.catalogInput().searchQuery(),
                command.merchantId(),
                UserProductSearchPagination.MAX_RESULT_WINDOW,
                preparation.catalogInput().context(),
                preparation.catalogInput().signals(),
                preparation.catalogInput().filters()
        ));
        if (discovery.status() == CatalogDiscoveryTerminalStatus.FAILED) {
            throw new UserProductSearchGroupingException("Every catalog discovery source failed");
        }

        ProductGroupingResult grouping = exactProductGroupingService.evaluate(discovery.candidates());
        ProductRankingResult ranking = productRankingService.rank(
                grouping.products(),
                rankingContextFactory.create(command.userId(), preparation, grouping.products())
        );
        List<CanonicalProduct> page = ranking.products().stream()
                .skip(preparation.offset())
                .limit(preparation.limit())
                .toList();
        int pageEnd = Math.min(
                preparation.offset() + preparation.limit(),
                UserProductSearchPagination.MAX_RESULT_WINDOW
        );
        boolean hasMore = ranking.products().size() > pageEnd;
        Set<String> visibleOfferKeys = page.stream()
                .flatMap(product -> product.offers().stream())
                .map(offer -> offer.key())
                .collect(Collectors.toUnmodifiableSet());
        List<ProductGroupingDecision> visibleDecisions = grouping.decisions().stream()
                .filter(decision -> visibleOfferKeys.contains(decision.leftOfferKey())
                        && visibleOfferKeys.contains(decision.rightOfferKey()))
                .limit(MAX_PUBLIC_GROUPING_DECISIONS)
                .toList();
        List<UserCatalogSourceState> sourceStates = discovery.sources().stream()
                .map(source -> new UserCatalogSourceState(
                        source.discoverySource(),
                        source.operation(),
                        source.failure() != null,
                        source.truncated() || source.page() != null && source.page().hasNextPage(),
                        source.failure() == null ? null : source.failure().kind(),
                        null,
                        source.failure() == null ? null : source.failure().retryAfter()
                ))
                .toList();
        Map<String, ProductRankingExplanation> pageProductExplanations =
                ranking.productExplanations().entrySet().stream()
                        .filter(entry -> page.stream().anyMatch(product -> product.key().equals(entry.getKey())))
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, OfferRankingExplanation> pageOfferExplanations =
                ranking.offerExplanations().entrySet().stream()
                        .filter(entry -> visibleOfferKeys.contains(entry.getKey()))
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        productSessionStore.remember(
                command.userId(), page, pageProductExplanations, pageOfferExplanations, sourceStates);
        return new UserGroupedProductSearchResult(
                preparation.query(),
                preparation.normalizedQuery(),
                preparation.profileHash(),
                false,
                preparation.offset(),
                preparation.limit(),
                hasMore ? pageEnd : null,
                hasMore,
                discovery.truncated(),
                page,
                pageProductExplanations,
                pageOfferExplanations,
                sourceStates,
                grouping.decisions().size(),
                grouping.decisions().size() > visibleDecisions.size(),
                visibleDecisions
        );
    }
}
