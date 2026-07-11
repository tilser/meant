package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.IdentityEvidenceStrength;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferComponentIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import com.meant.api.plugin.catalog.common.support.ProductIdentityNormalizationSupport;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Barcode;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Category;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Media;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Product;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.SelectedOption;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse.Variant;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShopifyGlobalCatalogNormalizer {

    public static final ProviderIdentity SHOPIFY = ShopifyOfferIdentityStrategy.PROVIDER;
    private static final String UPID_PREFIX = "gid://shopify/p/";

    private final ShopifyGlobalCatalogProperties properties;
    private final Clock clock;

    @Autowired
    public ShopifyGlobalCatalogNormalizer(ShopifyGlobalCatalogProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ShopifyGlobalCatalogNormalizer(
            ShopifyGlobalCatalogProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;
    }

    public NormalizedCandidates normalize(ShopifyGlobalCatalogResponse response) {
        Instant observedAt = clock.instant();
        ResultSourceReference sourceReference = sourceReference();
        List<ProductCandidate> candidates = new ArrayList<>();
        boolean truncated = false;

        outer:
        for (Product product : response.resolvedProducts()) {
            for (Variant variant : safe(product.variants())) {
                if (candidates.size() >= properties.maximumCandidates()) {
                    truncated = true;
                    break outer;
                }
                candidates.add(candidate(product, variant, observedAt, sourceReference));
            }
        }
        return new NormalizedCandidates(candidates, truncated);
    }

    private ProductCandidate candidate(
            Product product,
            Variant variant,
            Instant observedAt,
            ResultSourceReference sourceReference
    ) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, variant.seller().id());
        ExternalIdentifier provenanceProductIdentity = identifier(
                ExternalIdentifierType.PRODUCT,
                firstText(variant.productId(), product.id())
        );
        ExternalIdentifier variantIdentity = identifier(ExternalIdentifierType.VARIANT, variant.id());
        ExternalIdentifier offerProductIdentity = ShopifyOfferIdentityStrategy.productIdentity(
                SHOPIFY,
                provenanceProductIdentity,
                variantIdentity
        );
        ResultProvenance provenance = new ResultProvenance(
                SHOPIFY,
                discoverySource(),
                null,
                merchant,
                provenanceProductIdentity,
                variantIdentity,
                new ResultFreshness(observedAt, null),
                sourceReference
        );
        List<ProductAttribute> selectedOptions = selectedOptions(
                safe(variant.options()).isEmpty() ? safe(product.selected()) : safe(variant.options())
        );
        OfferIdentity offerIdentity = new OfferIdentity(
                SHOPIFY,
                OfferMerchantScope.external(merchant),
                offerProductIdentity,
                variantIdentity,
                selectedOptions,
                components(variant.components()),
                sellingPlan(variant.sellingPlan())
        );
        Offer offer = new Offer(
                offerIdentity,
                variant.seller().name(),
                variant.title(),
                money(variant.price()),
                money(variant.listPrice()),
                availability(variant.availability()),
                List.of(),
                safeHttpsUri(variant.checkoutUrl()),
                List.of(provenance)
        );
        return new ProductCandidate(
                product.title(),
                product.description() == null ? null : product.description().preferredText(),
                media(product, variant),
                attributes(product),
                List.of(),
                List.of(),
                attribution(product, variant, sourceReference),
                evidence(product, variant, sourceReference),
                List.of(provenance),
                offer
        );
    }

    private List<ProductIdentityEvidence> evidence(
            Product product,
            Variant variant,
            ResultSourceReference sourceReference
    ) {
        List<ProductIdentityEvidence> evidence = new ArrayList<>();
        if (product.id().startsWith(UPID_PREFIX)) {
            evidence.add(new ProductIdentityEvidence(
                    ProductIdentityEvidenceKind.UPID,
                    IdentityEvidenceStrength.TRUSTED_EXACT,
                    10_000,
                    List.of(new ExternalIdentifier(ExternalIdentifierType.UPID, SHOPIFY.value(), product.id())),
                    sourceReference
            ));
        }
        for (Barcode barcode : safe(variant.barcodes())) {
            EvidenceType type = barcodeType(barcode);
            if (type != null && ProductIdentityNormalizationSupport
                    .universalTradeItemNumber(type.kind(), barcode.value()).isPresent()) {
                evidence.add(new ProductIdentityEvidence(
                        type.kind(),
                        IdentityEvidenceStrength.TRUSTED_EXACT,
                        10_000,
                        List.of(new ExternalIdentifier(type.identifierType(), null, barcode.value())),
                        sourceReference
                ));
            }
        }
        return List.copyOf(evidence);
    }

    private EvidenceType barcodeType(Barcode barcode) {
        if (barcode == null || !hasText(barcode.type())) {
            return null;
        }
        return switch (barcode.type().trim().toUpperCase(Locale.ROOT)) {
            case "GTIN" -> new EvidenceType(ProductIdentityEvidenceKind.GTIN, ExternalIdentifierType.GTIN);
            case "UPC" -> new EvidenceType(ProductIdentityEvidenceKind.UPC, ExternalIdentifierType.UPC);
            case "EAN" -> new EvidenceType(ProductIdentityEvidenceKind.EAN, ExternalIdentifierType.EAN);
            default -> null;
        };
    }

    private List<ProductMedia> media(Product product, Variant variant) {
        return java.util.stream.Stream.concat(safe(product.media()).stream(), safe(variant.media()).stream())
                .filter(Objects::nonNull)
                .map(this::media)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private ProductMedia media(Media media) {
        URI url = safeHttpsUri(media.url());
        if (url == null) {
            return null;
        }
        ProductMediaType type = switch (media.type() == null ? "" : media.type().toLowerCase(Locale.ROOT)) {
            case "image" -> ProductMediaType.IMAGE;
            case "video" -> ProductMediaType.VIDEO;
            case "model", "model_3d" -> ProductMediaType.MODEL;
            case "document" -> ProductMediaType.DOCUMENT;
            default -> ProductMediaType.OTHER;
        };
        Integer width = positive(media.width());
        Integer height = positive(media.height());
        return new ProductMedia(type, url, media.altText(), width, height);
    }

    private List<ProductAttribute> attributes(Product product) {
        List<ProductAttribute> attributes = new ArrayList<>();
        for (Category category : safe(product.categories())) {
            if (category != null && hasText(category.value())) {
                attributes.add(new ProductAttribute("category", firstText(category.taxonomy(), "category"), category.value()));
            }
        }
        if (product.metadata() != null) {
            addAttributes(attributes, "technical specification", product.metadata().techSpecs());
            addAttributes(attributes, "top feature", product.metadata().topFeatures());
            addAttributes(attributes, "unique selling point", product.metadata().uniqueSellingPoints());
        }
        return attributes.stream().distinct().toList();
    }

    private void addAttributes(List<ProductAttribute> attributes, String name, List<String> values) {
        for (String value : safe(values)) {
            if (hasText(value)) {
                attributes.add(new ProductAttribute("shopify-global-catalog", name, value));
            }
        }
    }

    private List<ProductAttribution> attribution(
            Product product,
            Variant variant,
            ResultSourceReference sourceReference
    ) {
        List<ProductAttribution> attributions = new ArrayList<>();
        addAttribution(attributions, "Product", firstText(variant.url(), product.url()), sourceReference);
        addAttribution(attributions, "Merchant", variant.seller().url(), sourceReference);
        for (ShopifyGlobalCatalogResponse.Link link : safe(variant.seller().links())) {
            if (link != null) {
                addAttribution(attributions, "Merchant " + firstText(link.type(), "link"), link.url(), sourceReference);
            }
        }
        return attributions.stream().distinct().toList();
    }

    private void addAttribution(
            List<ProductAttribution> attributions,
            String label,
            String value,
            ResultSourceReference sourceReference
    ) {
        URI uri = safeHttpsUri(value);
        if (uri != null) {
            attributions.add(new ProductAttribution(label, uri, sourceReference));
        }
    }

    private List<ProductAttribute> selectedOptions(List<SelectedOption> options) {
        return safe(options).stream()
                .filter(option -> option != null && hasText(option.name()) && hasText(option.label()))
                .map(option -> new ProductAttribute("variant-option", option.name(), option.label()))
                .toList();
    }

    private List<OfferComponentIdentity> components(
            List<ShopifyGlobalCatalogResponse.Component> components
    ) {
        return safe(components).stream()
                .filter(component -> component != null && hasText(component.productId()))
                .map(component -> new OfferComponentIdentity(
                        identifier(ExternalIdentifierType.PRODUCT, component.productId()),
                        hasText(component.variantId())
                                ? identifier(ExternalIdentifierType.VARIANT, component.variantId())
                                : null,
                        component.quantity() == null ? 1 : component.quantity(),
                        selectedOptions(component.options())
                ))
                .toList();
    }

    private SellingPlanIdentity sellingPlan(ShopifyGlobalCatalogResponse.SellingPlan plan) {
        if (plan == null || !hasText(plan.id()) && !hasText(plan.groupId())) {
            return null;
        }
        List<SellingPlanOption> options = safe(plan.options()).stream()
                .filter(option -> option != null && hasText(option.name()) && hasText(option.value()))
                .map(option -> new SellingPlanOption(option.name(), option.value()))
                .toList();
        return new SellingPlanIdentity(
                hasText(plan.groupId())
                        ? identifier(ExternalIdentifierType.SELLING_PLAN_GROUP, plan.groupId())
                        : null,
                hasText(plan.id()) ? identifier(ExternalIdentifierType.SELLING_PLAN, plan.id()) : null,
                options
        );
    }

    private OfferAvailability availability(ShopifyGlobalCatalogResponse.Availability availability) {
        if (availability == null) {
            return OfferAvailability.unknown();
        }
        OfferAvailabilityStatus status = status(availability.status(), availability.available());
        Integer quantity = availability.quantity() != null && availability.quantity() >= 0
                ? availability.quantity()
                : null;
        return new OfferAvailability(status, quantity, null);
    }

    private OfferAvailabilityStatus status(String status, Boolean available) {
        if (hasText(status)) {
            return switch (status.trim().toLowerCase(Locale.ROOT)) {
                case "in_stock", "available" -> OfferAvailabilityStatus.IN_STOCK;
                case "out_of_stock", "unavailable" -> OfferAvailabilityStatus.OUT_OF_STOCK;
                case "preorder", "pre_order" -> OfferAvailabilityStatus.PREORDER;
                case "backorder", "back_order" -> OfferAvailabilityStatus.BACKORDER;
                case "discontinued" -> OfferAvailabilityStatus.DISCONTINUED;
                default -> Boolean.TRUE.equals(available)
                        ? OfferAvailabilityStatus.IN_STOCK
                        : Boolean.FALSE.equals(available)
                                ? OfferAvailabilityStatus.OUT_OF_STOCK
                                : OfferAvailabilityStatus.UNKNOWN;
            };
        }
        return Boolean.TRUE.equals(available)
                ? OfferAvailabilityStatus.IN_STOCK
                : Boolean.FALSE.equals(available)
                        ? OfferAvailabilityStatus.OUT_OF_STOCK
                        : OfferAvailabilityStatus.UNKNOWN;
    }

    private Money money(ShopifyGlobalCatalogResponse.Price price) {
        return price == null || price.amount() == null || !hasText(price.currency())
                ? null
                : new Money(price.amount(), price.currency());
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, SHOPIFY.value(), value);
    }

    private DiscoverySourceIdentity discoverySource() {
        return new DiscoverySourceIdentity(SHOPIFY, ResultSourceType.PROVIDER_CATALOG, properties.sourceIdentity());
    }

    private ResultSourceReference sourceReference() {
        return new ResultSourceReference(
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity(),
                properties.endpoint()
        );
    }

    private URI safeHttpsUri(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            URI uri = new URI(value.trim());
            return uri.isAbsolute() && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? uri
                    : null;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record NormalizedCandidates(List<ProductCandidate> candidates, boolean truncated) {

        public NormalizedCandidates {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    private record EvidenceType(
            ProductIdentityEvidenceKind kind,
            ExternalIdentifierType identifierType
    ) {
    }
}
