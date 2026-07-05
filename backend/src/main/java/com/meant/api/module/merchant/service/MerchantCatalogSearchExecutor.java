package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchOutcome;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.plugin.catalog.common.service.MerchantCatalogPluginDispatchService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantCatalogSearchExecutor {

    private final MerchantCatalogPluginDispatchService merchantCatalogPluginDispatchService;

    List<MerchantCatalogSearchOutcome> search(
            String query,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            int productsPerMerchant,
            List<MerchantSemanticSearchResult> merchants
    ) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<MerchantCatalogSearchOutcome>> futures = merchants.stream()
                    .map(merchant -> CompletableFuture.supplyAsync(() -> searchMerchantCatalog(
                            query,
                            context,
                            signals,
                            filters,
                            productsPerMerchant,
                            merchant
                    ), executor).exceptionally(exception -> merchantCatalogSearchFailure(
                            merchant,
                            query,
                            productsPerMerchant,
                            exception
                    )))
                    .toList();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
    }

    private MerchantCatalogSearchOutcome searchMerchantCatalog(
            String query,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            int productsPerMerchant,
            MerchantSemanticSearchResult merchant
    ) {
        try {
            CatalogSearchResult catalogSearchResult = merchantCatalogPluginDispatchService.searchCatalog(
                    merchant,
                    query,
                    context,
                    signals,
                    filters,
                    productsPerMerchant
            );
            return new MerchantCatalogSearchOutcome(
                    MerchantCatalogSearchAttemptResult.success(
                            merchant,
                            catalogSearchResult.endpoint(),
                            safeNonNullList(catalogSearchResult.products()).size()
                    ),
                    productCandidates(merchant, catalogSearchResult)
            );
        } catch (RuntimeException exception) {
            return merchantCatalogSearchFailure(merchant, query, productsPerMerchant, exception);
        }
    }

    private MerchantCatalogSearchOutcome merchantCatalogSearchFailure(
            MerchantSemanticSearchResult merchant,
            String query,
            int productsPerMerchant,
            Throwable exception
    ) {
        log.error(
                "Merchant catalog search failed. merchantId={}, domain={}, rank={}, query={}, productsPerMerchant={}",
                merchant.merchantId(),
                merchant.domain(),
                merchant.rank(),
                query,
                productsPerMerchant,
                exception
        );
        return new MerchantCatalogSearchOutcome(
                MerchantCatalogSearchAttemptResult.failure(merchant, failureMessage(exception)),
                List.of()
        );
    }

    private List<MerchantCatalogProductCandidate> productCandidates(
            MerchantSemanticSearchResult merchant,
            CatalogSearchResult catalogSearchResult
    ) {
        List<CatalogSearchResponse.Product> products = safeNonNullList(catalogSearchResult.products());
        return IntStream.range(0, products.size())
                .mapToObj(index -> new MerchantCatalogProductCandidate(
                        merchant,
                        catalogSearchResult.endpoint(),
                        products.get(index),
                        index + 1,
                        catalogSearchResult.negotiatedCapabilities()
                ))
                .toList();
    }

    private String failureMessage(Throwable exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message.trim();
    }
}
