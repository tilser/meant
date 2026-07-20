package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.ExactProductGroupingService;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryService;
import com.meant.api.module.catalog.service.ProductRankingService;
import com.meant.api.module.catalog.service.dto.*;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.exception.UserProductSearchGroupingException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.ReplaceUserDiscoverProductResultSetCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserCatalogSourceState;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserProductSearchHistoryContext;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserGroupedProductSearchService {

    static final int MAX_PUBLIC_GROUPING_DECISIONS = 100;

    private final UserProductSearchPreparationService preparationService;
    private final FederatedCatalogDiscoveryService federatedDiscoveryService;
    private final ExactProductGroupingService exactProductGroupingService;
    private final ProductRankingService productRankingService;
    private final UserProductRankingContextFactory rankingContextFactory;
    private final UserCanonicalProductSessionStore productSessionStore;
    private final UserCanonicalProductReferencePersistenceService productReferencePersistenceService;
    private final UserDiscoverProductResultSetPersistenceService productResultSetPersistenceService;
    private final UserProductPreferenceMatchCuratorService preferenceMatchCuratorService;

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command
    ) {
        return search(command, preparationService.prepare(profileCommand, command), null, null, null);
    }

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters
    ) {
        return search(
                command,
                preparationService.prepare(profileCommand, command, discoveryFilters),
                null,
                null,
                null
        );
    }

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            @NotNull @Valid UserProductSearchHistoryContext historyContext
    ) {
        return search(
                command,
                preparationService.prepare(profileCommand, command, discoveryFilters),
                historyContext,
                null,
                null
        );
    }

    UserGroupedProductSearchResult searchSimilar(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CanonicalProduct anchor,
            CatalogSimilarityReference similarityReference
    ) {
        return searchSimilar(profileCommand, command, null, anchor, similarityReference);
    }

    UserGroupedProductSearchResult searchSimilar(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            CanonicalProduct anchor,
            CatalogSimilarityReference similarityReference
    ) {
        return search(
                command,
                preparationService.prepareSimilarity(profileCommand, command, discoveryFilters),
                null,
                anchor,
                similarityReference
        );
    }

    private UserGroupedProductSearchResult search(
            SearchUserProductsCommand command,
            UserProductSearchPreparation preparation,
            UserProductSearchHistoryContext historyContext,
            CanonicalProduct anchor,
            CatalogSimilarityReference similarityReference
    ) {
        FederatedCatalogDiscoveryResult discovery = federatedDiscoveryService.search(new CatalogDiscoveryRequest(
                preparation.catalogInput().searchQuery(),
                command.merchantId(),
                UserProductSearchPagination.MAX_RESULT_WINDOW,
                preparation.catalogInput().context(),
                preparation.catalogInput().signals(),
                preparation.catalogInput().filters(),
                preparation.catalogInput().discoveryFilters(),
                similarityReference,
                Set.of()
        ));
        if (discovery.status() == CatalogDiscoveryTerminalStatus.FAILED) {
            if (discovery.sources().isEmpty()) {
                log.error("Catalog discovery failed with no eligible sources. terminalStatus={}", discovery.status());
                throw new UserProductSearchGroupingException(
                        "No eligible catalog discovery source was available");
            }
            String sourceDiagnostics = discovery.sources().stream()
                    .map(source -> {
                        CatalogSourceFailure failure = source.failure();
                        return "provider=%s,type=%s,operation=%s,failureKind=%s,upstreamStatus=%s".formatted(
                                source.provider().value(),
                                source.discoverySource().type(),
                                source.operation(),
                                failure == null ? "NONE" : failure.kind(),
                                failure == null || failure.upstreamStatus() == null
                                        ? "NONE"
                                        : failure.upstreamStatus()
                        );
                    })
                    .collect(Collectors.joining("; "));
            log.error("Every catalog discovery source failed. terminalStatus={}, sources=[{}]",
                    discovery.status(), sourceDiagnostics);
            throw new UserProductSearchGroupingException("Every catalog discovery source failed");
        }

        ProductGroupingResult grouping = exactProductGroupingService.evaluate(discovery.candidates());
        List<CanonicalProduct> groupedProducts = grouping.products().stream()
                .filter(product -> !sameProduct(product, anchor, similarityReference))
                .toList();
        ProductRankingResult ranking = productRankingService.rank(
                groupedProducts,
                rankingContextFactory.create(command.userId(), preparation, groupedProducts)
        );
        boolean singlePageSimilarity = similarityReference != null;
        int responseOffset = singlePageSimilarity
                ? UserProductSearchPagination.DEFAULT_OFFSET
                : preparation.offset();
        int responseLimit = singlePageSimilarity
                ? UserProductSearchPagination.DEFAULT_LIMIT
                : preparation.limit();
        List<CanonicalProduct> page = ranking.products().stream()
                .skip(responseOffset)
                .limit(responseLimit)
                .toList();
        int pageEnd = Math.min(
                responseOffset + responseLimit,
                UserProductSearchPagination.MAX_RESULT_WINDOW
        );
        boolean hasMore = !singlePageSimilarity && ranking.products().size() > pageEnd;
        Set<String> visibleOfferKeys = page.stream()
                .flatMap(product -> product.offers().stream())
                .map(Offer::key)
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
        Map<String, UserCanonicalProductPersonalizationResult> pagePersonalizations =
                preferenceMatchCuratorService.curateCanonical(page, preparation.settings());
        UUID productResultSetId;
        if (historyContext == null) {
            productReferencePersistenceService.replace(command.userId(), page);
            productResultSetId = null;
        } else {
            productResultSetId = productResultSetPersistenceService.replace(
                    new ReplaceUserDiscoverProductResultSetCommand(
                            command.userId(),
                            historyContext.conversationId(),
                            historyContext.qualificationId(),
                            responseOffset,
                            responseLimit,
                            hasMore ? pageEnd : null,
                            hasMore,
                            discovery.truncated(),
                            page
                    ));
        }
        productSessionStore.remember(
                command.userId(), page, pageProductExplanations, pageOfferExplanations,
                pagePersonalizations, sourceStates);
        return new UserGroupedProductSearchResult(
                preparation.query(),
                preparation.normalizedQuery(),
                preparation.profileHash(),
                false,
                responseOffset,
                responseLimit,
                hasMore ? pageEnd : null,
                hasMore,
                discovery.truncated(),
                page,
                pageProductExplanations,
                pageOfferExplanations,
                pagePersonalizations,
                sourceStates,
                grouping.decisions().size(),
                grouping.decisions().size() > visibleDecisions.size(),
                visibleDecisions,
                productResultSetId
        );
    }

    private boolean sameProduct(
            CanonicalProduct candidate,
            CanonicalProduct anchor,
            CatalogSimilarityReference similarityReference
    ) {
        if (anchor == null || similarityReference == null) {
            return false;
        }
        if (anchor.key().equals(candidate.key())
                || hasReference(
                        candidate,
                        similarityReference.provider(),
                        similarityReference.productReference().value())) {
            return true;
        }
        boolean matchingProvenance = anchor.provenance().stream().anyMatch(provenance ->
                hasReference(candidate, provenance.provider(), value(provenance.externalProductReference()))
                        || hasReference(candidate, provenance.provider(), value(provenance.externalVariantReference())));
        if (matchingProvenance) {
            return true;
        }
        return anchor.identityEvidence().stream()
                .flatMap(evidence -> evidence.identifiers().stream())
                .filter(identifier -> identifier.namespace() != null)
                .anyMatch(identifier -> hasReference(
                        candidate,
                        new ProviderIdentity(identifier.namespace()),
                        identifier.value()));
    }

    private boolean hasReference(CanonicalProduct product, ProviderIdentity provider, String reference) {
        if (provider == null || reference == null || reference.isBlank()) {
            return false;
        }
        boolean evidenceMatch = product.identityEvidence().stream()
                .flatMap(evidence -> evidence.identifiers().stream())
                .anyMatch(identifier -> provider.value().equals(identifier.namespace())
                        && reference.equals(identifier.value()));
        if (evidenceMatch) {
            return true;
        }
        return product.provenance().stream()
                .filter(provenance -> provider.equals(provenance.provider()))
                .anyMatch(provenance -> reference.equals(value(provenance.externalProductReference()))
                        || reference.equals(value(provenance.externalVariantReference())));
    }

    private String value(ExternalIdentifier identifier) {
        return identifier == null ? null : identifier.value();
    }
}
