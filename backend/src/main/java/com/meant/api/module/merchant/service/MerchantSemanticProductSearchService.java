package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchOutcome;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class MerchantSemanticProductSearchService {

    private final MerchantSemanticSearchService merchantSemanticSearchService;
    private final VoyageRerankClient voyageRerankClient;
    private final MerchantLookupService merchantLookupService;
    private final MerchantCatalogSearchProperties merchantCatalogSearchProperties;
    private final MerchantCatalogSearchExecutor merchantCatalogSearchExecutor;
    private final MerchantProductDetailsEnricher merchantProductDetailsEnricher;
    private final MerchantProductFilterMatcher merchantProductFilterMatcher;

    public MerchantSemanticProductSearchService(
            MerchantSemanticSearchService merchantSemanticSearchService,
            VoyageRerankClient voyageRerankClient,
            MerchantLookupService merchantLookupService,
            MerchantCatalogSearchProperties merchantCatalogSearchProperties,
            MerchantCatalogSearchExecutor merchantCatalogSearchExecutor,
            MerchantProductDetailsEnricher merchantProductDetailsEnricher,
            MerchantProductFilterMatcher merchantProductFilterMatcher
    ) {
        this.merchantSemanticSearchService = merchantSemanticSearchService;
        this.voyageRerankClient = voyageRerankClient;
        this.merchantLookupService = merchantLookupService;
        this.merchantCatalogSearchProperties = merchantCatalogSearchProperties;
        this.merchantCatalogSearchExecutor = merchantCatalogSearchExecutor;
        this.merchantProductDetailsEnricher = merchantProductDetailsEnricher;
        this.merchantProductFilterMatcher = merchantProductFilterMatcher;
    }

    public MerchantSemanticProductSearchResult search(@NotNull @Valid SemanticProductSearchQuery query) {
        return search(query, null);
    }

    public MerchantSemanticProductSearchResult search(
            @NotNull @Valid SemanticProductSearchQuery query,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        int merchantLimit = valueOrDefault(query.merchantLimit(), merchantCatalogSearchProperties.merchantLimit());
        int merchantCandidateLimit = Math.max(
                valueOrDefault(
                        query.merchantCandidateLimit(),
                        merchantCatalogSearchProperties.merchantCandidateLimit()
                ),
                merchantLimit
        );
        int productsPerMerchant = valueOrDefault(
                query.productsPerMerchant(),
                merchantCatalogSearchProperties.productsPerMerchant()
        );
        int productLimit = valueOrDefault(query.productLimit(), merchantCatalogSearchProperties.productLimit());

        List<MerchantSemanticSearchResult> merchants = merchants(query, merchantCandidateLimit);
        List<MerchantSemanticSearchResult> topMerchants = merchants.stream()
                .limit(merchantLimit)
                .toList();

        List<MerchantCatalogSearchOutcome> catalogSearchOutcomes = merchantCatalogSearchExecutor.search(
                query.query(),
                query.context(),
                query.signals(),
                query.filters(),
                productsPerMerchant,
                topMerchants
        );
        List<MerchantCatalogSearchAttemptResult> merchantAttempts = catalogSearchOutcomes.stream()
                .map(MerchantCatalogSearchOutcome::merchantAttempt)
                .toList();
        List<MerchantCatalogProductCandidate> productCandidates = catalogSearchOutcomes.stream()
                .flatMap(outcome -> outcome.productCandidates().stream())
                .toList();

        return new MerchantSemanticProductSearchResult(
                merchantAttempts,
                rerankProducts(
                        query.query(),
                        productCandidates,
                        productLimit,
                        query.context(),
                        query.filters(),
                        candidateConsumer)
        );
    }

    private List<MerchantSemanticSearchResult> merchants(SemanticProductSearchQuery query, int merchantCandidateLimit) {
        if (query.merchantId() != null) {
            return List.of(merchantLookupService.activeSearchResult(query.merchantId()));
        }
        return merchantSemanticSearchService.search(new SemanticMerchantSearchQuery(query.query(), merchantCandidateLimit));
    }

    private List<MerchantSemanticProductResult> rerankProducts(
            String query,
            List<MerchantCatalogProductCandidate> productCandidates,
            int productLimit,
            CatalogSearchContext context,
            CatalogSearchFilters filters,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        List<MerchantCatalogProductCandidate> distinctProductCandidates = distinctProductCandidates(productCandidates);
        List<MerchantCatalogProductCandidate> filteredProductCandidates = merchantProductFilterMatcher.filterCatalogProducts(
                query,
                context,
                filters,
                distinctProductCandidates
        );
        if (filteredProductCandidates.isEmpty()) {
            return List.of();
        }
        merchantProductDetailsEnricher.emitCatalogCandidates(filteredProductCandidates, productLimit, candidateConsumer);

        List<String> documents = filteredProductCandidates.stream()
                .map(MerchantCatalogProductCandidate::rerankDocument)
                .toList();
        List<VoyageRerankResult> rerankedProducts = voyageRerankClient.rerank(query, documents).stream()
                .sorted(Comparator.comparingDouble(VoyageRerankResult::relevanceScore)
                        .reversed()
                        .thenComparingInt(VoyageRerankResult::index))
                .limit(productLimit)
                .toList();

        return merchantProductFilterMatcher.filterProductResults(
                query,
                context,
                filters,
                merchantProductDetailsEnricher.productResults(
                        query,
                        filteredProductCandidates,
                        rerankedProducts,
                        context,
                        candidateConsumer
                )
        );
    }

    private List<MerchantCatalogProductCandidate> distinctProductCandidates(
            List<MerchantCatalogProductCandidate> productCandidates
    ) {
        Map<String, MerchantCatalogProductCandidate> distinctCandidates = new LinkedHashMap<>();
        for (MerchantCatalogProductCandidate productCandidate : productCandidates) {
            distinctCandidates.putIfAbsent(productCandidate.productKey(), productCandidate);
        }
        return List.copyOf(distinctCandidates.values());
    }

    private int valueOrDefault(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
