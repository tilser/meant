package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchOutcome;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.MerchantCatalogPluginDispatchService;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<MerchantCatalogSearchOutcome>> futures = executor.invokeAll(merchants.stream()
                    .<java.util.concurrent.Callable<MerchantCatalogSearchOutcome>>map(merchant -> () -> searchMerchantCatalog(
                            query,
                            context,
                            signals,
                            filters,
                            productsPerMerchant,
                            merchant
                    )).toList());
            return futures.stream()
                    .map(this::completedOutcome)
                    .toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Merchant catalog discovery was cancelled");
        } finally {
            executor.shutdownNow();
        }
    }

    private MerchantCatalogSearchOutcome completedOutcome(Future<MerchantCatalogSearchOutcome> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Merchant catalog discovery was cancelled");
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("Merchant catalog task failed without a scoped outcome", exception);
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
                    productCandidates(merchant, catalogSearchResult),
                    catalogSearchResult.pagination() != null
                            && Boolean.TRUE.equals(catalogSearchResult.pagination().hasNextPage())
            );
        } catch (RuntimeException exception) {
            return merchantCatalogSearchFailure(merchant, exception);
        }
    }

    private MerchantCatalogSearchOutcome merchantCatalogSearchFailure(
            MerchantSemanticSearchResult merchant,
            Throwable exception
    ) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        log.warn(
                "Merchant catalog search failed. merchantId={}, domain={}, rank={}, failureType={}",
                merchant.merchantId(),
                merchant.domain(),
                merchant.rank(),
                cause.getClass().getName()
        );
        return new MerchantCatalogSearchOutcome(
                MerchantCatalogSearchAttemptResult.failure(merchant, "Merchant catalog search failed"),
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

}
