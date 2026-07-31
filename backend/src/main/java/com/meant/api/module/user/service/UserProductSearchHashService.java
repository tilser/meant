package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProductSearchHashService {

    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    private final UserProductSearchProperties userProductSearchProperties;

    public String normalizeQuery(String query) {
        return SPACE_PATTERN.matcher(Normalizer.normalize(query, Normalizer.Form.NFKC)
                        .trim()
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ");
    }

    public String productKey(MerchantSemanticProductResult product) {
        return product.merchantDomain() + ":" + product.productId();
    }

    public String profileHash(UserSettingsResult settings) {
        return sha256(String.join("\n",
                "promptVersion=" + userProductSearchProperties.explanationPromptVersion(),
                "budget=" + value(settings.budget()),
                "currency=" + value(settings.currency()),
                "clothingFit=" + value(settings.clothingFit()),
                "locations=" + locationsValue(settings.locations()),
                "filters=" + filtersValue(settings.filters())
        ));
    }

    public String searchProfileHash(
            UserSettingsResult settings,
            String inventoryProfileHash,
            String tasteProfileHash
    ) {
        return profileHash(settings)
                + ":" + inventoryProfileHash
                + ":" + tasteProfileHash;
    }

    public String productHash(MerchantSemanticProductResult product) {
        return sha256(Stream.of(
                        "productKey=" + productKey(product),
                        "merchant=" + value(product.merchantName()),
                        "title=" + value(product.title()),
                        "description=" + value(plainText(product.detailDescription(), product.descriptionHtml())),
                        "url=" + value(product.url()),
                        "image=" + value(firstPresent(
                                product.detailImageUrl(),
                                product.selectedVariantImageUrl(),
                                product.imageUrl())),
                        "priceMin=" + value(firstPresent(product.detailPriceMin(), stringValue(product.priceMinAmount()))),
                        "priceMax=" + value(firstPresent(product.detailPriceMax(), stringValue(product.priceMaxAmount()))),
                        "currency=" + value(firstPresent(product.detailPriceCurrency(), product.priceCurrency())),
                        "listPrice=" + value(product.listPriceAmount()),
                        "listPriceCurrency=" + value(product.listPriceCurrency()),
                        "rating=" + value(product.ratingScore()),
                        "reviewCount=" + value(product.reviewCount()),
                        "media=" + values(product.media()),
                        "categories=" + values(product.categories()),
                        "certifications=" + values(product.certifications()),
                        "materials=" + values(product.materials()),
                        "skus=" + values(product.skus()),
                        "collections=" + values(product.collections()),
                        "attributes=" + values(product.attributes()),
                        "variant=" + value(product.selectedVariantId()),
                        "available=" + value(product.selectedVariantAvailable() == null
                                ? product.available()
                                : product.selectedVariantAvailable())
                )
                .collect(Collectors.joining("\n")));
    }

    private String filtersValue(List<ShoppingFilterResult> filters) {
        return filters.stream()
                .sorted(Comparator.comparing(ShoppingFilterResult::id))
                .map(filter -> Stream.of(
                                value(filter.id()),
                                value(filter.label()),
                                value(filter.description()),
                                value(filter.category()),
                                value(filter.polarity()))
                        .collect(Collectors.joining("|")))
                .collect(Collectors.joining(";"));
    }

    private String locationsValue(List<UserLocationResult> locations) {
        return locations.stream()
                .map(location -> Stream.of(
                                location.id(),
                                location.country(),
                                location.code(),
                                location.region(),
                                location.postalCode(),
                                location.city())
                        .map(this::value)
                        .collect(Collectors.joining("|")))
                .collect(Collectors.joining(";"));
    }

    private String plainText(String detailDescription, String descriptionHtml) {
        String value = firstPresent(detailDescription, descriptionHtml);
        if (value == null) {
            return "";
        }
        return SPACE_PATTERN.matcher(HTML_TAG_PATTERN.matcher(value).replaceAll(" "))
                .replaceAll(" ")
                .trim();
    }

    private String stringValue(Long value) {
        return value == null ? null : value.toString();
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String firstPresent(String first, String second, String third) {
        String value = firstPresent(first, second);
        return value == null ? firstPresent(third, null) : value;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String values(List<?> values) {
        return values == null ? "" : values.stream()
                .map(this::value)
                .collect(Collectors.joining("|"));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
