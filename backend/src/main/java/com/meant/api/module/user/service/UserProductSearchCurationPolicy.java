package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserClothingFit;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class UserProductSearchCurationPolicy {

    private static final int MIN_VISIBLE_CURATOR_SCORE = 50;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Set<String> APPAREL_CATEGORY_TERMS = Set.of(
            "apparel", "clothing", "clothes", "fashion", "footwear", "shoes", "menswear", "womenswear"
    );
    private static final Set<String> AUDIENCE_REQUIRED_APPAREL_TERMS = Set.of(
            "anorak", "blazer", "blouse", "bodysuit", "boot", "boots", "bra", "bralette", "camisole", "cardigan",
            "chino", "chinos", "coat", "dress", "dress shirt", "fleece", "hoodie", "jacket", "jean", "jeans",
            "legging", "leggings", "loafer", "loafers", "pant", "pants", "polo", "sandal", "sandals", "shirt",
            "shirts", "shoe", "shoes", "short", "shorts", "skirt", "skirts", "slipper", "slippers", "sneaker",
            "sneakers", "suit", "sweater", "sweaters", "sweatshirt", "sweatshirts", "swimsuit", "tank",
            "tank top", "tanks", "tee", "tees", "trouser", "trousers", "t shirt", "t shirts", "tshirt",
            "tshirts"
    );
    private static final Set<String> MEN_AUDIENCE_TERMS = Set.of(
            "man", "male", "men", "men s", "mens", "menswear", "gentleman", "gentlemen"
    );
    private static final Set<String> WOMEN_AUDIENCE_TERMS = Set.of(
            "female", "ladies", "lady", "misses", "woman", "women", "women s", "womens", "womenswear"
    );
    private static final Set<String> CHILD_AUDIENCE_TERMS = Set.of(
            "boys", "children", "girls", "infant", "kid", "kids", "toddler", "youth"
    );
    private static final Set<String> UNISEX_AUDIENCE_TERMS = Set.of(
            "all gender", "all genders", "gender neutral", "unisex"
    );

    public List<UserProductSearchProductResult> visibleProducts(List<UserProductSearchProductResult> products) {
        return visibleProducts(products, null);
    }

    public List<UserProductSearchProductResult> visibleProducts(
            List<UserProductSearchProductResult> products,
            UserSettingsResult settings
    ) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        UserClothingFit clothingFit = settings == null
                ? null
                : UserClothingFit.fromValue(settings.clothingFit()).orElse(null);
        return products.stream()
                .filter(product -> isVisible(product, clothingFit))
                .toList();
    }

    private boolean isVisible(UserProductSearchProductResult product, UserClothingFit clothingFit) {
        return product != null
                && product.matchScore() >= MIN_VISIBLE_CURATOR_SCORE
                && matchesClothingFit(product, clothingFit);
    }

    private boolean matchesClothingFit(UserProductSearchProductResult product, UserClothingFit clothingFit) {
        if (clothingFit == null || clothingFit == UserClothingFit.OTHER) {
            return true;
        }
        ApparelAudienceEvidence evidence = ApparelAudienceEvidence.from(product);
        if (!evidence.requiresDeclaredAudience()) {
            return true;
        }
        if (evidence.hasChildAudience()) {
            return false;
        }
        return switch (clothingFit) {
            case MEN -> evidence.hasMenAudience() || evidence.hasUnisexAudience();
            case WOMEN -> evidence.hasWomenAudience() || evidence.hasUnisexAudience();
            case OTHER -> true;
        };
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String plainText = HTML_TAG_PATTERN.matcher(value).replaceAll(" ");
        String normalized = Normalizer.normalize(plainText, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
        String cleaned = PUNCTUATION_PATTERN.matcher(normalized).replaceAll(" ");
        return SPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    private static boolean containsPhrase(String haystack, String phrase) {
        String normalizedPhrase = normalized(phrase);
        return !normalizedPhrase.isBlank() && haystack.contains(" " + normalizedPhrase + " ");
    }

    private static boolean containsAny(String haystack, Set<String> phrases) {
        return phrases.stream().anyMatch(phrase -> containsPhrase(haystack, phrase));
    }

    private static <T> Stream<T> stream(List<T> values) {
        return values == null ? Stream.empty() : values.stream();
    }

    private record ApparelAudienceEvidence(
            String text,
            String categoryText
    ) {

        static ApparelAudienceEvidence from(UserProductSearchProductResult product) {
            String categories = normalized(stream(product.categories())
                    .filter(category -> category != null)
                    .flatMap(category -> Stream.of(category.value(), category.taxonomy()))
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" ")));
            String text = normalized(Stream.of(
                            product.title(),
                            product.detailDescription(),
                            product.descriptionHtml(),
                            product.selectedVariantTitle(),
                            product.selectedVariantImageAltText(),
                            categories,
                            join(stream(product.media())
                                    .filter(media -> media != null)
                                    .map(ProductCatalogMedia::altText)),
                            join(stream(product.collections())),
                            join(stream(product.attributes())
                                    .filter(attribute -> attribute != null)
                                    .flatMap(attribute -> Stream.of(
                                            attribute.name(),
                                            attribute.value()
                                    )))
                    )
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" ")));
            return new ApparelAudienceEvidence(" " + text + " ", " " + categories + " ");
        }

        private boolean requiresDeclaredAudience() {
            return containsAny(categoryText, APPAREL_CATEGORY_TERMS)
                    || containsAny(text, AUDIENCE_REQUIRED_APPAREL_TERMS);
        }

        private boolean hasMenAudience() {
            return containsAny(text, MEN_AUDIENCE_TERMS);
        }

        private boolean hasWomenAudience() {
            return containsAny(text, WOMEN_AUDIENCE_TERMS);
        }

        private boolean hasChildAudience() {
            return containsAny(text, CHILD_AUDIENCE_TERMS);
        }

        private boolean hasUnisexAudience() {
            return containsAny(text, UNISEX_AUDIENCE_TERMS);
        }

        private static <T> String join(Stream<T> values) {
            return values
                    .filter(value -> value != null)
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining(" "));
        }
    }
}
