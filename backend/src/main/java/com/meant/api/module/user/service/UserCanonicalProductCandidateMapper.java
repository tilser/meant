package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductAttribution;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProductCertification;
import com.meant.api.plugin.catalog.common.dto.ProductMaterial;
import com.meant.api.plugin.catalog.common.dto.ProductMedia;
import com.meant.api.plugin.catalog.common.dto.ProductMediaType;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.support.UcpMoney;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class UserCanonicalProductCandidateMapper {

    public ProductCandidate from(
            UserProductSearchProductResult product,
            MerchantIntegrationResult integration,
            Instant observedAt,
            ResultSourceType sourceType
    ) {
        ProviderIdentity provider = new ProviderIdentity(integration.provider().name());
        ExternalIdentifier merchantIdentity = merchantIdentity(integration, provider);
        ExternalIdentifier productIdentity = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT,
                provider.value(),
                product.productId()
        );
        ExternalIdentifier variantIdentity = ExternalIdentifier.optional(
                ExternalIdentifierType.VARIANT,
                provider.value(),
                product.selectedVariantId()
        );
        ResultSourceReference sourceReference = new ResultSourceReference(
                sourceType,
                product.productKey(),
                uri(integration.endpoint())
        );
        ResultProvenance provenance = new ResultProvenance(
                provider,
                integration.id(),
                merchantIdentity,
                productIdentity,
                variantIdentity,
                new ResultFreshness(observedAt, null),
                sourceReference
        );
        OfferIdentity offerIdentity = new OfferIdentity(
                provider,
                integration.id(),
                merchantIdentity,
                productIdentity,
                variantIdentity,
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
                List.of(),
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
                List.of(),
                List.of(provenance),
                offer
        );
    }

    private ExternalIdentifier merchantIdentity(
            MerchantIntegrationResult integration,
            ProviderIdentity provider
    ) {
        String externalMerchantId = trimToNull(integration.externalMerchantId());
        String verifiedShopIdentity = trimToNull(integration.verifiedShopIdentity());
        String externalIdentity = Stream.of(externalMerchantId, verifiedShopIdentity)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(integration.id().toString());
        String namespace = externalMerchantId == null && verifiedShopIdentity == null
                ? "MEANT_INTEGRATION"
                : provider.value();
        return new ExternalIdentifier(ExternalIdentifierType.MERCHANT, namespace, externalIdentity);
    }

    private Money price(UserProductSearchProductResult product) {
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

    private OfferAvailability availability(UserProductSearchProductResult product) {
        Boolean available = product.selectedVariantAvailable() == null
                ? product.available()
                : product.selectedVariantAvailable();
        OfferAvailabilityStatus status = available == null
                ? OfferAvailabilityStatus.UNKNOWN
                : available ? OfferAvailabilityStatus.IN_STOCK : OfferAvailabilityStatus.OUT_OF_STOCK;
        return new OfferAvailability(status, null, null);
    }

    private List<ProductMedia> media(UserProductSearchProductResult product) {
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
}
