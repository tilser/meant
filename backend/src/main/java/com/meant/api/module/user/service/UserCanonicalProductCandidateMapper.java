package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.LocalMerchantRouting;
import com.meant.api.plugin.catalog.common.dto.IdentityEvidenceStrength;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductCertification;
import com.meant.api.plugin.catalog.common.dto.ProductMaterial;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.ProductRetrievalSignal;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidence;
import com.meant.api.plugin.catalog.common.dto.ProductIdentityEvidenceKind;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.support.OfferIdentityStrategy;
import com.meant.api.plugin.support.UcpMoney;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Maps typed source fields without inferring trusted brand/model or semantic identity from free text. */
@Component
@RequiredArgsConstructor
public class UserCanonicalProductCandidateMapper {

    private final List<OfferIdentityStrategy> offerIdentityStrategies;

    public ProductCandidate from(
            UserProductSearchProductResult product,
            MerchantIntegrationResult integration,
            Instant observedAt,
            ResultSourceType sourceType
    ) {
        return from(CandidateProduct.from(product), integration, observedAt, sourceType);
    }

    public ProductCandidate from(
            MerchantSemanticProductResult product,
            String productKey,
            MerchantIntegrationResult integration,
            Instant observedAt
    ) {
        return from(
                CandidateProduct.from(product, productKey),
                integration,
                observedAt,
                ResultSourceType.MERCHANT_STOREFRONT
        );
    }

    private ProductCandidate from(
            CandidateProduct product,
            MerchantIntegrationResult integration,
            Instant observedAt,
            ResultSourceType sourceType
    ) {
        ProviderIdentity provider = new ProviderIdentity(integration.provider().name());
        ExternalIdentifier merchantIdentity = externalMerchantIdentity(integration, provider);
        OfferMerchantScope merchantScope = merchantIdentity == null
                ? OfferMerchantScope.localIntegrationFallback(integration.id())
                : OfferMerchantScope.external(merchantIdentity);
        ExternalIdentifier provenanceProductIdentity = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT,
                provider.value(),
                product.productId()
        );
        ExternalIdentifier variantIdentity = ExternalIdentifier.optional(
                ExternalIdentifierType.VARIANT,
                provider.value(),
                product.selectedVariantId()
        );
        ExternalIdentifier offerProductIdentity = offerProductIdentity(
                provider,
                provenanceProductIdentity,
                variantIdentity
        );
        ResultSourceReference sourceReference = new ResultSourceReference(
                sourceType,
                product.productKey(),
                uri(integration.endpoint())
        );
        ResultProvenance provenance = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(
                        provider,
                        sourceType,
                        discoverySourceValue(sourceType, integration, merchantIdentity)
                ),
                new LocalMerchantRouting(integration.id()),
                merchantIdentity,
                provenanceProductIdentity,
                variantIdentity,
                new ResultFreshness(observedAt, null),
                sourceReference
        );
        OfferIdentity offerIdentity = new OfferIdentity(
                provider,
                merchantScope,
                offerProductIdentity,
                variantIdentity,
                selectedOptions(product.selectedOptions()),
                List.of(),
                null
        );
        Offer offer = new Offer(
                offerIdentity,
                product.merchantName(),
                product.selectedVariantTitle(),
                price(product),
                money(product.listPriceAmount(), product.listPriceCurrency()),
                availability(product),
                List.of(),
                null,
                List.of(provenance)
        );
        return new ProductCandidate(
                product.title(),
                firstText(product.detailDescription(), product.descriptionHtml()),
                media(product),
                attributes(product.attributes()),
                safeList(product.materials()).stream()
                        .filter(value -> !value.isBlank())
                        .map(value -> new ProductMaterial(value, null))
                        .toList(),
                safeList(product.certifications()).stream()
                        .filter(value -> !value.isBlank())
                        .map(value -> new ProductCertification(value, null, null, null))
                        .toList(),
                List.of(new ProductAttribution(
                        firstText(product.merchantName(), provider.value()),
                        uri(product.url()),
                        sourceReference
                )),
                canonicalUrlEvidence(provider, product.url(), sourceReference),
                List.of(provenance),
                List.of(new ProductRetrievalSignal(
                        provenance.discoverySource(),
                        offer.identity().merchantScope(),
                        ProductRetrievalSignal.Feature.INTENT_FIT,
                        product.retrievalIntentFitBasisPoints(),
                        "merchant-semantic-voyage-rerank-v1"
                )),
                offer
        );
    }

    private List<ProductIdentityEvidence> canonicalUrlEvidence(
            ProviderIdentity provider,
            String productUrl,
            ResultSourceReference sourceReference
    ) {
        URI url = uri(productUrl);
        if (url == null || !url.isAbsolute() || url.getHost() == null) {
            return List.of();
        }
        return List.of(new ProductIdentityEvidence(
                ProductIdentityEvidenceKind.CANONICAL_URL,
                IdentityEvidenceStrength.TRUSTED_EXACT,
                9_500,
                List.of(new ExternalIdentifier(
                        ExternalIdentifierType.CANONICAL_URL,
                        provider.value(),
                        url.toString()
                )),
                sourceReference
        ));
    }

    private ExternalIdentifier externalMerchantIdentity(
            MerchantIntegrationResult integration,
            ProviderIdentity provider
    ) {
        String externalMerchantId = trimToNull(integration.externalMerchantId());
        String verifiedShopIdentity = trimToNull(integration.verifiedShopIdentity());
        String externalIdentity = externalMerchantId == null ? verifiedShopIdentity : externalMerchantId;
        return externalIdentity == null
                ? null
                : new ExternalIdentifier(ExternalIdentifierType.MERCHANT, provider.value(), externalIdentity);
    }

    private ExternalIdentifier offerProductIdentity(
            ProviderIdentity provider,
            ExternalIdentifier productIdentity,
            ExternalIdentifier variantIdentity
    ) {
        return offerIdentityStrategies.stream()
                .filter(strategy -> strategy.supports(provider))
                .findFirst()
                .map(strategy -> strategy.product(provider, productIdentity, variantIdentity))
                .orElse(productIdentity);
    }

    private String discoverySourceValue(
            ResultSourceType sourceType,
            MerchantIntegrationResult integration,
            ExternalIdentifier externalMerchantIdentity
    ) {
        return switch (sourceType) {
            case MERCHANT_STOREFRONT -> externalMerchantIdentity == null
                    ? "LOCAL_STOREFRONT:" + integration.id()
                    : externalMerchantIdentity.value();
            case PROVIDER_CATALOG -> "PROVIDER_CATALOG";
            case CACHED_OBSERVATION -> "USER_PRODUCT_SEARCH_CACHE";
            case DATASET_IMPORT -> "DATASET_IMPORT";
            case MANUAL_ASSERTION -> "MANUAL_ASSERTION";
        };
    }

    private Money price(CandidateProduct product) {
        String currency = firstText(product.selectedVariantPriceCurrency(), product.priceCurrency());
        if (currency == null || currency.isBlank()) {
            return null;
        }
        Long minorUnits = UcpMoney.minorAmount(product.selectedVariantPriceAmount(), currency);
        if (minorUnits == null) {
            minorUnits = product.priceMinAmount();
        }
        return money(minorUnits, currency);
    }

    private Money money(Long minorUnits, String currency) {
        return minorUnits == null || currency == null || currency.isBlank()
                ? null
                : new Money(minorUnits, currency);
    }

    private OfferAvailability availability(CandidateProduct product) {
        Boolean available = product.selectedVariantAvailable() == null
                ? product.available()
                : product.selectedVariantAvailable();
        OfferAvailabilityStatus status = available == null
                ? OfferAvailabilityStatus.UNKNOWN
                : available ? OfferAvailabilityStatus.IN_STOCK : OfferAvailabilityStatus.OUT_OF_STOCK;
        return new OfferAvailability(status, null, null);
    }

    private List<ProductMedia> media(CandidateProduct product) {
        List<ProductMedia> richMedia = safeList(product.media()).stream()
                .map(this::media)
                .filter(Objects::nonNull)
                .toList();
        if (!richMedia.isEmpty()) {
            return richMedia;
        }
        URI image = uri(firstText(product.detailImageUrl(), product.imageUrl()));
        return image == null ? List.of() : List.of(new ProductMedia(ProductMediaType.IMAGE, image, product.title(), null, null));
    }

    private ProductMedia media(ProductCatalogMedia source) {
        URI url = uri(source.url());
        if (url == null) {
            return null;
        }
        ProductMediaType type;
        try {
            type = source.type() == null || source.type().isBlank()
                    ? ProductMediaType.OTHER
                    : ProductMediaType.valueOf(source.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            type = ProductMediaType.OTHER;
        }
        return new ProductMedia(type, url, source.altText(), null, null);
    }

    private List<ProductAttribute> attributes(List<ProductCatalogAttribute> attributes) {
        return safeList(attributes).stream()
                .filter(Objects::nonNull)
                .filter(value -> value.name() != null && !value.name().isBlank()
                        && value.value() != null && !value.value().isBlank())
                .map(value -> new ProductAttribute(null, value.name(), value.value()))
                .toList();
    }

    private List<ProductAttribute> selectedOptions(List<ProductDetailsResponse.SelectedOption> selectedOptions) {
        return safeList(selectedOptions).stream()
                .filter(value -> value.name() != null && !value.name().isBlank()
                        && value.value() != null && !value.value().isBlank())
                .map(value -> new ProductAttribute("variant-option", value.name(), value.value()))
                .distinct()
                .toList();
    }

    private URI uri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private record CandidateProduct(
            String productKey,
            String productId,
            String title,
            String descriptionHtml,
            String url,
            String imageUrl,
            Long priceMinAmount,
            String priceCurrency,
            Long listPriceAmount,
            String listPriceCurrency,
            List<ProductCatalogMedia> media,
            List<String> certifications,
            List<String> materials,
            List<ProductCatalogAttribute> attributes,
            Boolean available,
            String detailDescription,
            String detailImageUrl,
            String selectedVariantId,
            String selectedVariantTitle,
            List<ProductDetailsResponse.SelectedOption> selectedOptions,
            String selectedVariantPriceAmount,
            String selectedVariantPriceCurrency,
            Boolean selectedVariantAvailable,
            String merchantName,
            int retrievalIntentFitBasisPoints
    ) {

        private static CandidateProduct from(UserProductSearchProductResult product) {
            return new CandidateProduct(
                    product.productKey(),
                    product.productId(),
                    product.title(),
                    product.descriptionHtml(),
                    product.url(),
                    product.imageUrl(),
                    product.priceMinAmount(),
                    product.priceCurrency(),
                    product.listPriceAmount(),
                    product.listPriceCurrency(),
                    product.media(),
                    product.certifications(),
                    product.materials(),
                    product.attributes(),
                    product.available(),
                    product.detailDescription(),
                    product.detailImageUrl(),
                    product.selectedVariantId(),
                    product.selectedVariantTitle(),
                    List.of(),
                    product.selectedVariantPriceAmount(),
                    product.selectedVariantPriceCurrency(),
                    product.selectedVariantAvailable(),
                    product.merchantName(),
                    calibratedRetrievalScore(product.productRerankScore(), product.rank())
            );
        }

        private static CandidateProduct from(MerchantSemanticProductResult product, String productKey) {
            return new CandidateProduct(
                    productKey,
                    product.productId(),
                    product.title(),
                    product.descriptionHtml(),
                    product.url(),
                    product.imageUrl(),
                    product.priceMinAmount(),
                    product.priceCurrency(),
                    product.listPriceAmount(),
                    product.listPriceCurrency(),
                    product.media(),
                    product.certifications(),
                    product.materials(),
                    product.attributes(),
                    product.available(),
                    product.detailDescription(),
                    product.detailImageUrl(),
                    product.selectedVariantId(),
                    product.selectedVariantTitle(),
                    product.selectedOptions(),
                    product.selectedVariantPriceAmount(),
                    product.selectedVariantPriceCurrency(),
                    product.selectedVariantAvailable(),
                    product.merchantName(),
                    calibratedRetrievalScore(product.productRerankScore(), product.rank())
            );
        }

        private static int calibratedRetrievalScore(double sourceScore, int sourceRank) {
            double boundedScore = Double.isFinite(sourceScore)
                    ? Math.max(0.0d, Math.min(1.0d, sourceScore))
                    : 0.0d;
            int boundedRank = Math.max(1, Math.min(100, sourceRank));
            double ordinalScore = 1.0d - ((boundedRank - 1) / 99.0d);
            return (int) Math.round((boundedScore * 0.7d + ordinalScore * 0.3d) * 10_000.0d);
        }
    }
}
