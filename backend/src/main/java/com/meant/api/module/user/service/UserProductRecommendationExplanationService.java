package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserClothingFit;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserProductRecommendationExplanationService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern INTERNAL_EXPLANATION_PATTERN =
            Pattern.compile("\\b(?:agents?|curator|catalog[-\\s]+data|validation|verification)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final String INTERESTS_CATEGORY = "interests";
    private static final String SYSTEM_PROMPT = """
            You write short product recommendation explanations for Meant.
            Explain why each product is meant for this specific user and query.
            Use only facts present in the product data, merchant data, query, and user filters.
            Do not invent certifications, materials, review claims, discounts, or shipping promises.
            Write directly to the user. Do not mention internal agents, curator, catalog data, validation, or verification steps.
            Owned inventory signals are server-generated facts. Prefer complements and restocks; be explicit when a product looks duplicative.
            Filters in category interests are soft taste signals. Match them when the product clearly reflects the interest, but do not mark them missed just because the theme is absent.
            Clothing fit is a hard apparel and footwear audience constraint. Do not present opposite-audience or child-audience apparel as meant for the user.
            Use exact productKey values from the product list.
            matchedFilterIds and missedFilterIds must contain only filter IDs from the active user filters.
            Keep whyMeantForYou one concise sentence, under 220 characters.
            Return only the JSON object matching the requested schema.
            """;

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final UserProductSearchProperties userProductSearchProperties;
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final ObjectMapper objectMapper;

    public Map<String, UserProductRecommendationExplanationResult> explain(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            List<UserProductSearchProductSnapshot> products
    ) {
        return explain(userId, query, normalizedQuery, profileHash, settings, products, Map.of());
    }

    public Map<String, UserProductRecommendationExplanationResult> explain(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        String model = openRouterProperties.models().productRecommendationExplainer();
        String promptVersion = userProductSearchProperties.explanationPromptVersion();
        Map<String, UserProductRecommendationExplanationResult> cached =
                new LinkedHashMap<>(userProductSearchPersistenceService.findExplanations(
                        userId,
                        normalizedQuery,
                        profileHash,
                        model,
                        promptVersion,
                        products
                ));
        List<UserProductSearchProductSnapshot> missing = products.stream()
                .filter(product -> !cached.containsKey(product.productKey()))
                .toList();
        if (missing.isEmpty()) {
            return cached;
        }

        List<UserProductRecommendationExplanationResult> generated;
        try {
            generated = generate(
                    query,
                    settings,
                    missing,
                    inventorySignals
            );
        } catch (OpenRouterException exception) {
            log.warn(
                    "Could not generate product explanations; returning fallback explanations userId={} missingProductCount={} model={} promptVersion={} reason={}",
                    userId,
                    missing.size(),
                    model,
                    promptVersion,
                    exception.getMessage()
            );
            return withFallbacks(cached, products, inventorySignals);
        }
        Map<String, UserProductRecommendationExplanationResult> saved =
                userProductSearchPersistenceService.saveExplanations(
                        userId,
                        normalizedQuery,
                        profileHash,
                        model,
                        promptVersion,
                        generated,
                        Instant.now()
                );
        cached.putAll(saved);
        return withFallbacks(cached, products, inventorySignals);
    }

    private Map<String, UserProductRecommendationExplanationResult> withFallbacks(
            Map<String, UserProductRecommendationExplanationResult> explanations,
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        Map<String, UserInventoryRecommendationSignal> safeInventorySignals =
                inventorySignals == null ? Map.of() : inventorySignals;
        Map<String, UserProductRecommendationExplanationResult> result = new LinkedHashMap<>();
        products.forEach(product -> {
            UserProductRecommendationExplanationResult explanation = explanations.get(product.productKey());
            result.putIfAbsent(
                    product.productKey(),
                    explanation == null
                            ? UserProductRecommendationExplanationResult.fallback(
                                    product.productKey(),
                                    product.productHash(),
                                    safeInventorySignals.get(product.productKey())
                            )
                            : explanation
            );
        });
        return result;
    }

    private List<UserProductRecommendationExplanationResult> generate(
            String query,
            UserSettingsResult settings,
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        if (products.isEmpty()) {
            return List.of();
        }
        Map<String, UserInventoryRecommendationSignal> safeInventorySignals =
                inventorySignals == null ? Map.of() : inventorySignals;

        String response = openRouterChatClient.completeJson(
                openRouterProperties.models().productRecommendationExplainer(),
                SYSTEM_PROMPT,
                userPrompt(query, settings, products, safeInventorySignals),
                "product_recommendation_explanations",
                responseSchema()
        );
        return sanitize(response, products, settings.filters(), safeInventorySignals);
    }

    private String userPrompt(
            String query,
            UserSettingsResult settings,
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        return """
                Search query:
                %s

                User profile:
                Budget: %s
                Delivery locations: %s
                Clothing fit: %s
                Active filters:
                %s

                Products:
                %s
                """.formatted(
                query,
                settings.budget() == null ? "not set" : settings.budget(),
                locations(settings.locations()),
                clothingFit(settings.clothingFit()),
                filterCatalog(settings.filters()),
                productCatalog(products, inventorySignals)
        );
    }

    private String filterCatalog(List<ShoppingFilterResult> filters) {
        if (filters.isEmpty()) {
            return "- none";
        }
        return filters.stream()
                .map(filter -> "- %s (%s, %s, %s): %s".formatted(
                        filter.id(),
                        filter.label(),
                        filter.category(),
                        filter.polarity(),
                        filter.description()))
                .collect(Collectors.joining("\n"));
    }

    private String productCatalog(
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        return products.stream()
                .map(product -> productPrompt(product, inventorySignals.get(product.productKey())))
                .collect(Collectors.joining("\n\n"));
    }

    private String productPrompt(
            UserProductSearchProductSnapshot snapshot,
            UserInventoryRecommendationSignal inventorySignal
    ) {
        MerchantSemanticProductResult product = snapshot.product();
        return """
                Product key: %s
                Merchant: %s (%s)
                Title: %s
                Description: %s
                Categories: %s
                Materials: %s
                Certifications: %s
                Collections: %s
                Attributes: %s
                Image alt text: %s
                Variant details: %s
                Price: %s
                URL: %s
                Available: %s
                Owned inventory signal: %s
                """.formatted(
                snapshot.productKey(),
                value(product.merchantName()),
                value(product.merchantDomain()),
                value(product.title()),
                plainText(product.detailDescription(), product.descriptionHtml()),
                categories(product),
                listValue(product.materials()),
                listValue(product.certifications()),
                listValue(product.collections()),
                attributes(product),
                imageAltText(product),
                variantDetails(product),
                price(product),
                value(product.url()),
                value(product.selectedVariantAvailable() == null ? product.available() : product.selectedVariantAvailable()),
                inventorySignal(inventorySignal)
        );
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        OpenRouterJsonSchemaDefinition productSchema = OpenRouterJsonSchemaDefinition.object(
                List.of("productKey", "whyMeantForYou", "matchedFilterIds", "missedFilterIds"),
                Map.of(
                        "productKey", OpenRouterJsonSchemaDefinition.string(),
                        "whyMeantForYou", OpenRouterJsonSchemaDefinition.string(),
                        "matchedFilterIds", OpenRouterJsonSchemaDefinition.array(OpenRouterJsonSchemaDefinition.string()),
                        "missedFilterIds", OpenRouterJsonSchemaDefinition.array(OpenRouterJsonSchemaDefinition.string())
                )
        );
        return OpenRouterJsonSchemaDefinition.object(
                List.of("products"),
                Map.of("products", OpenRouterJsonSchemaDefinition.array(productSchema))
        );
    }

    private List<UserProductRecommendationExplanationResult> sanitize(
            String response,
            List<UserProductSearchProductSnapshot> products,
            List<ShoppingFilterResult> activeFilters,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        ExplanationBatchResponse parsed = parseResponse(response);
        Map<String, UserProductSearchProductSnapshot> requestedProducts = products.stream()
                .collect(Collectors.toMap(
                        UserProductSearchProductSnapshot::productKey,
                        product -> product,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Set<String> validFilterIds = activeFilters.stream()
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> interestFilterIds = activeFilters.stream()
                .filter(filter -> INTERESTS_CATEGORY.equals(filter.category()))
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UserProductRecommendationExplanationResult> explanations = safeList(parsed.products()).stream()
                .filter(product -> product.productKey() != null && requestedProducts.containsKey(product.productKey()))
                .map(product -> sanitizeProductExplanation(
                        product,
                        requestedProducts.get(product.productKey()),
                        inventorySignals.get(product.productKey()),
                        validFilterIds,
                        interestFilterIds))
                .filter(explanation -> !explanation.whyMeantForYou().isBlank())
                .collect(Collectors.toMap(
                        UserProductRecommendationExplanationResult::productKey,
                        explanation -> explanation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
        return explanations;
    }

    private UserProductRecommendationExplanationResult sanitizeProductExplanation(
            ProductExplanationResponse product,
            UserProductSearchProductSnapshot snapshot,
            UserInventoryRecommendationSignal inventorySignal,
            Set<String> validFilterIds,
            Set<String> interestFilterIds
    ) {
        List<String> matchedFilterIds = sanitizeFilterIds(product.matchedFilterIds(), validFilterIds);
        List<String> missedFilterIds = sanitizeFilterIds(product.missedFilterIds(), validFilterIds).stream()
                .filter(filterId -> !matchedFilterIds.contains(filterId))
                .filter(filterId -> !interestFilterIds.contains(filterId))
                .toList();
        return new UserProductRecommendationExplanationResult(
                product.productKey(),
                snapshot.productHash(),
                sanitizeWhy(product.whyMeantForYou()),
                matchedFilterIds,
                missedFilterIds,
                relationship(inventorySignal),
                inventorySignal == null ? null : inventorySignal.inventoryItemId(),
                inventorySignal == null ? null : inventorySignal.inventoryItemName()
        );
    }

    private String inventorySignal(UserInventoryRecommendationSignal signal) {
        if (signal == null || signal.relationship() == UserInventoryRecommendationRelationship.NONE) {
            return "none";
        }
        return "%s: %s".formatted(signal.relationship(), signal.reason());
    }

    private UserInventoryRecommendationRelationship relationship(UserInventoryRecommendationSignal signal) {
        return signal == null ? UserInventoryRecommendationRelationship.NONE : signal.relationship();
    }

    private ExplanationBatchResponse parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return new ExplanationBatchResponse(List.of());
        }
        try {
            ExplanationBatchResponse parsed = objectMapper.readValue(
                    OpenRouterJsonExtractor.objectCandidate(response),
                    ExplanationBatchResponse.class);
            return parsed == null ? new ExplanationBatchResponse(List.of()) : parsed;
        } catch (JacksonException exception) {
            throw new OpenRouterException("OpenRouter returned invalid product explanation JSON", exception);
        }
    }

    private List<String> sanitizeFilterIds(List<String> filterIds, Set<String> validFilterIds) {
        if (filterIds == null || validFilterIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        filterIds.stream()
                .filter(filterId -> filterId != null && validFilterIds.contains(filterId))
                .forEach(sanitized::add);
        return List.copyOf(sanitized);
    }

    private String sanitizeWhy(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = SPACE_PATTERN.matcher(value.trim()).replaceAll(" ");
        if (INTERNAL_EXPLANATION_PATTERN.matcher(trimmed).find()) {
            return "";
        }
        return trimmed.length() <= 260 ? trimmed : trimmed.substring(0, 260).trim();
    }

    private String location(UserLocationResult location) {
        if (location == null) {
            return "not set";
        }
        return "%s, %s (%s)".formatted(location.city(), location.country(), location.code());
    }

    private String locations(List<UserLocationResult> locations) {
        if (locations.isEmpty()) {
            return "not set";
        }
        return locations.stream()
                .map(this::location)
                .collect(Collectors.joining("; "));
    }

    private String clothingFit(String clothingFit) {
        String label = UserClothingFit.labelFor(clothingFit);
        return label == null ? "not set" : label;
    }

    private String categories(MerchantSemanticProductResult product) {
        String categories = safeList(product.categories()).stream()
                .filter(category -> category != null)
                .map(category -> compactJoin(category.value(), category.taxonomy()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("; "));
        return categories.isBlank() ? "not available" : categories;
    }

    private String attributes(MerchantSemanticProductResult product) {
        String attributes = safeList(product.attributes()).stream()
                .filter(attribute -> attribute != null)
                .map(attribute -> compactJoin(attribute.name(), attribute.value()))
                .filter(value -> !value.isBlank())
                .limit(24)
                .collect(Collectors.joining("; "));
        return attributes.isBlank() ? "not available" : attributes;
    }

    private String imageAltText(MerchantSemanticProductResult product) {
        String altText = Stream.concat(
                        Stream.of(product.selectedVariantImageAltText()),
                        Stream.concat(
                                safeList(product.media()).stream()
                                        .filter(media -> media != null)
                                        .map(media -> media.altText()),
                                safeList(product.detailImages()).stream()
                                        .filter(image -> image != null)
                                        .map(image -> image.altText())
                        )
                )
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
        return altText.isBlank() ? "not available" : altText;
    }

    private String variantDetails(MerchantSemanticProductResult product) {
        String variantDetails = Stream.of(
                        product.selectedVariantTitle(),
                        safeList(product.selectedOptions()).stream()
                                .filter(option -> option != null)
                                .map(option -> compactJoin(option.name(), option.value()))
                                .filter(value -> !value.isBlank())
                                .collect(Collectors.joining("; ")),
                        safeList(product.detailOptions()).stream()
                                .filter(option -> option != null)
                                .map(option -> optionDetails(option.name(), option.values()))
                                .filter(value -> !value.isBlank())
                                .collect(Collectors.joining("; "))
                )
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("; "));
        return variantDetails.isBlank() ? "not available" : variantDetails;
    }

    private String optionDetails(String name, List<String> values) {
        String optionValues = safeList(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));
        if (optionValues.isBlank()) {
            return "";
        }
        String optionName = value(name);
        return optionName.isBlank() ? optionValues : "%s: %s".formatted(optionName, optionValues);
    }

    private String compactJoin(String first, String second) {
        return Stream.of(first, second)
                .map(this::value)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(": "));
    }

    private String listValue(List<String> values) {
        String value = safeList(values).stream()
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.joining(", "));
        return value.isBlank() ? "not available" : value;
    }

    private String price(MerchantSemanticProductResult product) {
        String detailPrice = firstPresent(product.selectedVariantPriceAmount(), product.detailPriceMin());
        if (detailPrice != null) {
            return detailPrice + " " + value(firstPresent(product.selectedVariantPriceCurrency(), product.detailPriceCurrency()));
        }
        if (product.priceMinAmount() != null) {
            return product.priceMinAmount() + " " + value(product.priceCurrency());
        }
        return "not available";
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

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ExplanationBatchResponse(
            List<ProductExplanationResponse> products
    ) {
    }

    private record ProductExplanationResponse(
            String productKey,
            String whyMeantForYou,
            List<String> matchedFilterIds,
            List<String> missedFilterIds
    ) {
    }
}
