package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogRating;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductRichCatalogData;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantRichCatalogNormalizer {

    private final ProductCatalogMetadataNormalizer metadataNormalizer;

    ProductRichCatalogData normalize(
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
        return new ProductRichCatalogData(
                listPrice == null ? null : listPrice.amount(),
                listPrice == null ? null : firstPresent(listPrice.currency(), currency),
                firstPresent(
                        ratingValue(detailProduct == null ? null : detailProduct.rating()),
                        ratingValue(catalogProduct.rating())
                ),
                firstPresent(
                        detailProduct == null ? null : detailProduct.reviewCount(),
                        ratingCount(detailProduct == null ? null : detailProduct.rating()),
                        catalogProduct.reviewCount(),
                        ratingCount(catalogProduct.rating())
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
                metadataNormalizer.distinctStrings(Stream.concat(
                        metadataNormalizer.stringValues(catalogProduct.collections()).stream(),
                        metadataNormalizer.stringValues(detailProduct == null ? null : detailProduct.collections()).stream()
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
                metadataNormalizer.blankToDefault(media.type(), "image"),
                firstPresent(media.url(), media.previewImageUrl()),
                metadataNormalizer.blankToNull(media.altText())
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
                metadataNormalizer.blankToDefault(media.type(), "image"),
                firstPresent(media.url(), media.previewImageUrl()),
                metadataNormalizer.blankToNull(media.altText())
        );
    }

    private ProductCatalogMedia imageMedia(String url, String altText) {
        return new ProductCatalogMedia("image", metadataNormalizer.blankToNull(url), metadataNormalizer.blankToNull(altText));
    }

    private void addMedia(Map<String, ProductCatalogMedia> media, ProductCatalogMedia item) {
        if (item == null || item.url() == null || item.url().isBlank()) {
            return;
        }
        String type = metadataNormalizer.blankToDefault(item.type(), "image");
        media.putIfAbsent(type.toLowerCase(Locale.ROOT) + "|" + item.url(), new ProductCatalogMedia(
                type,
                item.url(),
                metadataNormalizer.blankToNull(item.altText())
        ));
    }

    private List<ProductCatalogCategory> richCategories(CatalogSearchResponse.Product product) {
        Map<String, ProductCatalogCategory> categories = new LinkedHashMap<>();
        for (CatalogSearchResponse.Category category : safeNonNullList(product.categories())) {
            String value = metadataNormalizer.blankToNull(category.value());
            if (value != null) {
                categories.putIfAbsent(
                        value.toLowerCase(Locale.ROOT) + "|" + metadataNormalizer.blankToDefault(category.taxonomy(), ""),
                        new ProductCatalogCategory(value, metadataNormalizer.blankToNull(category.taxonomy()))
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
        return metadataNormalizer.distinctStrings(Stream.of(
                        metadataNormalizer.stringValues(catalogProduct.skus()).stream(),
                        safeNonNullList(catalogProduct.variants()).stream().map(CatalogSearchResponse.Variant::sku),
                        metadataNormalizer.stringValues(detailProduct == null ? null : detailProduct.skus()).stream(),
                        Stream.of(selectedVariant == null ? null : selectedVariant.sku())
                )
                .flatMap(stream -> stream));
    }

    private List<ProductCatalogAttribute> richAttributes(
            CatalogSearchResponse.Product catalogProduct,
            ProductDetailsResponse.Product detailProduct
    ) {
        Map<String, ProductCatalogAttribute> attributes = new LinkedHashMap<>();
        Stream.concat(
                        Stream.of(
                                        catalogProduct.metadata(),
                                        catalogProduct.metafields(),
                                        detailProduct == null ? null : detailProduct.metadata(),
                                        detailProduct == null ? null : detailProduct.metafields()
                                )
                                .flatMap(value -> metadataNormalizer.attributes(value).stream()),
                        Stream.of(
                                        catalogProduct.techSpecs(),
                                        detailProduct == null ? null : detailProduct.techSpecs()
                                )
                                .flatMap(value -> metadataNormalizer.attributes(value).stream())
                )
                .forEach(attribute -> {
                    String name = metadataNormalizer.blankToNull(attribute.name());
                    String value = metadataNormalizer.blankToNull(attribute.value());
                    if (name != null && value != null) {
                        attributes.putIfAbsent(name.toLowerCase(Locale.ROOT) + "|" + value.toLowerCase(Locale.ROOT),
                                new ProductCatalogAttribute(name, value));
                    }
                });
        return List.copyOf(attributes.values());
    }

    private List<String> richMetadataValues(
            List<List<String>> explicitValues,
            List<ProductCatalogAttribute> attributes,
            List<String> attributeKeyFragments
    ) {
        Stream<String> explicit = explicitValues.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream);
        Stream<String> inferred = attributes.stream()
                .filter(attribute -> metadataNormalizer.containsAny(attribute.name(), attributeKeyFragments))
                .flatMap(attribute -> metadataNormalizer.stringValues(attribute.value()).stream());
        return metadataNormalizer.distinctStrings(Stream.concat(explicit, inferred));
    }

    private Double ratingValue(CatalogRating rating) {
        return rating == null ? null : UcpDecimal.ratingValue(rating.value());
    }

    private Integer ratingCount(CatalogRating rating) {
        return rating == null ? null : rating.count();
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
}
