package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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

        List<MerchantSemanticSearchResult> merchants = merchantSemanticSearchService.search(
                new SemanticMerchantSearchQuery(query.query(), merchantCandidateLimit)
        );
        List<MerchantSemanticSearchResult> topMerchants = merchants.stream()
                .limit(merchantLimit)
                .toList();

        List<MerchantCatalogSearchAttemptResult> merchantAttempts = new ArrayList<>();
        List<MerchantCatalogProductCandidate> productCandidates = new ArrayList<>();
        for (MerchantSemanticSearchResult merchant : topMerchants) {
            searchMerchantCatalog(query.query(), productsPerMerchant, merchant, merchantAttempts, productCandidates);
        }

        return new MerchantSemanticProductSearchResult(
                merchantAttempts,
                rerankProducts(query.query(), productCandidates, productLimit)
        );
    }

    private void searchMerchantCatalog(
            String query,
            int productsPerMerchant,
            MerchantSemanticSearchResult merchant,
            List<MerchantCatalogSearchAttemptResult> merchantAttempts,
            List<MerchantCatalogProductCandidate> productCandidates
    ) {
        try {
            CatalogSearchResult catalogSearchResult = merchantCatalogSearchClient.searchCatalog(
                    merchant,
                    query,
                    productsPerMerchant
            );
            merchantAttempts.add(MerchantCatalogSearchAttemptResult.success(
                    merchant,
                    catalogSearchResult.endpoint(),
                    catalogSearchResult.products().size()
            ));
            addProductCandidates(productCandidates, merchant, catalogSearchResult);
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
            merchantAttempts.add(MerchantCatalogSearchAttemptResult.failure(merchant, exception.getMessage()));
        }
    }

    private void addProductCandidates(
            List<MerchantCatalogProductCandidate> productCandidates,
            MerchantSemanticSearchResult merchant,
            CatalogSearchResult catalogSearchResult
    ) {
        IntStream.range(0, catalogSearchResult.products().size())
                .mapToObj(index -> new MerchantCatalogProductCandidate(
                        merchant,
                        catalogSearchResult.endpoint(),
                        catalogSearchResult.products().get(index),
                        index + 1
                ))
                .forEach(productCandidates::add);
    }

    private List<MerchantSemanticProductResult> rerankProducts(
            String query,
            List<MerchantCatalogProductCandidate> productCandidates,
            int productLimit
    ) {
        List<MerchantCatalogProductCandidate> distinctProductCandidates = distinctProductCandidates(productCandidates);
        if (distinctProductCandidates.isEmpty()) {
            return List.of();
        }

        List<String> documents = distinctProductCandidates.stream()
                .map(MerchantCatalogProductCandidate::rerankDocument)
                .toList();
        List<VoyageRerankResult> rerankedProducts = voyageRerankClient.rerank(query, documents).stream()
                .sorted(Comparator.comparingDouble(VoyageRerankResult::relevanceScore)
                        .reversed()
                        .thenComparingInt(VoyageRerankResult::index))
                .limit(productLimit)
                .toList();

        return IntStream.range(0, rerankedProducts.size())
                .mapToObj(index -> toProductResult(
                        distinctProductCandidates.get(rerankedProducts.get(index).index()),
                        rerankedProducts.get(index),
                        index + 1,
                        query
                ))
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
            String query
    ) {
        try {
            return toProductResult(productCandidate, rerankResult, rank, null, productDetails(productCandidate));
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
            return toProductResult(productCandidate, rerankResult, rank, exception.getMessage(), null);
        }
    }

    private ProductDetailsResult productDetails(MerchantCatalogProductCandidate productCandidate) {
        return merchantProductDetailsClient.getProductDetails(
                productCandidate.merchant(),
                productCandidate.product().id()
        );
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
