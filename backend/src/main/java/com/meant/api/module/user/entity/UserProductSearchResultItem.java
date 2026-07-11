package com.meant.api.module.user.entity;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUsePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "user_product_search_results",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_product_search_results_product",
                        columnNames = {"search_id", "product_key"}
                )
        }
)
public class UserProductSearchResultItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID searchId;

    @Column(nullable = false)
    private String productKey;

    @Column(nullable = false)
    private String productHash;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String merchantDomain;

    private String merchantName;

    private String endpoint;

    private String sourceProvider;

    private String sourceType;

    private String sourceIdentity;

    @Column(nullable = false)
    private int merchantRank;

    @Column(nullable = false)
    private double merchantSemanticScore;

    @Column(nullable = false)
    private double merchantRerankScore;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private String title;

    private String descriptionHtml;

    private String url;

    private String imageUrl;

    private Long priceMinAmount;

    private Long priceMaxAmount;

    private String priceCurrency;

    private Long listPriceAmount;

    private String listPriceCurrency;

    private Double ratingScore;

    private Integer reviewCount;

    private String mediaJson;

    private String categoriesJson;

    private String certificationsJson;

    private String materialsJson;

    private String skusJson;

    private String collectionsJson;

    private String attributesJson;

    private Boolean available;

    private String detailError;

    private String detailDescription;

    private String detailImageUrl;

    private String detailPriceMin;

    private String detailPriceMax;

    private String detailPriceCurrency;

    private String selectedVariantId;

    private String selectedVariantTitle;

    private String selectedVariantPriceAmount;

    private String selectedVariantPriceCurrency;

    private String selectedVariantImageUrl;

    private String selectedVariantImageAltText;

    private Boolean selectedVariantAvailable;

    @Column(nullable = false)
    private int catalogRank;

    @Column(nullable = false)
    private double productRerankScore;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false)
    private Instant createdAt;

    public static UserProductSearchResultItem from(
            UUID searchId,
            String productKey,
            String productHash,
            MerchantSemanticProductResult result,
            Instant now
    ) {
        return from(searchId, productKey, productHash, result, now, RichCatalogSnapshot.empty(),
                GenericUcpCatalogDataUsePolicy.SOURCE);
    }

    public static UserProductSearchResultItem from(
            UUID searchId,
            String productKey,
            String productHash,
            MerchantSemanticProductResult result,
            Instant now,
            RichCatalogSnapshot richCatalogSnapshot
    ) {
        return from(searchId, productKey, productHash, result, now, richCatalogSnapshot,
                GenericUcpCatalogDataUsePolicy.SOURCE);
    }

    public static UserProductSearchResultItem from(
            UUID searchId,
            String productKey,
            String productHash,
            MerchantSemanticProductResult result,
            Instant now,
            RichCatalogSnapshot richCatalogSnapshot,
            DiscoverySourceIdentity discoverySource
    ) {
        return UserProductSearchResultItem.builder()
                .id(UUID.randomUUID())
                .searchId(searchId)
                .productKey(productKey)
                .productHash(productHash)
                .merchantId(result.merchantId())
                .merchantDomain(result.merchantDomain())
                .merchantName(result.merchantName())
                .endpoint(result.endpoint())
                .sourceProvider(discoverySource.provider().value())
                .sourceType(discoverySource.type().name())
                .sourceIdentity(discoverySource.value())
                .merchantRank(result.merchantRank())
                .merchantSemanticScore(result.merchantSemanticScore())
                .merchantRerankScore(result.merchantRerankScore())
                .productId(requiredValue(result.productId(), productKey))
                .title(requiredValue(result.title(), result.productId()))
                .descriptionHtml(result.descriptionHtml())
                .url(result.url())
                .imageUrl(resultImageUrl(result))
                .priceMinAmount(result.priceMinAmount())
                .priceMaxAmount(result.priceMaxAmount())
                .priceCurrency(result.priceCurrency())
                .listPriceAmount(result.listPriceAmount())
                .listPriceCurrency(result.listPriceCurrency())
                .ratingScore(result.ratingScore())
                .reviewCount(result.reviewCount())
                .mediaJson(richCatalogSnapshot.mediaJson())
                .categoriesJson(richCatalogSnapshot.categoriesJson())
                .certificationsJson(richCatalogSnapshot.certificationsJson())
                .materialsJson(richCatalogSnapshot.materialsJson())
                .skusJson(richCatalogSnapshot.skusJson())
                .collectionsJson(richCatalogSnapshot.collectionsJson())
                .attributesJson(richCatalogSnapshot.attributesJson())
                .available(result.available())
                .detailError(result.detailError())
                .detailDescription(result.detailDescription())
                .detailImageUrl(result.detailImageUrl())
                .detailPriceMin(result.detailPriceMin())
                .detailPriceMax(result.detailPriceMax())
                .detailPriceCurrency(result.detailPriceCurrency())
                .selectedVariantId(result.selectedVariantId())
                .selectedVariantTitle(result.selectedVariantTitle())
                .selectedVariantPriceAmount(result.selectedVariantPriceAmount())
                .selectedVariantPriceCurrency(result.selectedVariantPriceCurrency())
                .selectedVariantImageUrl(result.selectedVariantImageUrl())
                .selectedVariantImageAltText(result.selectedVariantImageAltText())
                .selectedVariantAvailable(result.selectedVariantAvailable())
                .catalogRank(result.catalogRank())
                .productRerankScore(result.productRerankScore())
                .rank(result.rank())
                .createdAt(now)
                .build();
    }

    public DiscoverySourceIdentity discoverySource() {
        if (sourceProvider == null || sourceType == null || sourceIdentity == null) {
            return null;
        }
        try {
            return new DiscoverySourceIdentity(
                    new ProviderIdentity(sourceProvider),
                    ResultSourceType.valueOf(sourceType),
                    sourceIdentity
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String resultImageUrl(MerchantSemanticProductResult result) {
        if (result.detailImageUrl() != null && !result.detailImageUrl().isBlank()) {
            return result.detailImageUrl();
        }
        if (result.selectedVariantImageUrl() != null && !result.selectedVariantImageUrl().isBlank()) {
            return result.selectedVariantImageUrl();
        }
        return result.imageUrl();
    }

    private static String requiredValue(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null || fallback.isBlank() ? "Untitled product" : fallback;
    }

    public record RichCatalogSnapshot(
            String mediaJson,
            String categoriesJson,
            String certificationsJson,
            String materialsJson,
            String skusJson,
            String collectionsJson,
            String attributesJson
    ) {

        static RichCatalogSnapshot empty() {
            return new RichCatalogSnapshot(null, null, null, null, null, null, null);
        }
    }
}
