package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class MerchantSemanticProductSearchService {

    private final MerchantSemanticSearchService merchantSemanticSearchService;
    private final MerchantCatalogSearchClient merchantCatalogSearchClient;
    private final MerchantProductDetailsClient merchantProductDetailsClient;
    private final VoyageRerankClient voyageRerankClient;
    private final MerchantLookupService merchantLookupService;
    private final MerchantCatalogSearchProperties merchantCatalogSearchProperties;

    public MerchantSemanticProductSearchResult search(@NotNull @Valid SemanticProductSearchQuery query) {
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

        List<MerchantCatalogSearchOutcome> catalogSearchOutcomes = searchMerchantCatalogs(
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
                rerankProducts(query.query(), productCandidates, productLimit, query.context(), query.filters())
        );
    }

    private List<MerchantSemanticSearchResult> merchants(SemanticProductSearchQuery query, int merchantCandidateLimit) {
        if (query.merchantId() != null) {
            return List.of(merchantLookupService.activeSearchResult(query.merchantId()));
        }
        return merchantSemanticSearchService.search(new SemanticMerchantSearchQuery(query.query(), merchantCandidateLimit));
    }

    private List<MerchantCatalogSearchOutcome> searchMerchantCatalogs(
            String query,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            int productsPerMerchant,
            List<MerchantSemanticSearchResult> merchants
    ) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MerchantCatalogSearchOutcome>> futures = merchants.stream()
                    .map(merchant -> executor.submit(() -> searchMerchantCatalog(
                            query,
                            context,
                            signals,
                            filters,
                            productsPerMerchant,
                            merchant
                    )))
                    .toList();
            return futures.stream()
                    .map(this::catalogSearchOutcome)
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
            CatalogSearchResult catalogSearchResult = merchantCatalogSearchClient.searchCatalog(
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
                            catalogSearchResult.products().size()
                    ),
                    productCandidates(merchant, catalogSearchResult)
            );
        } catch (MerchantCatalogSearchException exception) {
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
                    MerchantCatalogSearchAttemptResult.failure(merchant, exception.getMessage()),
                    List.of()
            );
        }
    }

    private MerchantCatalogSearchOutcome catalogSearchOutcome(Future<MerchantCatalogSearchOutcome> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MerchantCatalogSearchException("Parallel merchant catalog search was interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof MerchantCatalogSearchException merchantCatalogSearchException) {
                throw merchantCatalogSearchException;
            }
            throw new MerchantCatalogSearchException("Parallel merchant catalog search failed", exception.getCause());
        }
    }

    private List<MerchantCatalogProductCandidate> productCandidates(
            MerchantSemanticSearchResult merchant,
            CatalogSearchResult catalogSearchResult
    ) {
        return IntStream.range(0, catalogSearchResult.products().size())
                .mapToObj(index -> new MerchantCatalogProductCandidate(
                        merchant,
                        catalogSearchResult.endpoint(),
                        catalogSearchResult.products().get(index),
                        index + 1
                ))
                .toList();
    }

    private List<MerchantSemanticProductResult> rerankProducts(
            String query,
            List<MerchantCatalogProductCandidate> productCandidates,
            int productLimit,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        List<MerchantCatalogProductCandidate> distinctProductCandidates = distinctProductCandidates(productCandidates);
        List<MerchantCatalogProductCandidate> filteredProductCandidates = distinctProductCandidates.stream()
                .filter(productCandidate -> matchesCatalogFilters(productCandidate, context, filters))
                .toList();
        if (filteredProductCandidates.isEmpty()) {
            return List.of();
        }

        List<String> documents = filteredProductCandidates.stream()
                .map(MerchantCatalogProductCandidate::rerankDocument)
                .toList();
        List<VoyageRerankResult> rerankedProducts = voyageRerankClient.rerank(query, documents).stream()
                .sorted(Comparator.comparingDouble(VoyageRerankResult::relevanceScore)
                        .reversed()
                        .thenComparingInt(VoyageRerankResult::index))
                .limit(productLimit)
                .toList();

        return productResults(query, filteredProductCandidates, rerankedProducts, context).stream()
                .filter(product -> matchesProductFilters(product, context, filters))
                .toList();
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

    private MerchantSemanticProductResult toProductResult(
            MerchantCatalogProductCandidate productCandidate,
            VoyageRerankResult rerankResult,
            int rank,
            String query,
            CatalogSearchContext context
    ) {
        try {
            return toProductResult(productCandidate, rerankResult, rank, null, productDetails(productCandidate, context));
        } catch (RuntimeException exception) {
            MerchantSemanticSearchResult merchant = productCandidate.merchant();
            log.error(
                    "Merchant product details failed. merchantId={}, domain={}, endpoint={}, productId={}, query={}, rank={}",
                    merchant.merchantId(),
                    merchant.domain(),
                    productCandidate.endpoint(),
                    productCandidate.product().id(),
                    query,
                    rank,
                    exception
            );
            return toProductResult(
                    productCandidate,
                    rerankResult,
                    rank,
                    exception.getMessage(),
                    (ProductDetailsResult) null
            );
        }
    }

    private ProductDetailsResult productDetails(
            MerchantCatalogProductCandidate productCandidate,
            CatalogSearchContext context
    ) {
        return merchantProductDetailsClient.getProductDetails(
                productCandidate.merchant(),
                productCandidate.product().id(),
                context
        );
    }

    private List<MerchantSemanticProductResult> productResults(
            String query,
            List<MerchantCatalogProductCandidate> filteredProductCandidates,
            List<VoyageRerankResult> rerankedProducts,
            CatalogSearchContext context
    ) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MerchantSemanticProductResult>> futures = IntStream.range(0, rerankedProducts.size())
                    .mapToObj(index -> executor.submit(() -> toProductResult(
                            filteredProductCandidates.get(rerankedProducts.get(index).index()),
                            rerankedProducts.get(index),
                            index + 1,
                            query,
                            context
                    )))
                    .toList();
            return futures.stream()
                    .map(this::productResult)
                    .toList();
        }
    }

    private MerchantSemanticProductResult productResult(Future<MerchantSemanticProductResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MerchantProductDetailsException("Parallel merchant product details search was interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof MerchantProductDetailsException merchantProductDetailsException) {
                throw merchantProductDetailsException;
            }
            throw new MerchantProductDetailsException("Parallel merchant product details search failed", exception.getCause());
        }
    }

    private MerchantSemanticProductResult toProductResult(
            MerchantCatalogProductCandidate productCandidate,
            VoyageRerankResult rerankResult,
            int rank,
            String detailError,
            ProductDetailsResult details
    ) {
        MerchantSemanticSearchResult merchant = productCandidate.merchant();
        ProductDetailsResponse.Product detailProduct = details == null ? null : details.product();
        ProductDetailsResponse.PriceRange detailPriceRange = detailProduct == null ? null : detailProduct.priceRange();
        ProductDetailsResponse.SelectedVariant selectedVariant = detailProduct == null
                ? null
                : detailProduct.selectedOrFirstAvailableVariant();
        return new MerchantSemanticProductResult(
                merchant.merchantId(),
                merchant.domain(),
                merchant.name(),
                details == null ? productCandidate.endpoint() : details.endpoint(),
                merchant.rank(),
                merchant.semanticScore(),
                merchant.rerankScore(),
                productCandidate.product().id(),
                valueOrDefault(detailProduct == null ? null : detailProduct.title(), productCandidate.product().title()),
                productCandidate.descriptionHtml(),
                valueOrDefault(detailProduct == null ? null : detailProduct.url(), productCandidate.product().url()),
                productCandidate.imageUrl(),
                productCandidate.priceMinAmount(),
                productCandidate.priceMaxAmount(),
                productCandidate.priceCurrency(),
                productCandidate.available(),
                detailError,
                detailProduct == null ? null : detailProduct.description(),
                detailProduct == null ? null : detailProduct.imageUrl(),
                detailProduct == null ? List.of() : safeList(detailProduct.images()),
                detailProduct == null ? List.of() : safeList(detailProduct.options()),
                detailPriceRange == null ? null : detailPriceRange.min(),
                detailPriceRange == null ? null : detailPriceRange.max(),
                detailPriceRange == null ? null : detailPriceRange.currency(),
                detailProduct == null ? null : detailProduct.totalVariants(),
                detailProduct == null ? null : detailProduct.requiresSellingPlan(),
                detailProduct == null ? List.of() : safeList(detailProduct.sellingPlanGroups()),
                selectedVariant == null ? null : selectedVariant.variantId(),
                selectedVariant == null ? null : selectedVariant.title(),
                selectedVariant == null ? List.of() : safeList(selectedVariant.selectedOptions()),
                selectedVariant == null ? null : selectedVariant.price(),
                selectedVariant == null ? null : selectedVariant.currency(),
                selectedVariant == null ? null : selectedVariant.imageUrl(),
                selectedVariant == null ? null : selectedVariant.imageAltText(),
                selectedVariant == null ? null : selectedVariant.available(),
                productCandidate.catalogRank(),
                rerankResult.relevanceScore(),
                rank
        );
    }

    private int valueOrDefault(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean matchesCatalogFilters(
            MerchantCatalogProductCandidate productCandidate,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        return matchesCategories(productCandidate, filters)
                && matchesPrice(
                productCandidate.priceMinAmount(),
                productCandidate.priceMaxAmount(),
                productCandidate.priceCurrency(),
                context,
                filters
        );
    }

    private boolean matchesProductFilters(
            MerchantSemanticProductResult product,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        String currency = firstPresent(
                product.selectedVariantPriceCurrency(),
                product.detailPriceCurrency(),
                product.priceCurrency()
        );
        Long selectedVariantPrice = decimalAmountToMinor(product.selectedVariantPriceAmount(), currency);
        Long detailMin = decimalAmountToMinor(product.detailPriceMin(), currency);
        Long detailMax = decimalAmountToMinor(product.detailPriceMax(), currency);
        Long minAmount = firstPresent(selectedVariantPrice, detailMin, product.priceMinAmount());
        Long maxAmount = firstPresent(selectedVariantPrice, detailMax, product.priceMaxAmount(), minAmount);
        return matchesPrice(minAmount, maxAmount, currency, context, filters);
    }

    private boolean matchesCategories(
            MerchantCatalogProductCandidate productCandidate,
            CatalogSearchFilters filters
    ) {
        if (filters == null || safeList(filters.categories()).isEmpty()) {
            return true;
        }
        List<String> productCategories = Stream.concat(
                        productCandidate.categoryValues().stream(),
                        productCandidate.tagValues().stream()
                )
                .map(this::normalizedValue)
                .filter(value -> !value.isBlank())
                .toList();
        return safeList(filters.categories()).stream()
                .map(this::normalizedValue)
                .filter(value -> !value.isBlank())
                .anyMatch(category -> productCategories.stream()
                        .anyMatch(productCategory -> productCategory.equals(category)
                                || productCategory.contains(category)));
    }

    private boolean matchesPrice(
            Long minAmount,
            Long maxAmount,
            String currency,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        CatalogSearchPriceFilter price = filters == null ? null : filters.price();
        if (price == null || (price.min() == null && price.max() == null)) {
            return true;
        }
        String expectedCurrency = normalizedCurrency(context == null ? null : context.currency());
        if (expectedCurrency != null && !expectedCurrency.equals(normalizedCurrency(currency))) {
            return false;
        }
        if (price.min() != null && (maxAmount == null || maxAmount < price.min())) {
            return false;
        }
        return price.max() == null || (minAmount != null && minAmount <= price.max());
    }

    private Long decimalAmountToMinor(String amount, String currency) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        String cleaned = amount.trim()
                .replaceAll("[^0-9,.\\-]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.contains(".") && cleaned.contains(",")) {
            cleaned = cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            int commaIndex = cleaned.lastIndexOf(',');
            cleaned = cleaned.length() - commaIndex == 3
                    ? cleaned.replace(',', '.')
                    : cleaned.replace(",", "");
        }
        try {
            BigDecimal decimal = new BigDecimal(cleaned);
            return decimal
                    .movePointRight(currencyExponent(currency))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int currencyExponent(String currency) {
        return switch (normalizedCurrency(currency) == null ? "" : normalizedCurrency(currency)) {
            case "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND",
                    "VUV", "XAF", "XOF", "XPF" -> 0;
            case "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3;
            default -> 2;
        };
    }

    private String normalizedCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizedValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstPresent(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return third;
    }

    private Long firstPresent(Long first, Long second, Long third) {
        if (first != null) {
            return first;
        }
        return second == null ? third : second;
    }

    private Long firstPresent(Long first, Long second, Long third, Long fourth) {
        Long value = firstPresent(first, second, third);
        return value == null ? fourth : value;
    }

    private record MerchantCatalogSearchOutcome(
            MerchantCatalogSearchAttemptResult merchantAttempt,
            List<MerchantCatalogProductCandidate> productCandidates
    ) {
    }

}
