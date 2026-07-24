package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProductAttribution;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
import com.meant.api.module.catalog.service.port.CatalogProductDetailProvider;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import com.meant.api.module.merchant.service.MerchantProductMessageSanitizer;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogContext;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters;
import com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogSelectedOption;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogGetProductRequest;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogLookupRequest;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogProductResult;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogResponse;
import com.meant.api.plugin.support.UcpDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Rehydrates exact Shopify offers through lookup with a raw get_product fallback for capped results. */
@Component
public class ShopifyCatalogProductRehydrationProvider
        implements CatalogProductRehydrationProvider, CatalogProductDetailProvider {
    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute option) -> option.group() == null ? "" : option.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);

    private final ShopifyGlobalCatalogProvider provider;
    private final ShopifyGlobalCatalogProperties catalogProperties;
    private final ShopifyCatalogDataUseProperties dataUseProperties;
    private final ShopifyCatalogReferenceMatcher matcher;
    private final ShopifyGlobalCatalogNormalizer normalizer;
    private final Clock clock;

    @Autowired
    public ShopifyCatalogProductRehydrationProvider(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties catalogProperties,
            ShopifyCatalogDataUseProperties dataUseProperties,
            ShopifyCatalogReferenceMatcher matcher,
            ShopifyGlobalCatalogNormalizer normalizer
    ) {
        this(provider, catalogProperties, dataUseProperties, matcher, normalizer, Clock.systemUTC());
    }

    ShopifyCatalogProductRehydrationProvider(
            ShopifyGlobalCatalogProvider provider,
            ShopifyGlobalCatalogProperties catalogProperties,
            ShopifyCatalogDataUseProperties dataUseProperties,
            ShopifyCatalogReferenceMatcher matcher,
            ShopifyGlobalCatalogNormalizer normalizer,
            Clock clock
    ) {
        this.provider = provider;
        this.catalogProperties = catalogProperties;
        this.dataUseProperties = dataUseProperties;
        this.matcher = matcher;
        this.normalizer = normalizer;
        this.clock = clock;
    }

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return source != null && source.equals(provider.discoverySourceIdentity());
    }

    @Override
    public boolean supportsDetails(DiscoverySourceIdentity source) {
        return supports(source);
    }

    @Override
    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        return getDetails(reference, null, context);
    }

    @Override
    public CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection,
            CatalogRehydrationContext context
    ) {
        if (!validDetailRequest(reference, selection)) {
            return CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.UNAVAILABLE,
                    CatalogRehydrationFailureKind.INVALID_REFERENCE
            );
        }
        try {
            ShopifyGlobalCatalogProductResult productResult = provider.getProductWithDetails(
                    new ShopifyGlobalCatalogGetProductRequest(
                            selection == null
                                    ? detailIdentifier(reference)
                                    : reference.externalProductReference().value(),
                            selectedOptions(reference, selection).stream()
                                    .map(option -> new ShopifyCatalogSelectedOption(option.name(), option.value()))
                                    .toList(),
                            selection == null ? null : selection.preferences(),
                            shopifyDetailContext(context),
                            detailFilters(reference)
                    ));
            CatalogSourceResult sourceResult = productResult.catalogResult();
            if (!sourceResult.successful()) {
                return CatalogProductDetailResult.failed(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                );
            }
            ShopifyCatalogReferenceMatcher.Match match = rawMatch(reference, productResult.product(), selection);
            if (match == null) {
                return CatalogProductDetailResult.failed(
                        reference,
                        CatalogRehydrationStatus.UNAVAILABLE,
                        CatalogRehydrationFailureKind.NOT_FOUND
                );
            }
            CatalogProductRehydrationResult rehydrated = fresh(reference, match);
            RehydratedProductDetails details = details(
                    match.reference(), productResult.product(), productResult.messages(), selection);
            if (details == null) {
                return CatalogProductDetailResult.failed(
                        reference,
                        CatalogRehydrationStatus.UNAVAILABLE,
                        CatalogRehydrationFailureKind.INVALID_RESPONSE
                );
            }
            return CatalogProductDetailResult.from(
                    rehydrated,
                    details
            );
        } catch (RuntimeException exception) {
            return CatalogProductDetailResult.failed(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
        }
    }

    @Override
    public List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    ) {
        Map<CatalogProductReference, CatalogProductRehydrationResult> results = new LinkedHashMap<>();
        List<CatalogProductReference> valid = references.stream()
                .filter(reference -> {
                    if (matcher.validRequest(reference)) {
                        return true;
                    }
                    results.put(reference, failure(reference, CatalogRehydrationStatus.UNAVAILABLE,
                            CatalogRehydrationFailureKind.INVALID_REFERENCE));
                    return false;
                })
                .toList();
        for (int start = 0; start < valid.size(); start += catalogProperties.maximumLookupIds()) {
            List<CatalogProductReference> batch = valid.subList(
                    start,
                    Math.min(start + catalogProperties.maximumLookupIds(), valid.size())
            );
            try {
                hydrateBatch(batch, context, results);
            } catch (RuntimeException exception) {
                batch.forEach(reference -> results.put(reference, failure(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                )));
            }
        }
        return references.stream().map(results::get).toList();
    }

    private void hydrateBatch(
            List<CatalogProductReference> batch,
            CatalogRehydrationContext context,
            Map<CatalogProductReference, CatalogProductRehydrationResult> results
    ) {
        CatalogSourceResult sourceResult = provider.lookupCatalog(new ShopifyGlobalCatalogLookupRequest(
                batch.stream().map(reference -> reference.externalVariantReference().value()).distinct().toList(),
                shopifyContext(context),
                null
        ));
        if (!sourceResult.successful()) {
            batch.forEach(reference -> results.put(reference, failure(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            )));
            return;
        }
        List<ProductCandidate> candidates = sourceResult.candidates() == null ? List.of() : sourceResult.candidates();
        for (CatalogProductReference reference : batch) {
            ShopifyCatalogReferenceMatcher.Match match = matcher.match(reference, candidates);
            results.put(reference, match != null
                    ? fresh(reference, match)
                    : sourceResult.truncated()
                            ? rehydrateExact(reference, context)
                            : failure(
                                    reference,
                                    CatalogRehydrationStatus.UNAVAILABLE,
                                    CatalogRehydrationFailureKind.NOT_FOUND
                            ));
        }
    }

    private CatalogProductRehydrationResult rehydrateExact(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    ) {
        try {
            ShopifyGlobalCatalogProductResult productResult = provider.getProductWithDetails(
                    new ShopifyGlobalCatalogGetProductRequest(
                            detailIdentifier(reference),
                            reference.selectedOptions().stream()
                                    .map(option -> new ShopifyCatalogSelectedOption(option.name(), option.value()))
                                    .toList(),
                            null,
                            shopifyContext(context),
                            detailFilters(reference)
                    ));
            if (!productResult.catalogResult().successful()) {
                return failure(
                        reference,
                        CatalogRehydrationStatus.DEGRADED,
                        CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
                );
            }
            ShopifyCatalogReferenceMatcher.Match match = rawMatch(reference, productResult.product());
            return match == null
                    ? failure(reference, CatalogRehydrationStatus.UNAVAILABLE, CatalogRehydrationFailureKind.NOT_FOUND)
                    : fresh(reference, match);
        } catch (RuntimeException exception) {
            return failure(
                    reference,
                    CatalogRehydrationStatus.DEGRADED,
                    CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE
            );
        }
    }

    private ShopifyCatalogReferenceMatcher.Match rawMatch(
            CatalogProductReference reference,
            ShopifyGlobalCatalogResponse.Product product
    ) {
        return rawMatch(reference, product, null);
    }

    private ShopifyCatalogReferenceMatcher.Match rawMatch(
            CatalogProductReference reference,
            ShopifyGlobalCatalogResponse.Product product,
            CatalogProductDetailSelection selection
    ) {
        if (product == null || !reference.discoverySource().equals(provider.discoverySourceIdentity())) {
            return null;
        }
        List<ProductAttribute> requestedOptions = selection == null
                ? reference.selectedOptions()
                : effectiveSelection(product, selection);
        String anchorVariantId = reference.externalVariantReference() == null
                ? null
                : reference.externalVariantReference().value();
        List<ShopifyGlobalCatalogResponse.Variant> matches = safe(product.variants()).stream()
                .filter(Objects::nonNull)
                .filter(variant -> rawMerchantMatches(reference, product, variant))
                .filter(variant -> selection != null
                        || Objects.equals(anchorVariantId, variant.id()))
                .filter(variant -> selection == null
                        ? rawOptionsMatch(requestedOptions, rawSelectedOptions(product, variant))
                        : rawOptionsContain(requestedOptions, rawSelectedOptions(product, variant)))
                .filter(variant -> rawConfigurationMatches(reference, variant))
                .toList();
        if (matches.isEmpty() || selection == null && matches.size() != 1) {
            return null;
        }
        ShopifyGlobalCatalogResponse.Variant variant = selection == null
                ? matches.getFirst()
                : matches.stream()
                        .sorted(Comparator
                                .comparing((ShopifyGlobalCatalogResponse.Variant candidate) ->
                                        anchorVariantId == null || !anchorVariantId.equals(candidate.id()))
                                .thenComparing(candidate -> candidate.availability() == null
                                        || !Boolean.TRUE.equals(candidate.availability().available()))
                                .thenComparing(
                                        ShopifyGlobalCatalogResponse.Variant::id,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                        .findFirst()
                        .orElseThrow();
        CatalogProductReference resolved = new CatalogProductReference(
                reference.interactionKey(),
                reference.discoverySource(),
                null,
                null,
                shopifyIdentifier(ExternalIdentifierType.MERCHANT, variant.seller().id()),
                normalizedDomain(variant.seller().domain()),
                shopifyIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        firstText(variant.productId(), product.id())
                ),
                shopifyIdentifier(ExternalIdentifierType.VARIANT, variant.id()),
                rawSelectedAttributes(rawSelectedOptions(product, variant)),
                rawComponents(variant.components()),
                rawSellingPlan(variant.sellingPlan())
        );
        return new ShopifyCatalogReferenceMatcher.Match(
                resolved,
                normalizer.normalizeExact(product, variant)
        );
    }

    private List<ProductAttribute> selectedOptions(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection
    ) {
        return selection == null ? reference.selectedOptions() : selection.selectedOptions();
    }

    private boolean validDetailRequest(
            CatalogProductReference reference,
            CatalogProductDetailSelection selection
    ) {
        return selection == null
                ? matcher.validRequest(reference)
                : reference.localMerchantId() == null
                        && reference.localRouting() == null
                        && reference.externalMerchantReference() != null;
    }

    private ShopifyCatalogFilters detailFilters(CatalogProductReference reference) {
        return new ShopifyCatalogFilters(
                false,
                null,
                null,
                null,
                null,
                List.of(reference.externalMerchantReference().value()),
                null,
                null,
                null,
                null
        );
    }

    private String detailIdentifier(CatalogProductReference reference) {
        return reference.externalVariantReference() == null
                ? reference.externalProductReference().value()
                : reference.externalVariantReference().value();
    }

    private CatalogProductRehydrationResult fresh(
            CatalogProductReference requested,
            ShopifyCatalogReferenceMatcher.Match match
    ) {
        Instant observedAt = clock.instant();
        ResultFreshness freshness = new ResultFreshness(
                observedAt,
                observedAt.plus(dataUseProperties.rehydratedFactsTtl())
        );
        ProductCandidate candidate = match.candidate();
        boolean knownAvailability = candidate.offer().availability().status() != OfferAvailabilityStatus.UNKNOWN;
        return CatalogProductRehydrationResult.fresh(requested, match.reference(), new RehydratedCommercialFacts(
                candidate.title(),
                candidate.offer().merchantName(),
                candidate.attribution().stream()
                        .filter(attribution -> "Product".equalsIgnoreCase(attribution.label()))
                        .map(ProductAttribution::url)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null),
                candidate.offer().price(),
                candidate.offer().availability(),
                candidate.offer().identity().externalVariantIdentity(),
                candidate.offer().selectedOptions(),
                candidate.offer().delivery(),
                candidate.media(),
                freshness,
                new CommercialFactsFreshness(
                        candidate.offer().price() == null ? null : freshness,
                        knownAvailability ? freshness : null,
                        candidate.offer().identity().externalVariantIdentity() == null ? null : freshness,
                        candidate.offer().selectedOptions().isEmpty() ? null : freshness,
                        candidate.offer().delivery().isEmpty() ? null : freshness
                )
        ));
    }

    private CatalogProductRehydrationResult failure(
            CatalogProductReference reference,
            CatalogRehydrationStatus status,
            CatalogRehydrationFailureKind failure
    ) {
        return CatalogProductRehydrationResult.failed(reference, status, failure);
    }

    private ShopifyCatalogContext shopifyContext(CatalogRehydrationContext context) {
        return new ShopifyCatalogContext(
                context == null ? null : context.country(),
                null,
                null,
                context == null ? null : context.language(),
                null,
                "Rehydrate selected product"
        );
    }

    private ShopifyCatalogContext shopifyDetailContext(CatalogRehydrationContext context) {
        return new ShopifyCatalogContext(
                context == null ? null : context.country(),
                null,
                null,
                context == null ? null : context.language(),
                null,
                "Open saved product detail"
        );
    }

    private RehydratedProductDetails details(
            CatalogProductReference reference,
            ShopifyGlobalCatalogResponse.Product product,
            List<ShopifyGlobalCatalogResponse.Message> messages,
            CatalogProductDetailSelection selection
    ) {
        if (product == null) {
            return null;
        }
        List<ShopifyGlobalCatalogResponse.Variant> merchantVariants = safe(product.variants()).stream()
                .filter(Objects::nonNull)
                .filter(variant -> rawMerchantMatches(reference, product, variant))
                .toList();
        List<ShopifyGlobalCatalogResponse.Variant> selectedMatches = merchantVariants.stream()
                .filter(variant -> reference.externalVariantReference().value().equals(variant.id()))
                .filter(variant -> rawOptionsMatch(
                        reference.selectedOptions(), rawSelectedOptions(product, variant)))
                .filter(variant -> rawConfigurationMatches(reference, variant))
                .toList();
        if (selectedMatches.size() != 1) {
            return null;
        }
        ShopifyGlobalCatalogResponse.Variant selected = selectedMatches.getFirst();
        String providerEndpoint = catalogProperties.endpoint() == null
                ? null
                : catalogProperties.endpoint().toString();
        MerchantProductMessageSanitizer.TransportContext messageContext =
                MerchantProductMessageSanitizer.context(
                        null,
                        providerEndpoint,
                        providerEndpoint,
                        reference.externalMerchantDomain(),
                        selected.seller() == null ? null : selected.seller().domain(),
                        selected.seller() == null ? null : selected.seller().url()
                );
        List<RehydratedProductDetails.Variant> detailVariants = merchantVariants.stream()
                .map(variant -> detailVariant(product, variant))
                .toList();
        RehydratedProductDetails.PriceRange priceRange = detailPriceRange(product.priceRange());
        RehydratedProductDetails.PriceRange listPriceRange = detailPriceRange(product.listPriceRange());
        return new RehydratedProductDetails(
                reference.externalProductReference().value(),
                product.handle(),
                product.title(),
                product.description() == null ? null : product.description().preferredText(),
                product.url(),
                imageUrl(product.media()),
                safe(product.media()).stream()
                        .filter(Objects::nonNull)
                        .filter(item -> "image".equalsIgnoreCase(item.type()))
                        .map(item -> new RehydratedProductDetails.Image(
                                item.url(), item.altText()))
                        .toList(),
                detailMedia(product.media()),
                detailCategories(product.categories()),
                distinctStrings(product.tags()),
                safe(product.options()).stream()
                        .filter(Objects::nonNull)
                        .map(option -> new RehydratedProductDetails.Option(
                                option.name(),
                                safe(option.values()).stream()
                                        .filter(Objects::nonNull)
                                        .map(ShopifyGlobalCatalogResponse.OptionValue::label)
                                        .filter(this::hasText)
                                        .distinct()
                                        .toList(),
                                safe(option.values()).stream()
                                        .filter(Objects::nonNull)
                                        .filter(value -> hasText(value.label()))
                                        .map(value -> new RehydratedProductDetails.OptionValue(
                                                value.label(), value.available(), value.exists()))
                                        .toList()))
                        .toList(),
                effectiveSelectedOptions(product, selected, selection).stream()
                        .map(option -> new RehydratedProductDetails.SelectedOption(
                                option.name(), option.label()))
                        .toList(),
                detailVariants,
                product.totalVariants(),
                priceRange,
                listPriceRange,
                requiresSellingPlan(selected),
                detailVariant(product, selected),
                merchantVariants.stream()
                        .map(ShopifyGlobalCatalogResponse.Variant::sku)
                        .filter(this::hasText)
                        .distinct()
                        .toList(),
                List.of(),
                List.of(),
                List.of(),
                productAttributes(product),
                safe(messages).stream()
                        .filter(Objects::nonNull)
                        .map(message -> detailMessage(message, messageContext))
                        .toList(),
                ratingScore(product.rating()),
                ratingScaleMax(product.rating()),
                reviewCount(product.rating()),
                selected.seller() == null ? null : selected.seller().name(),
                null,
                java.util.stream.Stream.of(
                                providerEndpoint,
                                reference.externalMerchantDomain(),
                                selected.seller() == null ? null : selected.seller().domain(),
                                selected.seller() == null ? null : selected.seller().url()
                        )
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList()
        );
    }

    private RehydratedProductDetails.Variant detailVariant(
            ShopifyGlobalCatalogResponse.Product product,
            ShopifyGlobalCatalogResponse.Variant variant
    ) {
        List<ShopifyGlobalCatalogResponse.SelectedOption> selectedOptions = rawSelectedOptions(product, variant);
        List<RehydratedProductDetails.Media> media = detailMedia(variant.media());
        return new RehydratedProductDetails.Variant(
                variant.id(),
                variant.handle(),
                variant.title(),
                variant.description() == null ? null : variant.description().preferredText(),
                variant.url(),
                moneyText(variant.price()),
                variant.price() == null ? null : variant.price().currency(),
                moneyText(variant.listPrice()),
                variant.listPrice() == null ? null : variant.listPrice().currency(),
                variant.sku(),
                imageUrl(variant.media()),
                safe(variant.media()).stream()
                        .filter(Objects::nonNull)
                        .filter(item -> "image".equalsIgnoreCase(item.type()))
                        .map(ShopifyGlobalCatalogResponse.Media::altText)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null),
                media,
                availability(variant.availability()),
                selectedOptions.stream()
                        .map(option -> new RehydratedProductDetails.SelectedOption(
                                option.name(), option.label()))
                        .toList(),
                detailCategories(variant.categories()),
                distinctStrings(variant.tags()),
                variantAttributes(variant)
        );
    }

    private boolean rawMerchantMatches(
            CatalogProductReference reference,
            ShopifyGlobalCatalogResponse.Product product,
            ShopifyGlobalCatalogResponse.Variant variant
    ) {
        return variant.seller() != null
                && reference.externalMerchantReference().value().equals(variant.seller().id())
                && (reference.externalMerchantDomain() == null
                        || Objects.equals(reference.externalMerchantDomain(), normalizedDomain(variant.seller().domain())))
                && reference.externalProductReference().value().equals(firstText(variant.productId(), product.id()));
    }

    private boolean rawOptionsMatch(
            List<ProductAttribute> requested,
            List<ShopifyGlobalCatalogResponse.SelectedOption> observed
    ) {
        return safe(requested).isEmpty() || optionKeys(requested).equals(rawOptionKeys(observed));
    }

    private boolean rawOptionsContain(
            List<ProductAttribute> requested,
            List<ShopifyGlobalCatalogResponse.SelectedOption> observed
    ) {
        return rawOptionKeys(observed).containsAll(optionKeys(requested));
    }

    private boolean rawConfigurationMatches(
            CatalogProductReference reference,
            ShopifyGlobalCatalogResponse.Variant variant
    ) {
        return reference.components().equals(rawComponents(variant.components()))
                && Objects.equals(reference.sellingPlanIdentity(), rawSellingPlan(variant.sellingPlan()));
    }

    private List<OfferComponentIdentity> rawComponents(
            List<ShopifyGlobalCatalogResponse.Component> components
    ) {
        return safe(components).stream()
                .filter(component -> component != null && hasText(component.productId()))
                .map(component -> new OfferComponentIdentity(
                        shopifyIdentifier(ExternalIdentifierType.PRODUCT, component.productId()),
                        hasText(component.variantId())
                                ? shopifyIdentifier(ExternalIdentifierType.VARIANT, component.variantId())
                                : null,
                        component.quantity() == null ? 1 : component.quantity(),
                        rawSelectedAttributes(component.options())
                ))
                .sorted(ShopifyCatalogProductRehydrationProvider::compareComponents)
                .toList();
    }

    private static int compareComponents(OfferComponentIdentity first, OfferComponentIdentity second) {
        int comparison = compareIdentifiers(first.externalProductIdentity(), second.externalProductIdentity());
        if (comparison != 0) {
            return comparison;
        }
        comparison = compareIdentifiers(first.externalVariantIdentity(), second.externalVariantIdentity());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(first.quantity(), second.quantity());
        if (comparison != 0) {
            return comparison;
        }
        return compareOptions(first.selectedOptions(), second.selectedOptions());
    }

    private static int compareIdentifiers(ExternalIdentifier first, ExternalIdentifier second) {
        if (first == second) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }
        int comparison = first.type().compareTo(second.type());
        if (comparison != 0) {
            return comparison;
        }
        comparison = nullToEmpty(first.namespace()).compareTo(nullToEmpty(second.namespace()));
        return comparison != 0 ? comparison : first.value().compareTo(second.value());
    }

    private static int compareOptions(List<ProductAttribute> first, List<ProductAttribute> second) {
        int sharedSize = Math.min(first.size(), second.size());
        for (int index = 0; index < sharedSize; index++) {
            int comparison = OPTION_ORDER.compare(first.get(index), second.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(first.size(), second.size());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private SellingPlanIdentity rawSellingPlan(ShopifyGlobalCatalogResponse.SellingPlan plan) {
        if (plan == null || !hasText(plan.id()) && !hasText(plan.groupId())) {
            return null;
        }
        return new SellingPlanIdentity(
                hasText(plan.groupId())
                        ? shopifyIdentifier(ExternalIdentifierType.SELLING_PLAN_GROUP, plan.groupId())
                        : null,
                hasText(plan.id())
                        ? shopifyIdentifier(ExternalIdentifierType.SELLING_PLAN, plan.id())
                        : null,
                safe(plan.options()).stream()
                        .filter(option -> option != null && hasText(option.name()) && hasText(option.value()))
                        .map(option -> new SellingPlanOption(option.name(), option.value()))
                        .toList()
        );
    }

    private List<ProductAttribute> rawSelectedAttributes(
            List<ShopifyGlobalCatalogResponse.SelectedOption> options
    ) {
        return safe(options).stream()
                .filter(option -> option != null && hasText(option.name()) && hasText(option.label()))
                .map(option -> new ProductAttribute("variant-option", option.name(), option.label()))
                .toList();
    }

    private ExternalIdentifier shopifyIdentifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, "SHOPIFY", value);
    }

    private List<ShopifyGlobalCatalogResponse.SelectedOption> rawSelectedOptions(
            ShopifyGlobalCatalogResponse.Product product,
            ShopifyGlobalCatalogResponse.Variant variant
    ) {
        List<ShopifyGlobalCatalogResponse.SelectedOption> options = safe(variant.options());
        return options.isEmpty() ? safe(product.selected()) : options;
    }

    private List<ProductAttribute> effectiveSelection(
            ShopifyGlobalCatalogResponse.Product product,
            CatalogProductDetailSelection selection
    ) {
        List<ProductAttribute> responseSelection = rawSelectedAttributes(safe(product.selected()));
        return responseSelection.isEmpty() ? selection.selectedOptions() : responseSelection;
    }

    private List<ShopifyGlobalCatalogResponse.SelectedOption> effectiveSelectedOptions(
            ShopifyGlobalCatalogResponse.Product product,
            ShopifyGlobalCatalogResponse.Variant selectedVariant,
            CatalogProductDetailSelection selection
    ) {
        List<ShopifyGlobalCatalogResponse.SelectedOption> selected = safe(product.selected());
        if (!selected.isEmpty()) {
            return selected;
        }
        if (selection != null) {
            return selection.selectedOptions().stream()
                    .map(option -> new ShopifyGlobalCatalogResponse.SelectedOption(option.name(), option.value()))
                    .toList();
        }
        return safe(selectedVariant.options());
    }

    private List<String> optionKeys(List<ProductAttribute> options) {
        return safe(options).stream()
                .filter(Objects::nonNull)
                .filter(option -> hasText(option.name()) && hasText(option.value()))
                .map(option -> option.name() + "\u0000" + option.value())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> rawOptionKeys(List<ShopifyGlobalCatalogResponse.SelectedOption> options) {
        return safe(options).stream()
                .filter(Objects::nonNull)
                .filter(option -> hasText(option.name()) && hasText(option.label()))
                .map(option -> option.name() + "\u0000" + option.label())
                .distinct()
                .sorted()
                .toList();
    }

    private RehydratedProductDetails.PriceRange detailPriceRange(
            ShopifyGlobalCatalogResponse.PriceRange range
    ) {
        if (range == null || range.min() == null && range.max() == null) {
            return null;
        }
        String currency = range.min() != null && hasText(range.min().currency())
                ? range.min().currency()
                : range.max().currency();
        return new RehydratedProductDetails.PriceRange(
                moneyText(range.min()),
                moneyText(range.max()),
                currency
        );
    }

    private List<RehydratedProductDetails.Media> detailMedia(
            List<ShopifyGlobalCatalogResponse.Media> media
    ) {
        return safe(media).stream()
                .filter(Objects::nonNull)
                .map(item -> new RehydratedProductDetails.Media(
                        item.type(),
                        item.url(),
                        item.altText(),
                        null
                ))
                .toList();
    }

    private List<RehydratedProductDetails.Category> detailCategories(
            List<ShopifyGlobalCatalogResponse.Category> categories
    ) {
        return safe(categories).stream()
                .filter(Objects::nonNull)
                .map(category -> new RehydratedProductDetails.Category(
                        category.value(), category.taxonomy()))
                .toList();
    }

    private List<RehydratedProductDetails.Attribute> productAttributes(
            ShopifyGlobalCatalogResponse.Product product
    ) {
        List<RehydratedProductDetails.Attribute> attributes = new ArrayList<>();
        ShopifyGlobalCatalogResponse.Metadata metadata = product.metadata();
        if (metadata != null) {
            addAttributes(attributes, "Technical specification", metadata.techSpecs());
            addAttributes(attributes, "Top feature", metadata.topFeatures());
            addAttributes(attributes, "Unique selling point", metadata.uniqueSellingPoints());
        }
        return List.copyOf(attributes);
    }

    private List<RehydratedProductDetails.Attribute> variantAttributes(
            ShopifyGlobalCatalogResponse.Variant variant
    ) {
        List<RehydratedProductDetails.Attribute> attributes = new ArrayList<>();
        safe(variant.barcodes()).stream()
                .filter(Objects::nonNull)
                .forEach(barcode -> addAttribute(attributes,
                        firstText(barcode.type(), "Barcode"), barcode.value()));
        safe(variant.inputs()).stream()
                .filter(Objects::nonNull)
                .forEach(input -> addAttribute(attributes,
                        "Input " + firstText(input.id(), "match"), input.match()));
        if (variant.requires() != null) {
            addAttribute(attributes, "Requires shipping", text(variant.requires().shipping()));
            addAttribute(attributes, "Requires selling plan", text(variant.requires().sellingPlan()));
            addAttribute(attributes, "Requires components", text(variant.requires().components()));
        }
        return List.copyOf(attributes);
    }

    private void addAttributes(
            List<RehydratedProductDetails.Attribute> attributes,
            String name,
            List<String> values
    ) {
        safe(values).stream().filter(this::hasText).forEach(value -> addAttribute(attributes, name, value));
    }

    private void addAttribute(
            List<RehydratedProductDetails.Attribute> attributes,
            String name,
            String value
    ) {
        if (hasText(name) && hasText(value)) {
            attributes.add(new RehydratedProductDetails.Attribute(name.trim(), value.trim()));
        }
    }

    private RehydratedProductDetails.Message detailMessage(
            ShopifyGlobalCatalogResponse.Message message,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        MerchantProductMessageSanitizer.SanitizedMessage sanitized =
                MerchantProductMessageSanitizer.sanitize(
                        message.type(),
                        message.code(),
                        message.path(),
                        message.contentType(),
                        message.content(),
                        message.severity(),
                        message.presentation(),
                        message.imageUrl(),
                        message.url(),
                        context
                );
        return new RehydratedProductDetails.Message(
                sanitized.type(),
                sanitized.code(),
                sanitized.path(),
                sanitized.contentType(),
                sanitized.content(),
                sanitized.severity(),
                sanitized.presentation(),
                sanitized.imageUrl(),
                sanitized.url()
        );
    }

    private String imageUrl(List<ShopifyGlobalCatalogResponse.Media> media) {
        return safe(media).stream()
                .filter(Objects::nonNull)
                .filter(item -> "image".equalsIgnoreCase(item.type()))
                .map(ShopifyGlobalCatalogResponse.Media::url)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String moneyText(ShopifyGlobalCatalogResponse.Price money) {
        return money == null || money.amount() == null
                ? null
                : UcpDecimal.minorAmountToDecimalText(money.amount(), money.currency());
    }

    private Boolean availability(ShopifyGlobalCatalogResponse.Availability availability) {
        if (availability == null) {
            return null;
        }
        if (availability.available() != null) {
            return availability.available();
        }
        return switch (availability.status() == null ? "" : availability.status().trim().toLowerCase(Locale.ROOT)) {
            case "in_stock", "available", "preorder", "pre_order", "backorder", "back_order" -> true;
            case "out_of_stock", "unavailable", "discontinued" -> false;
            default -> null;
        };
    }

    private Boolean requiresSellingPlan(ShopifyGlobalCatalogResponse.Variant variant) {
        if (variant.requires() != null && variant.requires().sellingPlan() != null) {
            return variant.requires().sellingPlan();
        }
        return variant.sellingPlan() == null ? null : true;
    }

    private Double ratingScore(ShopifyGlobalCatalogResponse.Rating rating) {
        return rating == null || rating.value() == null ? null : rating.value().doubleValue();
    }

    private Double ratingScaleMax(ShopifyGlobalCatalogResponse.Rating rating) {
        return rating == null || rating.scaleMax() == null ? null : rating.scaleMax().doubleValue();
    }

    private Long reviewCount(ShopifyGlobalCatalogResponse.Rating rating) {
        return rating == null ? null : rating.count();
    }

    private List<String> distinctStrings(List<String> values) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        safe(values).stream().filter(this::hasText).map(String::trim).forEach(distinct::add);
        return List.copyOf(distinct);
    }

    private String normalizedDomain(String value) {
        if (!hasText(value)) {
            return null;
        }
        String candidate = value.trim();
        try {
            URI uri = candidate.contains("://") ? URI.create(candidate) : URI.create("https://" + candidate);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                    || uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }
            return java.net.IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String text(Boolean value) {
        return value == null ? null : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
