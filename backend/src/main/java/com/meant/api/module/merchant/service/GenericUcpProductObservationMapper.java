package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.GenericUcpVariantObservationResolver.VariantObservation;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Selects the exact requested UCP variant/options and maps its current typed facts. */
@Component
public class GenericUcpProductObservationMapper {
    private static final int MAX_METADATA_DEPTH = 32;
    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute option) -> option.group() == null ? "" : option.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);
    private final GenericUcpCatalogReferenceVerifier referenceVerifier;
    private final GenericUcpVariantObservationResolver variantResolver;
    private final GenericUcpCatalogDataUseProperties properties;
    private final Clock clock;

    @Autowired
    public GenericUcpProductObservationMapper(
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpVariantObservationResolver variantResolver,
            GenericUcpCatalogDataUseProperties properties
    ) {
        this(referenceVerifier, variantResolver, properties, Clock.systemUTC());
    }

    GenericUcpProductObservationMapper(
            GenericUcpCatalogReferenceVerifier referenceVerifier,
            GenericUcpVariantObservationResolver variantResolver,
            GenericUcpCatalogDataUseProperties properties,
            Clock clock
    ) {
        this.referenceVerifier = referenceVerifier;
        this.variantResolver = variantResolver;
        this.properties = properties;
        this.clock = clock;
    }

    public CatalogProductRehydrationResult map(
            CatalogProductReference reference,
            MerchantIntegrationResult integration,
            ProductDetailsResult details
    ) {
        // Generic get-product does not expose typed component or selling-plan identities to verify.
        if (!reference.components().isEmpty() || reference.sellingPlanIdentity() != null) {
            return failed(reference, CatalogRehydrationFailureKind.INVALID_REFERENCE);
        }
        ProductDetailsResponse.Product product = details == null ? null : details.product();
        if (product == null || !reference.externalProductReference().value().equals(product.productId())) {
            return failed(reference, CatalogRehydrationFailureKind.NOT_FOUND);
        }
        if (reference.externalVariantReference() == null) {
            return failed(reference, CatalogRehydrationFailureKind.INVALID_REFERENCE);
        }
        if (requiresSellingPlan(product)) {
            return failed(reference, CatalogRehydrationFailureKind.INVALID_REFERENCE);
        }
        GenericUcpVariantObservationResolver.Resolution resolution = variantResolver.resolve(reference, product);
        if (resolution.failure() != null) {
            return failed(reference, resolution.failure());
        }
        VariantObservation variant = resolution.observation();
        Instant observedAt = clock.instant();
        ResultFreshness freshness = new ResultFreshness(observedAt, observedAt.plus(properties.rehydratedFactsTtl()));
        String provider = reference.discoverySource().provider().value();
        ExternalIdentifier productId = identifier(provider, ExternalIdentifierType.PRODUCT, product.productId());
        ExternalIdentifier variantId = identifier(provider, ExternalIdentifierType.VARIANT, variant.id());
        CatalogProductReference resolved = referenceVerifier.canonical(
                reference, integration, productId, variantId, variant.options());
        Money price = money(variant.price(), variant.currency());
        return CatalogProductRehydrationResult.fresh(reference, resolved, new RehydratedCommercialFacts(
                product.title(),
                integration.merchantName(),
                price,
                availability(variant.available()),
                variantId,
                variant.options(),
                List.of(),
                media(product, variant),
                freshness,
                new CommercialFactsFreshness(
                        price == null ? null : freshness,
                        variant.available() == null ? null : freshness,
                        freshness,
                        variant.options().isEmpty() ? null : freshness,
                        null
                )
        ));
    }

    /** Maps the same verified get-product response into a full, transient presentation projection. */
    public RehydratedProductDetails details(
            ProductDetailsResult details,
            CatalogProductReference resolvedReference,
            String merchantName
    ) {
        ProductDetailsResponse.Product product = details == null ? null : details.product();
        if (product == null || resolvedReference == null) {
            return null;
        }
        ProductDetailsResponse.PriceRange priceRange = product.priceRange();
        ProductDetailsResponse.PriceRange listPriceRange = product.listPriceRange();
        UcpMoney productListPrice = product.listPrice() == null
                ? null
                : UcpMoney.value(product.listPrice(), priceRange == null ? null : priceRange.currency());
        RehydratedProductDetails.Variant selectedVariant = selectedVariant(product, resolvedReference);
        return new RehydratedProductDetails(
                product.productId(),
                product.handle(),
                product.title(),
                product.description(),
                product.url(),
                product.imageUrl(),
                safe(product.images()).stream()
                        .filter(Objects::nonNull)
                        .map(image -> new RehydratedProductDetails.Image(image.url(), image.altText()))
                        .toList(),
                safe(product.media()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailMedia)
                        .toList(),
                safe(product.categories()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailCategory)
                        .toList(),
                distinctStrings(product.tags()),
                safe(product.options()).stream()
                        .filter(Objects::nonNull)
                        .map(option -> new RehydratedProductDetails.Option(
                                option.name(), distinctStrings(option.values())))
                        .toList(),
                safe(product.variants()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailVariant)
                        .toList(),
                product.totalVariants(),
                detailPriceRange(priceRange),
                listPriceRange == null
                        ? productListPrice == null
                                ? null
                                : new RehydratedProductDetails.PriceRange(
                                        moneyText(productListPrice),
                                        moneyText(productListPrice),
                                        productListPrice.currency())
                        : detailPriceRange(listPriceRange),
                product.requiresSellingPlan(),
                selectedVariant,
                stringValues(product.skus()),
                stringValues(product.certifications()),
                stringValues(product.materials()),
                stringValues(product.collections()),
                attributes(product.metadata(), product.metafields(), product.techSpecs()),
                safe(details.messages()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailMessage)
                        .toList(),
                UcpDecimal.ratingValue(product.rating()),
                UcpDecimal.ratingValue(product.rating()) == null ? null : 5.0d,
                reviewCount(product.reviewCount()),
                merchantName
        );
    }

    private Long reviewCount(Object value) {
        Integer count = UcpDecimal.reviewCountValue(value);
        return count == null ? null : count.longValue();
    }

    private boolean requiresSellingPlan(ProductDetailsResponse.Product product) {
        return Boolean.TRUE.equals(product.requiresSellingPlan())
                || product.sellingPlanGroups() != null && !product.sellingPlanGroups().isEmpty();
    }

    private RehydratedProductDetails.Variant selectedVariant(
            ProductDetailsResponse.Product product,
            CatalogProductReference reference
    ) {
        String selectedId = reference.externalVariantReference() == null
                ? null
                : reference.externalVariantReference().value();
        ProductDetailsResponse.SelectedVariant selected = product.selectedOrFirstAvailableVariant();
        if (selected != null && Objects.equals(selectedId, selected.variantId())) {
            return detailVariant(selected);
        }
        return safe(product.variants()).stream()
                .filter(Objects::nonNull)
                .filter(variant -> Objects.equals(selectedId, variant.variantId()))
                .filter(variant -> selectedOptions(variant.selectedOptions()).equals(reference.selectedOptions()))
                .findFirst()
                .map(this::detailVariant)
                .orElse(null);
    }

    private RehydratedProductDetails.Variant detailVariant(ProductDetailsResponse.SelectedVariant variant) {
        UcpMoney listPrice = variant.listPrice() == null
                ? null
                : UcpMoney.value(variant.listPrice(), variant.currency());
        return new RehydratedProductDetails.Variant(
                variant.variantId(),
                null,
                variant.title(),
                null,
                null,
                variant.price(),
                variant.currency(),
                moneyText(listPrice),
                listPrice == null ? null : listPrice.currency(),
                variant.sku(),
                variant.imageUrl(),
                variant.imageAltText(),
                safe(variant.media()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailMedia)
                        .toList(),
                variant.available(),
                detailSelectedOptions(variant.selectedOptions()),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private RehydratedProductDetails.Variant detailVariant(ProductDetailsResponse.Variant variant) {
        UcpMoney listPrice = variant.listPrice() == null
                ? null
                : UcpMoney.value(variant.listPrice(), variant.currency());
        return new RehydratedProductDetails.Variant(
                variant.variantId(),
                variant.handle(),
                variant.title(),
                variant.description(),
                variant.url(),
                variant.price(),
                variant.currency(),
                moneyText(listPrice),
                listPrice == null ? null : listPrice.currency(),
                variant.sku(),
                variant.imageUrl(),
                variant.imageAltText(),
                safe(variant.media()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailMedia)
                        .toList(),
                variant.available(),
                detailSelectedOptions(variant.selectedOptions()),
                safe(variant.categories()).stream()
                        .filter(Objects::nonNull)
                        .map(this::detailCategory)
                        .toList(),
                distinctStrings(variant.tags()),
                attributes(variant.metadata())
        );
    }

    private List<ProductAttribute> selectedOptions(
            List<ProductDetailsResponse.SelectedOption> options
    ) {
        return safe(options).stream()
                .filter(Objects::nonNull)
                .filter(option -> option.name() != null && !option.name().isBlank()
                        && option.value() != null && !option.value().isBlank())
                .map(option -> new ProductAttribute("variant-option", option.name(), option.value()))
                .distinct()
                .sorted(OPTION_ORDER)
                .toList();
    }

    private List<RehydratedProductDetails.SelectedOption> detailSelectedOptions(
            List<ProductDetailsResponse.SelectedOption> options
    ) {
        return safe(options).stream()
                .filter(Objects::nonNull)
                .map(option -> new RehydratedProductDetails.SelectedOption(option.name(), option.value()))
                .toList();
    }

    private RehydratedProductDetails.Media detailMedia(ProductDetailsResponse.Media media) {
        return new RehydratedProductDetails.Media(
                media.type(), media.url(), media.altText(), media.previewImageUrl());
    }

    private RehydratedProductDetails.Category detailCategory(ProductDetailsResponse.Category category) {
        return new RehydratedProductDetails.Category(category.value(), category.taxonomy());
    }

    private RehydratedProductDetails.PriceRange detailPriceRange(ProductDetailsResponse.PriceRange range) {
        return range == null ? null : new RehydratedProductDetails.PriceRange(
                range.min(), range.max(), range.currency());
    }

    private RehydratedProductDetails.Message detailMessage(ProductDetailsResponse.Message message) {
        return new RehydratedProductDetails.Message(
                message.type(),
                message.code(),
                message.path(),
                message.contentType(),
                message.content(),
                message.severity(),
                message.presentation(),
                message.imageUrl(),
                message.url()
        );
    }

    private String moneyText(UcpMoney money) {
        return money == null ? null : UcpDecimal.minorAmountToDecimalText(money.amount(), money.currency());
    }

    private List<String> distinctStrings(List<String> values) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (String value : safe(values)) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                seen.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return List.copyOf(seen.values());
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
                Object named = firstMapValue(map, "values", "value", "name", "label", "title", "text");
                if (named != null) {
                    stack.add(new ValueNode(named, node.depth() + 1));
                    continue;
                }
                List<?> items = new ArrayList<>(map.values());
                for (int index = items.size() - 1; index >= 0; index--) {
                    stack.add(new ValueNode(items.get(index), node.depth() + 1));
                }
                continue;
            }
            String scalar = scalarString(node.value());
            if (scalar != null) {
                values.add(scalar);
            }
        }
        return distinctStrings(values);
    }

    private List<RehydratedProductDetails.Attribute> attributes(Object... values) {
        Map<String, RehydratedProductDetails.Attribute> attributes = new LinkedHashMap<>();
        for (Object value : values) {
            collectAttributes(attributes, "metadata", value, 0);
        }
        return List.copyOf(attributes.values());
    }

    private void collectAttributes(
            Map<String, RehydratedProductDetails.Attribute> attributes,
            String name,
            Object value,
            int depth
    ) {
        if (depth > MAX_METADATA_DEPTH || value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object namedValue = firstMapValue(map, "value", "values", "text", "description");
            Object keyField = firstMapValue(map, "name", "key", "label", "title");
            if (namedValue != null && keyField != null) {
                addAttribute(attributes, firstPresent(scalarString(keyField), name),
                        String.join(", ", stringValues(namedValue)));
                return;
            }
            map.forEach((key, nested) -> {
                String nestedName = scalarString(key);
                if (nestedName != null) {
                    collectAttributes(attributes,
                            "metadata".equals(name) ? nestedName : name + " " + nestedName,
                            nested,
                            depth + 1);
                }
            });
            return;
        }
        if (value instanceof Collection<?> collection) {
            addAttribute(attributes, name, String.join(", ", stringValues(collection)));
            return;
        }
        addAttribute(attributes, name, scalarString(value));
    }

    private void addAttribute(
            Map<String, RehydratedProductDetails.Attribute> attributes,
            String name,
            String value
    ) {
        String normalizedName = blankToNull(name);
        String normalizedValue = blankToNull(value);
        if (normalizedName != null && normalizedValue != null) {
            attributes.putIfAbsent(
                    normalizedName.toLowerCase(Locale.ROOT) + "|" + normalizedValue.toLowerCase(Locale.ROOT),
                    new RehydratedProductDetails.Attribute(normalizedName, normalizedValue));
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

    private String scalarString(Object value) {
        if (value instanceof String string) {
            return blankToNull(string);
        }
        return value instanceof Number || value instanceof Boolean || value instanceof Character
                ? blankToNull(value.toString())
                : null;
    }

    private String firstPresent(String first, String second) {
        return first == null ? second : first;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ValueNode(Object value, int depth) {
    }

    private ExternalIdentifier identifier(String provider, ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, provider, value);
    }

    private Money money(String price, String currency) {
        Long amount = UcpMoney.minorAmount(price, currency);
        return amount == null || currency == null ? null : new Money(amount, currency);
    }

    private OfferAvailability availability(Boolean available) {
        return new OfferAvailability(available == null ? OfferAvailabilityStatus.UNKNOWN
                : available ? OfferAvailabilityStatus.IN_STOCK : OfferAvailabilityStatus.OUT_OF_STOCK, null, null);
    }

    private List<ProductMedia> media(ProductDetailsResponse.Product product, VariantObservation variant) {
        return java.util.stream.Stream.concat(
                        product.images() == null ? java.util.stream.Stream.empty()
                                : product.images().stream().map(ProductDetailsResponse.Image::url),
                        java.util.stream.Stream.of(variant.imageUrl(), product.imageUrl()))
                .map(this::httpsUri)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(uri -> new ProductMedia(ProductMediaType.IMAGE, uri, null, null, null))
                .toList();
    }

    private URI httpsUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private CatalogProductRehydrationResult failed(
            CatalogProductReference reference,
            CatalogRehydrationFailureKind failure
    ) {
        return CatalogProductRehydrationResult.failed(reference, CatalogRehydrationStatus.UNAVAILABLE, failure);
    }

}
