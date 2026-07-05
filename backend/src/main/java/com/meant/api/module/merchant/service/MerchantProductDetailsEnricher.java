package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.dto.ProductRichCatalogData;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.plugin.catalog.common.service.MerchantCatalogPluginDispatchService;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantProductDetailsEnricher {

    private final MerchantCatalogPluginDispatchService merchantCatalogPluginDispatchService;
    private final MerchantRichCatalogNormalizer richCatalogNormalizer;

    void emitCatalogCandidates(
            List<MerchantCatalogProductCandidate> filteredProductCandidates,
            int productLimit,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        if (candidateConsumer == null) {
            return;
        }
        IntStream.range(0, Math.min(filteredProductCandidates.size(), productLimit))
                .mapToObj(index -> toProductResult(
                        filteredProductCandidates.get(index),
                        new VoyageRerankResult(index, 0d),
                        index + 1,
                        null,
                        (ProductDetailsResult) null
                ))
                .forEach(candidateConsumer);
    }

    List<MerchantSemanticProductResult> productResults(
            String query,
            List<MerchantCatalogProductCandidate> filteredProductCandidates,
            List<VoyageRerankResult> rerankedProducts,
            CatalogSearchContext context,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<MerchantSemanticProductResult>> futures = IntStream.range(0, rerankedProducts.size())
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> toProductResult(
                            filteredProductCandidates.get(rerankedProducts.get(index).index()),
                            rerankedProducts.get(index),
                            index + 1,
                            query,
                            context
                    ), executor)
                            .whenComplete((product, exception) -> emitProductUpdate(product, candidateConsumer))
                            .exceptionally(exception -> {
                                log.error("Parallel merchant product details search failed", exception);
                                return null;
                            }))
                    .toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .handle((ignored, exception) -> futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .toList())
                    .join();
        }
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
        NegotiatedCapabilities activeCapabilities = productCandidate.negotiatedCapabilities();
        CatalogLookupResult lookupResult = merchantCatalogPluginDispatchService.lookupCatalog(
                productCandidate.merchant(),
                productCandidate.product().id(),
                context,
                activeCapabilities
        );
        activeCapabilities = activeCapabilities(lookupResult.negotiatedCapabilities(), activeCapabilities);
        return merchantCatalogPluginDispatchService.getProduct(
                productCandidate.merchant(),
                lookupResult.productId(),
                context,
                activeCapabilities
        );
    }

    private NegotiatedCapabilities activeCapabilities(
            NegotiatedCapabilities current,
            NegotiatedCapabilities fallback
    ) {
        return current == null || current.versions().isEmpty() ? fallback : current;
    }

    private void emitProductUpdate(
            MerchantSemanticProductResult product,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        if (product == null || candidateConsumer == null) {
            return;
        }
        try {
            candidateConsumer.accept(product);
        } catch (RuntimeException exception) {
            log.debug(
                    "Product search candidate consumer rejected an enrichment update. productId={}",
                    product.productId(),
                    exception
            );
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
        if (selectedVariant == null) {
            selectedVariant = catalogSelectedVariant(productCandidate.product());
        }
        ProductRichCatalogData richCatalogData = richCatalogNormalizer.normalize(
                productCandidate,
                detailProduct,
                detailPriceRange,
                selectedVariant
        );
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
                richCatalogData.listPriceAmount(),
                richCatalogData.listPriceCurrency(),
                richCatalogData.ratingScore(),
                richCatalogData.reviewCount(),
                richCatalogData.media(),
                richCatalogData.categories(),
                richCatalogData.certifications(),
                richCatalogData.materials(),
                richCatalogData.skus(),
                richCatalogData.collections(),
                richCatalogData.attributes(),
                productCandidate.available(),
                detailError,
                detailProduct == null ? null : detailProduct.description(),
                detailProduct == null ? null : detailProduct.imageUrl(),
                detailProduct == null ? List.of() : safeNonNullList(detailProduct.images()),
                detailProduct == null ? List.of() : safeNonNullList(detailProduct.options()),
                detailPriceRange == null ? null : detailPriceRange.min(),
                detailPriceRange == null ? null : detailPriceRange.max(),
                detailPriceRange == null ? null : detailPriceRange.currency(),
                detailProduct == null ? null : detailProduct.totalVariants(),
                detailProduct == null ? null : detailProduct.requiresSellingPlan(),
                detailProduct == null ? List.of() : safeNonNullList(detailProduct.sellingPlanGroups()),
                selectedVariant == null ? null : selectedVariant.variantId(),
                selectedVariant == null ? null : selectedVariant.title(),
                selectedVariant == null ? List.of() : safeNonNullList(selectedVariant.selectedOptions()),
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

    private ProductDetailsResponse.SelectedVariant catalogSelectedVariant(CatalogSearchResponse.Product product) {
        CatalogSearchResponse.Variant variant = safeNonNullList(product.variants()).stream()
                .filter(candidate -> candidate != null
                        && candidate.availability() != null
                        && Boolean.TRUE.equals(candidate.availability().available()))
                .findFirst()
                .orElseGet(() -> safeNonNullList(product.variants()).stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
        if (variant == null) {
            return null;
        }
        CatalogSearchResponse.Media media = safeNonNullList(variant.media()).stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.url() != null && !candidate.url().isBlank())
                .findFirst()
                .orElse(null);
        boolean imageMedia = media != null && (media.type() == null
                || media.type().isBlank()
                || media.type().equalsIgnoreCase("image"));
        return new ProductDetailsResponse.SelectedVariant(
                variant.id(),
                variant.title(),
                catalogAmount(variant.price()),
                variant.price() == null ? null : variant.price().currency(),
                variant.sku(),
                variant.listPrice(),
                imageMedia ? media.url() : null,
                imageMedia ? media.altText() : null,
                media == null ? List.of() : List.of(new ProductDetailsResponse.Media(
                        media.type(),
                        media.url(),
                        media.altText(),
                        media.previewImageUrl()
                )),
                variant.availability() == null ? null : variant.availability().available(),
                List.of()
        );
    }

    private String catalogAmount(CatalogSearchResponse.Money money) {
        return money == null || money.amount() == null ? null : money.amount().toString();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
