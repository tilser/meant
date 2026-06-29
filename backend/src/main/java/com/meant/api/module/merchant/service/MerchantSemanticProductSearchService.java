package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.plugin.catalog.service.MerchantCatalogPluginDispatchService;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    private static final int MAX_METADATA_DEPTH = 64;
    private static final Pattern AUDIENCE_WORD_SEPARATOR_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern AUDIENCE_SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern MEN_AUDIENCE_PATTERN =
            Pattern.compile("\\b(?:men|men s|mens|man|man s|male|males)\\b");
    private static final Pattern WOMEN_AUDIENCE_PATTERN =
            Pattern.compile("\\b(?:women|women s|womens|woman|woman s|ladies|lady|female|females)\\b");
    private static final Pattern CHILDREN_AUDIENCE_PATTERN =
            Pattern.compile(
                    "\\b(?:kids|kid|children|children s|child|child s|boys|boy|boy s|girls|girl|girl s|youth"
                            + "|toddler|toddlers|baby|babies)\\b");
    private static final Pattern UNISEX_AUDIENCE_PATTERN =
            Pattern.compile("\\b(?:unisex|gender neutral|all gender|all genders|everyone)\\b");

    private final MerchantSemanticSearchService merchantSemanticSearchService;
    private final MerchantCatalogPluginDispatchService merchantCatalogPluginDispatchService;
    private final VoyageRerankClient voyageRerankClient;
    private final MerchantLookupService merchantLookupService;
    private final MerchantCatalogSearchProperties merchantCatalogSearchProperties;

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

    private List<MerchantSemanticProductResult> rerankProducts(
            String query,
            List<MerchantCatalogProductCandidate> productCandidates,
            int productLimit,
            CatalogSearchContext context,
            CatalogSearchFilters filters,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        ProductAudience requiredAudience = requiredAudience(query, context);
        List<MerchantCatalogProductCandidate> distinctProductCandidates = distinctProductCandidates(productCandidates);
        List<MerchantCatalogProductCandidate> filteredProductCandidates = distinctProductCandidates.stream()
                .filter(productCandidate -> matchesCatalogFilters(productCandidate, requiredAudience, context, filters))
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
        emitCatalogCandidates(filteredProductCandidates, rerankedProducts, candidateConsumer);

        return productResults(query, filteredProductCandidates, rerankedProducts, context).stream()
                .filter(product -> matchesProductFilters(product, requiredAudience, context, filters))
                .toList();
    }

    private void emitCatalogCandidates(
            List<MerchantCatalogProductCandidate> filteredProductCandidates,
            List<VoyageRerankResult> rerankedProducts,
            Consumer<MerchantSemanticProductResult> candidateConsumer
    ) {
        if (candidateConsumer == null) {
            return;
        }
        IntStream.range(0, rerankedProducts.size())
                .mapToObj(index -> toProductResult(
                        filteredProductCandidates.get(rerankedProducts.get(index).index()),
                        rerankedProducts.get(index),
                        index + 1,
                        null,
                        (ProductDetailsResult) null
                ))
                .forEach(candidateConsumer);
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
        RichCatalogData richCatalogData = richCatalogData(productCandidate, detailProduct, detailPriceRange, selectedVariant);
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

    private RichCatalogData richCatalogData(
            MerchantCatalogProductCandidate productCandidate,
            ProductDetailsResponse.Product detailProduct,
            ProductDetailsResponse.PriceRange detailPriceRange,
            ProductDetailsResponse.SelectedVariant selectedVariant
    ) {
        CatalogSearchResponse.Product catalogProduct = productCandidate.product();
        String currency = firstPresent(
                selectedVariant == null ? null : selectedVariant.currency(),
                detailPriceRange == null ? null : detailPriceRange.currency(),
                productCandidate.priceCurrency()
        );
        UcpMoney listPrice = firstPresent(
                UcpMoney.value(selectedVariant == null ? null : selectedVariant.listPrice(), currency),
                UcpMoney.value(detailProduct == null ? null : detailProduct.listPrice(), currency),
                firstVariantListPrice(catalogProduct, currency),
                UcpMoney.value(catalogProduct.listPrice(), currency)
        );
        List<ProductCatalogAttribute> attributes = richAttributes(catalogProduct, detailProduct);
        return new RichCatalogData(
                listPrice == null ? null : listPrice.amount(),
                listPrice == null ? null : firstPresent(listPrice.currency(), currency),
                firstPresent(
                        UcpDecimal.ratingValue(detailProduct == null ? null : detailProduct.rating()),
                        UcpDecimal.ratingValue(catalogProduct.rating())
                ),
                firstPresent(
                        UcpDecimal.reviewCountValue(detailProduct == null ? null : detailProduct.reviewCount()),
                        UcpDecimal.reviewCountValue(detailProduct == null ? null : detailProduct.rating()),
                        UcpDecimal.reviewCountValue(catalogProduct.reviewCount()),
                        UcpDecimal.reviewCountValue(catalogProduct.rating())
                ),
                richMedia(catalogProduct, detailProduct, selectedVariant),
                richCategories(catalogProduct),
                richMetadataValues(
                        Stream.of(catalogProduct.certifications(), detailProduct == null ? null : detailProduct.certifications())
                                .toList(),
                        attributes,
                        List.of("certif", "standard", "compliance")
                ),
                richMetadataValues(
                        Stream.of(catalogProduct.materials(), detailProduct == null ? null : detailProduct.materials())
                                .toList(),
                        attributes,
                        List.of("material", "fabric", "fiber", "fibre", "composition", "ingredient")
                ),
                richSkus(catalogProduct, detailProduct, selectedVariant),
                distinctStrings(Stream.concat(
                        stringValues(catalogProduct.collections()).stream(),
                        stringValues(detailProduct == null ? null : detailProduct.collections()).stream()
                )),
                attributes
        );
    }

    private UcpMoney firstVariantListPrice(CatalogSearchResponse.Product product, String currency) {
        if (product == null) {
            return null;
        }
        return safeNonNullList(product.variants()).stream()
                .map(variant -> UcpMoney.value(variant.listPrice(), currency))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<ProductCatalogMedia> richMedia(
            CatalogSearchResponse.Product catalogProduct,
            ProductDetailsResponse.Product detailProduct,
            ProductDetailsResponse.SelectedVariant selectedVariant
    ) {
        Map<String, ProductCatalogMedia> media = new LinkedHashMap<>();
        safeNonNullList(catalogProduct.media()).forEach(item -> addMedia(media, media(item)));
        safeNonNullList(catalogProduct.variants()).stream()
                .flatMap(variant -> safeNonNullList(variant.media()).stream())
                .map(this::media)
                .forEach(item -> addMedia(media, item));
        if (detailProduct != null) {
            addMedia(media, imageMedia(detailProduct.imageUrl(), null));
            safeNonNullList(detailProduct.images()).stream()
                    .map(this::media)
                    .forEach(item -> addMedia(media, item));
            safeNonNullList(detailProduct.media()).stream()
                    .map(this::media)
                    .forEach(item -> addMedia(media, item));
        }
        if (selectedVariant != null) {
            addMedia(media, imageMedia(selectedVariant.imageUrl(), selectedVariant.imageAltText()));
            safeNonNullList(selectedVariant.media()).stream()
                    .map(this::media)
                    .forEach(item -> addMedia(media, item));
        }
        return List.copyOf(media.values());
    }

    private ProductCatalogMedia media(CatalogSearchResponse.Media media) {
        if (media == null) {
            return null;
        }
        return new ProductCatalogMedia(
                blankToDefault(media.type(), "image"),
                firstPresent(media.url(), media.previewImageUrl()),
                blankToNull(media.altText())
        );
    }

    private ProductCatalogMedia media(ProductDetailsResponse.Image image) {
        if (image == null) {
            return null;
        }
        return imageMedia(image.url(), image.altText());
    }

    private ProductCatalogMedia media(ProductDetailsResponse.Media media) {
        if (media == null) {
            return null;
        }
        return new ProductCatalogMedia(
                blankToDefault(media.type(), "image"),
                firstPresent(media.url(), media.previewImageUrl()),
                blankToNull(media.altText())
        );
    }

    private ProductCatalogMedia imageMedia(String url, String altText) {
        return new ProductCatalogMedia("image", blankToNull(url), blankToNull(altText));
    }

    private void addMedia(Map<String, ProductCatalogMedia> media, ProductCatalogMedia item) {
        if (item == null || item.url() == null || item.url().isBlank()) {
            return;
        }
        String type = blankToDefault(item.type(), "image");
        media.putIfAbsent(type.toLowerCase(Locale.ROOT) + "|" + item.url(), new ProductCatalogMedia(
                type,
                item.url(),
                blankToNull(item.altText())
        ));
    }

    private List<ProductCatalogCategory> richCategories(CatalogSearchResponse.Product product) {
        Map<String, ProductCatalogCategory> categories = new LinkedHashMap<>();
        for (CatalogSearchResponse.Category category : safeNonNullList(product.categories())) {
            String value = blankToNull(category.value());
            if (value != null) {
                categories.putIfAbsent(
                        value.toLowerCase(Locale.ROOT) + "|" + blankToDefault(category.taxonomy(), ""),
                        new ProductCatalogCategory(value, blankToNull(category.taxonomy()))
                );
            }
        }
        return List.copyOf(categories.values());
    }

    private List<String> richSkus(
            CatalogSearchResponse.Product catalogProduct,
            ProductDetailsResponse.Product detailProduct,
            ProductDetailsResponse.SelectedVariant selectedVariant
    ) {
        return distinctStrings(Stream.of(
                        stringValues(catalogProduct.skus()).stream(),
                        safeNonNullList(catalogProduct.variants()).stream().map(CatalogSearchResponse.Variant::sku),
                        stringValues(detailProduct == null ? null : detailProduct.skus()).stream(),
                        Stream.of(selectedVariant == null ? null : selectedVariant.sku())
                )
                .flatMap(stream -> stream));
    }

    private List<ProductCatalogAttribute> richAttributes(
            CatalogSearchResponse.Product catalogProduct,
            ProductDetailsResponse.Product detailProduct
    ) {
        Map<String, ProductCatalogAttribute> attributes = new LinkedHashMap<>();
        Stream.of(
                        catalogProduct.metadata(),
                        catalogProduct.metafields(),
                        catalogProduct.techSpecs(),
                        detailProduct == null ? null : detailProduct.metadata(),
                        detailProduct == null ? null : detailProduct.metafields(),
                        detailProduct == null ? null : detailProduct.techSpecs()
                )
                .flatMap(value -> attributes(value).stream())
                .forEach(attribute -> {
                    String name = blankToNull(attribute.name());
                    String value = blankToNull(attribute.value());
                    if (name != null && value != null) {
                        attributes.putIfAbsent(name.toLowerCase(Locale.ROOT) + "|" + value.toLowerCase(Locale.ROOT),
                                new ProductCatalogAttribute(name, value));
                    }
                });
        return List.copyOf(attributes.values());
    }

    private List<String> richMetadataValues(
            List<Object> explicitValues,
            List<ProductCatalogAttribute> attributes,
            List<String> attributeKeyFragments
    ) {
        Stream<String> explicit = explicitValues.stream()
                .flatMap(value -> stringValues(value).stream());
        Stream<String> inferred = attributes.stream()
                .filter(attribute -> containsAny(attribute.name(), attributeKeyFragments))
                .flatMap(attribute -> stringValues(attribute.value()).stream());
        return distinctStrings(Stream.concat(explicit, inferred));
    }

    private List<ProductCatalogAttribute> attributes(Object value) {
        if (value == null) {
            return List.of();
        }
        List<ProductCatalogAttribute> attributes = new ArrayList<>();
        List<AttributeNode> stack = new ArrayList<>();
        stack.add(new AttributeNode("metadata", value, 0));
        while (!stack.isEmpty()) {
            AttributeNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null) {
                continue;
            }
            addAttributeValue(attributes, stack, node.name(), node.value(), node.depth());
        }
        return attributes;
    }

    private void addAttributeValue(
            List<ProductCatalogAttribute> attributes,
            List<AttributeNode> stack,
            String key,
            Object value,
            int depth
    ) {
        if (value instanceof Map<?, ?> map) {
            Object namedValue = firstMapValue(map, "value", "values", "text", "description");
            String namedKey = firstPresent(firstStringValue(map, "name", "key", "label", "title"), key);
            if (namedValue != null) {
                String stringValue = String.join(", ", stringValues(namedValue));
                if (!stringValue.isBlank()) {
                    attributes.add(new ProductCatalogAttribute(namedKey, stringValue));
                }
                return;
            }
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            for (int index = entries.size() - 1; index >= 0; index--) {
                Map.Entry<?, ?> entry = entries.get(index);
                String nestedName = scalarString(entry.getKey());
                if (nestedName != null) {
                    String attributeName = "metadata".equals(key) ? nestedName : key + " " + nestedName;
                    stack.add(new AttributeNode(attributeName, entry.getValue(), depth + 1));
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            List<String> values = stringValues(collection);
            if (!values.isEmpty()) {
                attributes.add(new ProductCatalogAttribute(key, String.join(", ", values)));
            }
            return;
        }
        String scalar = scalarString(value);
        if (scalar != null) {
            attributes.add(new ProductCatalogAttribute(key, scalar));
        }
    }

    private List<String> stringValues(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        List<ValueNode> stack = new ArrayList<>();
        stack.add(new ValueNode(value, 0));
        while (!stack.isEmpty()) {
            ValueNode node = stack.removeLast();
            if (node.depth() > MAX_METADATA_DEPTH || node.value() == null) {
                continue;
            }
            if (node.value() instanceof Collection<?> collection) {
                List<?> items = new ArrayList<>(collection);
                for (int index = items.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(items.get(index), node.depth() + 1));
                }
                continue;
            }
            if (node.value() instanceof Map<?, ?> map) {
                Object namedValues = firstMapValue(map, "values", "value", "name", "label", "title", "text");
                if (namedValues != null) {
                    stack.add(new ValueNode(namedValues, node.depth() + 1));
                    continue;
                }
                List<?> mapValues = new ArrayList<>(map.values());
                for (int index = mapValues.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(mapValues.get(index), node.depth() + 1));
                }
                continue;
            }
            String scalar = scalarString(node.value());
            if (scalar != null) {
                Stream.of(scalar.split("\\s*[,;/|]\\s*"))
                        .map(this::blankToNull)
                        .filter(Objects::nonNull)
                        .forEach(values::add);
            }
        }
        return values;
    }

    private Object firstMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String firstStringValue(Map<?, ?> map, String... keys) {
        Object value = firstMapValue(map, keys);
        return scalarString(value);
    }

    private List<String> distinctStrings(Stream<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        return values
                .map(this::blankToNull)
                .filter(Objects::nonNull)
                .filter(value -> seen.add(value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private boolean containsAny(String value, List<String> fragments) {
        String normalized = normalizedValue(value);
        return fragments.stream().anyMatch(normalized::contains);
    }

    private String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return blankToNull(string);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return blankToNull(value.toString());
        }
        return null;
    }

    private String blankToDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int valueOrDefault(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean matchesCatalogFilters(
            MerchantCatalogProductCandidate productCandidate,
            ProductAudience requiredAudience,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        return matchesCategories(productCandidate, filters)
                && matchesAudience(catalogAudienceText(productCandidate), requiredAudience)
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
            ProductAudience requiredAudience,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        String currency = firstPresent(
                product.selectedVariantPriceCurrency(),
                product.detailPriceCurrency(),
                product.priceCurrency()
        );
        Long selectedVariantPrice = UcpDecimal.decimalAmountToMinor(product.selectedVariantPriceAmount(), currency);
        Long detailMin = UcpDecimal.decimalAmountToMinor(product.detailPriceMin(), currency);
        Long detailMax = UcpDecimal.decimalAmountToMinor(product.detailPriceMax(), currency);
        Long minAmount = firstPresent(selectedVariantPrice, detailMin, product.priceMinAmount());
        Long maxAmount = firstPresent(selectedVariantPrice, detailMax, product.priceMaxAmount(), minAmount);
        return matchesPrice(minAmount, maxAmount, currency, context, filters)
                && matchesAudience(productAudienceText(product), requiredAudience);
    }

    private ProductAudience requiredAudience(String query, CatalogSearchContext context) {
        String normalizedIntent = normalizedAudienceText(context == null ? null : context.intent());
        if (normalizedIntent.contains("hard apparel audience filter men s sizing")
                || normalizedIntent.contains("clothing fit signal prefer men s sizing")) {
            return ProductAudience.MEN;
        }
        if (normalizedIntent.contains("hard apparel audience filter women s sizing")
                || normalizedIntent.contains("clothing fit signal prefer women s sizing")) {
            return ProductAudience.WOMEN;
        }

        AudienceEvidence queryEvidence = audienceEvidence(query);
        if (queryEvidence.men() && !queryEvidence.women() && !queryEvidence.children()) {
            return ProductAudience.MEN;
        }
        if (queryEvidence.women() && !queryEvidence.men() && !queryEvidence.children()) {
            return ProductAudience.WOMEN;
        }
        return null;
    }

    private boolean matchesAudience(String audienceText, ProductAudience requiredAudience) {
        if (requiredAudience == null) {
            return true;
        }
        AudienceEvidence evidence = audienceEvidence(audienceText);
        if (!evidence.hasExplicitAudience() || evidence.unisex()) {
            return true;
        }
        return switch (requiredAudience) {
            case MEN -> evidence.men() || (!evidence.women() && !evidence.children());
            case WOMEN -> evidence.women() || (!evidence.men() && !evidence.children());
        };
    }

    private AudienceEvidence audienceEvidence(String value) {
        String normalized = normalizedAudienceText(value);
        if (normalized.isBlank()) {
            return new AudienceEvidence(false, false, false, false);
        }
        return new AudienceEvidence(
                MEN_AUDIENCE_PATTERN.matcher(normalized).find(),
                WOMEN_AUDIENCE_PATTERN.matcher(normalized).find(),
                CHILDREN_AUDIENCE_PATTERN.matcher(normalized).find(),
                UNISEX_AUDIENCE_PATTERN.matcher(normalized).find()
        );
    }

    private String catalogAudienceText(MerchantCatalogProductCandidate productCandidate) {
        CatalogSearchResponse.Product product = productCandidate.product();
        return joinedAudienceText(Stream.of(
                        Stream.of(product.title(), productCandidate.descriptionHtml()),
                        productCandidate.categoryValues().stream(),
                        productCandidate.tagValues().stream(),
                        stringValues(product.materials()).stream(),
                        stringValues(product.collections()).stream(),
                        stringValues(product.metadata()).stream(),
                        stringValues(product.metafields()).stream(),
                        stringValues(product.techSpecs()).stream(),
                        safeNonNullList(product.variants()).stream()
                                .flatMap(variant -> Stream.of(
                                        variant.title(),
                                        variant.description() == null ? null : variant.description().html()
                                ))
                )
                .flatMap(stream -> stream));
    }

    private String productAudienceText(MerchantSemanticProductResult product) {
        return joinedAudienceText(Stream.of(
                        Stream.of(
                                product.title(),
                                product.detailDescription(),
                                product.descriptionHtml(),
                                product.selectedVariantTitle(),
                                product.selectedVariantImageAltText()
                        ),
                        safeList(product.categories()).stream()
                                .flatMap(category -> Stream.of(category.value(), category.taxonomy())),
                        safeList(product.media()).stream().map(ProductCatalogMedia::altText),
                        safeList(product.detailImages()).stream().map(ProductDetailsResponse.Image::altText),
                        safeList(product.detailOptions()).stream()
                                .flatMap(option -> Stream.concat(
                                        Stream.of(option.name()),
                                        safeList(option.values()).stream()
                                )),
                        safeList(product.selectedOptions()).stream()
                                .flatMap(option -> Stream.of(option.name(), option.value())),
                        safeList(product.materials()).stream(),
                        safeList(product.collections()).stream(),
                        safeList(product.attributes()).stream()
                                .flatMap(attribute -> Stream.of(attribute.name(), attribute.value()))
                )
                .flatMap(stream -> stream));
    }

    private String joinedAudienceText(Stream<String> values) {
        return values
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String normalizedAudienceText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = AUDIENCE_WORD_SEPARATOR_PATTERN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return AUDIENCE_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
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
        String expectedCurrency = UcpDecimal.normalizedCurrency(context == null ? null : context.currency());
        String productCurrency = UcpDecimal.normalizedCurrency(currency);
        if (expectedCurrency != null && productCurrency != null && !expectedCurrency.equals(productCurrency)) {
            return false;
        }
        if (price.min() != null && !hasKnownPriceAtOrAbove(minAmount, maxAmount, price.min())) {
            return false;
        }
        return price.max() == null || hasKnownPriceAtOrBelow(minAmount, maxAmount, price.max());
    }

    private boolean hasKnownPriceAtOrAbove(Long minAmount, Long maxAmount, Long threshold) {
        if (maxAmount != null) {
            return maxAmount >= threshold;
        }
        return minAmount != null && minAmount >= threshold;
    }

    private boolean hasKnownPriceAtOrBelow(Long minAmount, Long maxAmount, Long threshold) {
        if (minAmount != null) {
            return minAmount <= threshold;
        }
        return maxAmount != null && maxAmount <= threshold;
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

    private String firstPresent(String first, String second) {
        return firstPresent(first, second, null);
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

    private UcpMoney firstPresent(UcpMoney... values) {
        for (UcpMoney value : values) {
            if (value != null && value.amount() != null) {
                return value;
            }
        }
        return null;
    }

    private Double firstPresent(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer firstPresent(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record MerchantCatalogSearchOutcome(
            MerchantCatalogSearchAttemptResult merchantAttempt,
            List<MerchantCatalogProductCandidate> productCandidates
    ) {
    }

    private record RichCatalogData(
            Long listPriceAmount,
            String listPriceCurrency,
            Double ratingScore,
            Integer reviewCount,
            List<ProductCatalogMedia> media,
            List<ProductCatalogCategory> categories,
            List<String> certifications,
            List<String> materials,
            List<String> skus,
            List<String> collections,
            List<ProductCatalogAttribute> attributes
    ) {
    }

    private record AttributeNode(String name, Object value, int depth) {
    }

    private record ValueNode(Object value, int depth) {
    }

    private enum ProductAudience {
        MEN,
        WOMEN
    }

    private record AudienceEvidence(
            boolean men,
            boolean women,
            boolean children,
            boolean unisex
    ) {

        boolean hasExplicitAudience() {
            return men || women || children || unisex;
        }
    }

}
