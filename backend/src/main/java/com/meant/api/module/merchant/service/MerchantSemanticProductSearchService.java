package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        MoneyValue listPrice = firstPresent(
                moneyValue(selectedVariant == null ? null : selectedVariant.listPrice(), currency),
                moneyValue(detailProduct == null ? null : detailProduct.listPrice(), currency),
                firstVariantListPrice(catalogProduct, currency),
                moneyValue(catalogProduct.listPrice(), currency)
        );
        List<ProductCatalogAttribute> attributes = richAttributes(catalogProduct, detailProduct);
        return new RichCatalogData(
                listPrice == null ? null : listPrice.amount(),
                listPrice == null ? null : firstPresent(listPrice.currency(), currency),
                firstPresent(
                        ratingValue(detailProduct == null ? null : detailProduct.rating()),
                        ratingValue(catalogProduct.rating())
                ),
                firstPresent(
                        reviewCountValue(detailProduct == null ? null : detailProduct.reviewCount()),
                        reviewCountValue(detailProduct == null ? null : detailProduct.rating()),
                        reviewCountValue(catalogProduct.reviewCount()),
                        reviewCountValue(catalogProduct.rating())
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

    private MoneyValue firstVariantListPrice(CatalogSearchResponse.Product product, String currency) {
        return safeList(product.variants()).stream()
                .map(variant -> moneyValue(variant.listPrice(), currency))
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
        safeList(catalogProduct.media()).forEach(item -> addMedia(media, media(item)));
        safeList(catalogProduct.variants()).stream()
                .flatMap(variant -> safeList(variant.media()).stream())
                .map(this::media)
                .forEach(item -> addMedia(media, item));
        if (detailProduct != null) {
            addMedia(media, imageMedia(detailProduct.imageUrl(), null));
            safeList(detailProduct.images()).stream()
                    .map(this::media)
                    .forEach(item -> addMedia(media, item));
            safeList(detailProduct.media()).stream()
                    .map(this::media)
                    .forEach(item -> addMedia(media, item));
        }
        if (selectedVariant != null) {
            addMedia(media, imageMedia(selectedVariant.imageUrl(), selectedVariant.imageAltText()));
            safeList(selectedVariant.media()).stream()
                    .map(this::media)
                    .forEach(item -> addMedia(media, item));
        }
        return List.copyOf(media.values());
    }

    private ProductCatalogMedia media(CatalogSearchResponse.Media media) {
        return new ProductCatalogMedia(
                blankToDefault(media.type(), "image"),
                firstPresent(media.url(), media.previewImageUrl()),
                blankToNull(media.altText())
        );
    }

    private ProductCatalogMedia media(ProductDetailsResponse.Image image) {
        return imageMedia(image.url(), image.altText());
    }

    private ProductCatalogMedia media(ProductDetailsResponse.Media media) {
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
        for (CatalogSearchResponse.Category category : safeList(product.categories())) {
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
                        safeList(catalogProduct.variants()).stream().map(CatalogSearchResponse.Variant::sku),
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

    private MoneyValue moneyValue(Object value, String fallbackCurrency) {
        if (value == null) {
            return null;
        }
        if (value instanceof CatalogSearchResponse.Money money) {
            return money.amount() == null ? null : new MoneyValue(money.amount(), firstPresent(money.currency(), fallbackCurrency));
        }
        if (value instanceof Map<?, ?> map) {
            String currency = firstPresent(firstStringValue(map, "currency", "currencyCode"), fallbackCurrency);
            Object explicitMinorAmount = firstMapValue(map,
                    "minorAmount",
                    "minor_amount",
                    "amountMinor",
                    "amount_minor",
                    "amountInMinorUnits",
                    "amount_in_minor_units",
                    "amountCents",
                    "amount_cents",
                    "cents");
            Long explicitMinor = wholeNumberAmount(explicitMinorAmount);
            if (explicitMinor != null) {
                return new MoneyValue(explicitMinor, currency);
            }
            Object amount = firstMapValue(map, "amount", "value", "price", "min");
            Long minorAmount = hasMinorUnitHint(map)
                    ? wholeNumberAmount(amount)
                    : minorAmount(amount, currency);
            return minorAmount == null ? null : new MoneyValue(minorAmount, currency);
        }
        Long minorAmount = minorAmount(value, fallbackCurrency);
        return minorAmount == null ? null : new MoneyValue(minorAmount, fallbackCurrency);
    }

    private Long minorAmount(Object value, String currency) {
        if (value == null) {
            return null;
        }
        return decimalAmountToMinor(value.toString(), currency);
    }

    private Long wholeNumberAmount(Object value) {
        if (value == null) {
            return null;
        }
        String amount = value.toString().trim();
        if (amount.isBlank()) {
            return null;
        }
        String wholeNumber = amount.replace(",", "").replaceAll("\\s+", "");
        if (!wholeNumber.matches("-?\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(wholeNumber);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasMinorUnitHint(Map<?, ?> map) {
        String unit = firstStringValue(map,
                "unit",
                "units",
                "amountUnit",
                "amount_unit",
                "scale",
                "format");
        if (unit == null) {
            return false;
        }
        String normalized = normalizedValue(unit);
        return normalized.contains("minor")
                || normalized.equals("cent")
                || normalized.equals("cents")
                || normalized.equals("centavo")
                || normalized.equals("centavos");
    }

    private Double ratingValue(Object value) {
        Object ratingValue = value;
        if (value instanceof Map<?, ?> map) {
            ratingValue = firstMapValue(map, "ratingValue", "rating_value", "value", "average", "score", "rating");
        }
        Double rating = decimalValue(ratingValue);
        if (rating == null) {
            return null;
        }
        if (rating > 5.0d && rating <= 10.0d) {
            return rating / 2.0d;
        }
        if (rating > 10.0d && rating <= 100.0d) {
            return rating / 20.0d;
        }
        return rating;
    }

    private Integer reviewCountValue(Object value) {
        Object countValue = value;
        if (value instanceof Map<?, ?> map) {
            countValue = firstMapValue(map, "reviewCount", "review_count", "reviewsCount", "reviews_count",
                    "ratingCount", "rating_count", "count");
        }
        Double count = decimalValue(countValue);
        return count == null ? null : Math.max(0, count.intValue());
    }

    private Double decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String scalar = scalarString(value);
        if (scalar == null) {
            return null;
        }
        String cleaned = scalar.trim().replaceAll("[^0-9,.\\-]", "");
        if (cleaned.isBlank()) {
            return null;
        }
        if (cleaned.contains(".") && cleaned.contains(",")) {
            int lastDot = cleaned.lastIndexOf('.');
            int lastComma = cleaned.lastIndexOf(',');
            cleaned = lastComma > lastDot
                    ? cleaned.replace(".", "").replace(',', '.')
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            int commaIndex = cleaned.lastIndexOf(',');
            int fractionalDigits = cleaned.length() - commaIndex - 1;
            cleaned = fractionalDigits == 3 && commaIndex <= 3
                    ? cleaned.replace(",", "")
                    : cleaned.replace(',', '.');
        } else if (cleaned.contains(".")) {
            int dotIndex = cleaned.lastIndexOf('.');
            int fractionalDigits = cleaned.length() - dotIndex - 1;
            if (fractionalDigits == 3 && dotIndex <= 3) {
                cleaned = cleaned.replace(".", "");
            }
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
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
        String productCurrency = normalizedCurrency(currency);
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
            int lastDot = cleaned.lastIndexOf('.');
            int lastComma = cleaned.lastIndexOf(',');
            cleaned = lastComma > lastDot
                    ? cleaned.replace(".", "").replace(',', '.')
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            int commaIndex = cleaned.lastIndexOf(',');
            cleaned = cleaned.length() - commaIndex == 3
                    ? cleaned.replace(',', '.')
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(".")) {
            int dotIndex = cleaned.lastIndexOf('.');
            if (cleaned.length() - dotIndex == 4) {
                cleaned = cleaned.replace(".", "");
            }
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

    private MoneyValue firstPresent(MoneyValue... values) {
        for (MoneyValue value : values) {
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

    private record MoneyValue(
            Long amount,
            String currency
    ) {
    }

    private record AttributeNode(String name, Object value, int depth) {
    }

    private record ValueNode(Object value, int depth) {
    }

}
